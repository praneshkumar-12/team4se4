package org.leap.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import org.leap.domain.enums.OrderSide;
import org.leap.domain.enums.OrderType;
import org.leap.dto.OrderRequest;
import org.leap.exceptions.AccountNotActiveException;
import org.leap.exceptions.AccountNotFoundException;
import org.leap.exceptions.DomainValidationException;
import org.leap.exceptions.DuplicateOrderException;
import org.leap.exceptions.InstrumentNotFoundException;
import org.leap.exceptions.InsufficientFundsException;
import org.leap.exceptions.InsufficientHoldingsException;

public class OrderLogic {

    private final Map<Long, Account> accounts;
    private final Map<String, Instrument> instruments;
    private final Map<String, Order> orders;
    private final Map<String, Position> positions;

    private long nextOrderId = 1L;

    public OrderLogic(
            Map<Long, Account> accounts,
            Map<String, Instrument> instruments,
            Map<String, Order> orders,
            Map<String, Position> positions) {

        this.accounts = Objects.requireNonNull(accounts);
        this.instruments = Objects.requireNonNull(instruments);
        this.orders = Objects.requireNonNull(orders);
        this.positions = Objects.requireNonNull(positions);
    }

    /*
     * synchronized keeps the duplicate check and order creation
     * atomic for the Sprint 5 in-memory implementation.
     * 
     * Runs rules 1-8 and, if every rule passes, returns a new Order at status
     * NEW. It does not fill: no account debit/credit, no position mutation,
     * no order.fill(). Pricing and settlement happen later, once the Trade
     * Executor has read a real market quote (Sprint 7).
     */
    public synchronized Order acceptOrder(OrderRequest request) {

        Objects.requireNonNull(request);

        // Rule 1: Account must exist.
        Account account = accounts.get(request.getAccountId());

        if (account == null) {
            throw new AccountNotFoundException(
                    request.getAccountId()
            );
        }

        // Rule 2: Account must be active.
        if (!account.isActive()) {
            throw new AccountNotActiveException(
                    account.getAccountId()
            );
        }

        // Rule 3: Instrument must exist and be tradable.
        Instrument instrument = instruments.get(request.getSymbol());

        if (instrument == null || !instrument.isTradable()) {
            throw new InstrumentNotFoundException(
                    request.getSymbol()
            );
        }

        // Validate order side at the domain level as well.
        if (request.getSide() == null) {
            throw new DomainValidationException(
                    "side",
                    null,
                    "Order side is required"
            );
        }

        // Rule 4: Quantity must be greater than zero.
        BigDecimal quantity = request.getQuantity();

        if (quantity == null ||
                quantity.compareTo(BigDecimal.ZERO) <= 0) {

            throw new DomainValidationException(
                    "quantity",
                    quantity,
                    "Quantity must be greater than zero"
            );
        }

        // Rule 5: Price must be greater than zero.
        BigDecimal price = request.getPrice();

        if (price == null ||
                price.compareTo(BigDecimal.ZERO) <= 0) {

            throw new DomainValidationException(
                    "price",
                    price,
                    "Price must be greater than zero"
            );
        }

        BigDecimal orderValue = quantity.multiply(price);

        // Rule 6: BUY orders require sufficient cash.
        if (request.getSide() == OrderSide.BUY) {

            if (!account.canAfford(orderValue)) {
                throw new InsufficientFundsException(
                        account.getAccountId(),
                        orderValue,
                        account.getCashBalance()
                );
            }
        }

        Position position = positions.get(
                positionKey(
                        request.getAccountId(),
                        request.getSymbol()
                )
        );

        // Rule 7: SELL orders require sufficient holdings.
        if (request.getSide() == OrderSide.SELL) {

            BigDecimal availableQuantity =
                    position == null
                            ? BigDecimal.ZERO
                            : position.getQuantity();

            if (position == null ||
                    availableQuantity.compareTo(quantity) < 0) {

                throw new InsufficientHoldingsException(
                        account.getAccountId(),
                        instrument.getInstrumentId(),
                        quantity,
                        availableQuantity
                );
            }
        }

        // Rule 8: Idempotency key must be unique.
        String idempotencyKey = request.getIdempotencyKey();

        if (orders.containsKey(idempotencyKey)) {
            throw new DuplicateOrderException(idempotencyKey);
        }

        // All business rules passed: build the order at NEW and record it for
        // the idempotency check (rule 8). No cash, position or fill movement
        // happens here anymore - that is the executor's job once it has priced
        // the order against a market quote.
        Order order = new Order(
                nextOrderId++,
                idempotencyKey,
                account.getAccountId(),
                instrument.getInstrumentId(),
                request.getSide(),
                OrderType.LIMIT,
                quantity,
                price
        );

        
        orders.put(idempotencyKey, order);

        return order;
    }

    private String positionKey(Long accountId, String symbol) {
        return accountId + ":" + symbol;
    }

}