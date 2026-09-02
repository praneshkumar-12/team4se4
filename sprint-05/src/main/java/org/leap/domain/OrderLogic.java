package org.leap.domain;

import org.leap.domain.enums.OrderSide;
import org.leap.domain.enums.OrderStatus;
import org.leap.domain.enums.OrderType;
import org.leap.exceptions.AccountNotActiveException;
import org.leap.exceptions.AccountNotFoundException;
import org.leap.exceptions.DomainValidationException;
import org.leap.exceptions.DuplicateOrderException;
import org.leap.exceptions.InstrumentNotFoundException;
import org.leap.exceptions.InsufficientFundsException;
import org.leap.exceptions.InsufficientHoldingsException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public class OrderLogic {

    private final Map<Long, Account> accounts;
    private final Map<String, Instrument> instruments;
    private final Map<String, Order> orders;
    private final Map<String, Position> positions;

    private long nextOrderId = 1L;

    /*
     * The synchronized method gives us an atomic in-memory
     * idempotency check for Sprint 5.
     *
     * Sprint 6 can replace this with the database unique
     * constraint on orders.idempotency_key.
     */
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

    public synchronized Order placeOrder(
            PlaceOrderRequest request) {

        Objects.requireNonNull(request);

        /*
         * =====================================================
         * RULE 1
         * Account must exist
         * =====================================================
         */

        Account account =
                accounts.get(request.getAccountId());

        if (account == null) {
            throw new AccountNotFoundException(
                    request.getAccountId()
            );
        }

        /*
         * =====================================================
         * RULE 2
         * Account must be ACTIVE
         * =====================================================
         */

        if (!account.isActive()) {
            throw new AccountNotActiveException(
                    account.getAccountId()
            );
        }

        /*
         * =====================================================
         * RULE 3
         * Instrument must exist and be tradable
         * =====================================================
         */

        Instrument instrument =
                instruments.get(request.getSymbol());

        if (instrument == null ||
                !instrument.isTradable()) {

            throw new InstrumentNotFoundException(
                    request.getSymbol()
            );
        }

        /*
         * =====================================================
         * RULE 4
         * Quantity must be greater than zero
         *
         * DTO validates this too, but the domain validates
         * again because callers can bypass Bean Validation.
         * =====================================================
         */

        BigDecimal quantity =
                request.getQuantity();

        if (quantity == null ||
                quantity.compareTo(BigDecimal.ZERO) <= 0) {

            throw new DomainValidationException(
                    "Quantity must be greater than zero"
            );
        }

        /*
         * =====================================================
         * RULE 5
         * Price must be greater than zero
         * =====================================================
         */

        BigDecimal price =
                request.getPrice();

        if (price == null ||
                price.compareTo(BigDecimal.ZERO) <= 0) {

            throw new DomainValidationException(
                    "Price must be greater than zero"
            );
        }

        /*
         * =====================================================
         * RULE 6
         * BUY must have enough cash
         *
         * required cash =
         *
         * quantity * price
         * =====================================================
         */

        BigDecimal orderValue =
                quantity.multiply(price);

        if (request.getSide() == OrderSide.BUY) {

            if (!account.canAfford(orderValue)) {

                throw new InsufficientFundsException(
                        account.getAccountId(),
                        orderValue,
                        account.getCashBalance()
                );
            }
        }

        /*
         * =====================================================
         * RULE 7
         * SELL must have sufficient holdings
         * =====================================================
         */

        Position position =
                positions.get(
                        positionKey(
                                request.getAccountId(),
                                request.getSymbol()
                        )
                );

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

        /*
         * =====================================================
         * RULE 8
         * Idempotency key must not have been used
         *
         * This is intentionally LAST.
         *
         * synchronized + containsKey/put gives us one atomic
         * critical section for the Sprint 5 in-memory version.
         *
         * Sprint 6 should use:
         *
         * UNIQUE(idempotency_key)
         *
         * in the database.
         * =====================================================
         */

        String idempotencyKey =
                request.getIdempotencyKey();

        if (orders.containsKey(idempotencyKey)) {

            throw new DuplicateOrderException(
                    idempotencyKey
            );
        }

        /*
         * =====================================================
         * ALL RULES PASSED
         *
         * Now mutation is allowed.
         * =====================================================
         */

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

        /*
         * -----------------------------------------------------
         * Execute BUY
         * -----------------------------------------------------
         */

        if (request.getSide() == OrderSide.BUY) {

            /*
             * Deduct cash.
             */
            account.debit(orderValue);

            /*
             * Create or update position.
             */
            if (position == null) {

                position = new Position(
                        nextHoldingId(),
                        account.getAccountId(),
                        instrument.getInstrumentId(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                );

                positions.put(
                        positionKey(
                                account.getAccountId(),
                                request.getSymbol()
                        ),
                        position
                );
            }

            position.buy(quantity, price);
        }

        /*
         * -----------------------------------------------------
         * Execute SELL
         * -----------------------------------------------------
         */

        else if (request.getSide() == OrderSide.SELL) {

            /*
             * Reduce holding.
             */
            position.sell(quantity);

            /*
             * Add cash.
             */
            account.credit(orderValue);
        }

        /*
         * Mark order as filled.
         */
        order.fill();

        /*
         * Store order using the idempotency key.
         */
        orders.put(
                idempotencyKey,
                order
        );

        return order;
    }

    private String positionKey(
            Long accountId,
            String symbol) {

        return accountId + ":" + symbol;
    }

    private long nextHoldingId() {

        /*
         * Sprint 5 only needs a deterministic ID.
         * Sprint 6 database persistence should generate IDs.
         */
        return positions.size() + 1L;
    }
}