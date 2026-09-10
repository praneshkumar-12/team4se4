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
    private final TradeMapper tradeMapper;
    private final AccountAccessGuard guard;

    public OrderService(AccountMapper accountMapper, InstrumentMapper instrumentMapper,
                        PositionMapper positionMapper, OrderMapper orderMapper,
                        TradeMapper tradeMapper, AccountAccessGuard guard) {
        this.accountMapper = accountMapper;
        this.instrumentMapper = instrumentMapper;
        this.positionMapper = positionMapper;
        this.orderMapper = orderMapper;
        this.tradeMapper = tradeMapper;
        this.guard = guard;
    }

    /**
     * Validates against rules 1 to 8, fills synchronously, and writes the order,
     * the trade, the cash movement and the position in one transaction: they
     * move together or none of them moves.
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
        boolean positionExisted = false;

        if (instrument != null) {
            instruments.put(request.symbol(), instrument);
            Position existing = positionMapper.findDomain(accountId, instrument.getInstrumentId());
            if (existing != null) {
                positions.put(positionKey(accountId, request.symbol()), existing);
                positionExisted = true;
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

        Order filled = domain.placeOrder(domainRequest);

        // Persist the resulting state.
        String publicId = UUID.randomUUID().toString();
        NewOrder newOrder = new NewOrder(
                publicId,
                request.idempotencyKey(),
                accountId,
                instrument.getInstrumentId(),
                filled.getSide().name(),
                filled.getOrderType().name(),
                filled.getQuantity(),
                filled.getLimitPrice(),
                filled.getStatus().name());

        try {
            orderMapper.insert(newOrder);
        } catch (DuplicateKeyException e) {
            throw new DuplicateOrderException(request.idempotencyKey());
        }

        int updated = accountMapper.updateBalanceWithVersion(
                accountId, account.getCashBalance(), account.getVersion());
        if (updated == 0) {
            // A concurrent writer moved the version between our read and our write.
            throw new ConcurrentUpdateException();
        }

        Position finalPosition = positions.get(positionKey(accountId, request.symbol()));
        if (positionExisted) {
            positionMapper.updatePosition(accountId, instrument.getInstrumentId(),
                    finalPosition.getQuantity(), finalPosition.getAverageCost());
        } else {
            positionMapper.insertPosition(accountId, instrument.getInstrumentId(),
                    finalPosition.getQuantity(), finalPosition.getAverageCost());
        }

        tradeMapper.insert(newOrder.getOrderId(), filled.getLimitPrice(),
                filled.getQuantity(), Instant.now());

        log.info("Order {} filled: account={} {} {} qty={} price={}",
                publicId, accountId, filled.getSide(), request.symbol(),
                filled.getQuantity(), filled.getLimitPrice());

        return new OrderResponse("ORD-" + publicId, filled.getStatus(), "Order executed",
                request.symbol(), filled.getSide(), request.quantity(), filled.getLimitPrice());
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
