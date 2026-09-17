package com.leap.tradeapi.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.leap.domain.Account;
import org.leap.domain.Instrument;
import org.leap.domain.Order;
import org.leap.domain.OrderLogic;
import org.leap.domain.Position;
import org.leap.domain.enums.OrderStatus;
import org.leap.dto.OrderRequest;
import org.leap.exceptions.AccountNotFoundException;
import org.leap.exceptions.DuplicateOrderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leap.tradeapi.controller.dto.OrderResponse;
import com.leap.tradeapi.controller.dto.PlaceOrderRequest;
import com.leap.tradeapi.error.ConcurrentUpdateException;
import com.leap.tradeapi.error.OrderNotCancellableException;
import com.leap.tradeapi.error.OrderNotFoundException;
import com.leap.tradeapi.mapper.AccountMapper;
import com.leap.tradeapi.mapper.InstrumentMapper;
import com.leap.tradeapi.mapper.OrderMapper;
import com.leap.tradeapi.mapper.PositionMapper;
import com.leap.tradeapi.mapper.TradeMapper;
import com.leap.tradeapi.mapper.row.NewOrder;
import com.leap.tradeapi.mapper.row.OrderRow;
import com.leap.tradeapi.messaging.OrderEventPublisher;
import com.leap.tradeapi.messaging.OrderPlacedPayload;

/**
 * Order placement and cancellation. This class decides nothing about whether a
 * trade is allowed: it loads the state, hands it to the Sprint 5
 * {@link OrderLogic}, and persists what comes back. Every business rule lives in
 * the domain so the Sprint 7 Trade Executor reads the same rules unchanged.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final PositionMapper positionMapper;
    private final OrderMapper orderMapper;
    private final AccountAccessGuard guard;
    private final OrderEventPublisher orderEventPublisher;

    public OrderService(AccountMapper accountMapper, InstrumentMapper instrumentMapper,
                        PositionMapper positionMapper, OrderMapper orderMapper,
                        AccountAccessGuard guard, OrderEventPublisher orderEventPublisher) {
        this.accountMapper = accountMapper;
        this.instrumentMapper = instrumentMapper;
        this.positionMapper = positionMapper;
        this.orderMapper = orderMapper;
        this.guard = guard;
        this.orderEventPublisher = orderEventPublisher;
    }

    /**
     * Validates against rules 1 to 8 and writes the order at {@code NEW}. No
     * cash movement, no position movement, no fill: pricing and settlement now
     * happen later, in the Trade Executor, once it has read a market quote for
     * this order off the {@code orders} topic. On success, publishes
     * {@code ORDER_PLACED} once this method's transaction has committed - see
     * {@link OrderEventPublisher}.
     */
    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        long accountId = request.accountId();
        guard.requireReaches(accountId);

        Account account = accountMapper.findDomainById(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }

        Instrument instrument = instrumentMapper.findTradableByTicker(request.symbol());

        // Assemble the snapshot the domain needs. Rule 8 (idempotency) is left to
        // the unique constraint on orders.idempotency_key, not a read-then-write.
        Map<Long, Account> accounts = new HashMap<>();
        accounts.put(accountId, account);
        Map<String, Instrument> instruments = new HashMap<>();
        Map<String, Position> positions = new HashMap<>();

        if (instrument != null) {
            instruments.put(request.symbol(), instrument);
            Position existing = positionMapper.findDomain(accountId, instrument.getInstrumentId());
            if (existing != null) {
                positions.put(positionKey(accountId, request.symbol()), existing);
            }
        }

        OrderLogic domain = new OrderLogic(accounts, instruments, new HashMap<>(), positions);
        OrderRequest domainRequest = new OrderRequest(
                accountId,
                request.symbol(),
                request.side(),
                BigDecimal.valueOf(request.quantity()),
                request.price(),
                request.idempotencyKey());

        Order accepted = domain.acceptOrder(domainRequest);

        // Persist the order at NEW.
        String publicId = UUID.randomUUID().toString();
        NewOrder newOrder = new NewOrder(
                publicId,
                request.idempotencyKey(),
                accountId,
                instrument.getInstrumentId(),
                accepted.getSide().name(),
                accepted.getOrderType().name(),
                accepted.getQuantity(),
                accepted.getLimitPrice(),
                accepted.getStatus().name());

        try {
            orderMapper.insert(newOrder);
        } catch (DuplicateKeyException e) {
            throw new DuplicateOrderException(request.idempotencyKey());
        }

        Instant createdOn = Instant.now();
        orderEventPublisher.publishOrderPlaced(new OrderPlacedPayload(
                String.valueOf(newOrder.getOrderId()),
                accountId,
                request.symbol(),
                accepted.getSide().name(),
                request.quantity(),
                accepted.getLimitPrice(),
                request.idempotencyKey(),
                createdOn));

        log.info("Order {} accepted: account={} {} {} qty={} price={}",
                publicId, accountId, accepted.getSide(), request.symbol(),
                accepted.getQuantity(), accepted.getLimitPrice());

        return new OrderResponse("ORD-" + publicId, accepted.getStatus(), "Order accepted",
                request.symbol(), accepted.getSide(), request.quantity(), accepted.getLimitPrice());
    }

    /**
     * Cancels an order that is still {@code NEW}. The transition is a single
     * guarded statement inside the transaction, so it does not race a filler.
     */
    @Transactional
    public OrderResponse cancelOrder(UUID id) {
        OrderRow order = orderMapper.findByPublicId(id.toString());
        if (order == null) {
            throw new OrderNotFoundException();
        }

        guard.requireReaches(order.accountId());

        int cancelled = orderMapper.cancelIfNew(id.toString());
        if (cancelled == 0) {
            throw new OrderNotCancellableException();
        }

        log.info("Order {} cancelled: account={}", id, order.accountId());

        return new OrderResponse("ORD-" + id, OrderStatus.CANCELLED, "Order cancelled",
                order.symbol(), order.side(), order.quantity().intValueExact(), order.limitPrice());
    }

    private static String positionKey(long accountId, String symbol) {
        return accountId + ":" + symbol;
    }
}
