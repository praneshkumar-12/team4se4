package org.leap.service;

import org.leap.domain.Account;
import org.leap.domain.Instrument;
import org.leap.domain.Order;
import org.leap.domain.Position;
import org.leap.dto.OrderRequest;
import org.leap.exceptions.*;
import org.leap.domain.enums.*;

import java.math.BigDecimal;
import java.util.Map;

public class OrderPlacementServiceImpl implements OrderPlacementService {

    private final Map<Long, Account> accounts;
    private final Map<String, Instrument> instruments;
    private final Map<String, Order> orders;
    private final Map<String, Position> positions;

    public OrderPlacementServiceImpl(
            Map<Long, Account> accounts,
            Map<String, Instrument> instruments,
            Map<String, Order> orders,
            Map<String, Position> positions) {

        this.accounts = accounts;
        this.instruments = instruments;
        this.orders = orders;
        this.positions = positions;
    }

    @Override
    public void placeOrder(OrderRequest request) {

        // Rule 1: Account must exist
        Account account = accounts.get(request.getAccountId());

        if (account == null) {
            throw new AccountNotFoundException(request.getAccountId());
        }

        // Rule 2: Account must be ACTIVE
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getAccountId());
        }

        // Rule 3: Instrument must exist
        Instrument instrument = instruments.get(request.getSymbol());

        if (instrument == null) {
            throw new InstrumentNotFoundException(request.getSymbol());
        }

        // Instrument must be tradable

    if (instrument == null || !instrument.isTradable()) {
        throw new InstrumentNotFoundException(request.getSymbol());
    }

        // Rule 4: Quantity > 0
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new DomainValidationException(
                "quantity",
                request.getQuantity(),
                "Quantity must be greater than zero"
            );
        }

        // Rule 5: Price > 0
        if (request.getPrice() == null ||
                request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {

            throw new DomainValidationException(
                "price",
                request.getPrice(),
                "Price must be greater than zero"
            );
        }

        // Rule 8: Idempotency key must be unique
        if (orders.containsKey(request.getIdempotencyKey())) {
            throw new DuplicateOrderException(request.getIdempotencyKey());
        }

        // Rule 6: BUY -> sufficient funds
        if (request.getSide() == OrderSide.BUY) {

            BigDecimal required =
                    request.getPrice().multiply(
                            BigDecimal.valueOf(request.getQuantity())
                    );

            if (account.getCashBalance().compareTo(required) < 0) {
                throw new InsufficientFundsException(
                        account.getAccountId(),
                        account.getCashBalance(),
                        required
                );
            }
        }

        // Rule 7: SELL -> sufficient holdings
        if (request.getSide() == OrderSide.SELL) {

            String key = request.getAccountId() + ":" + request.getSymbol();

            Position position = positions.get(key);

            BigDecimal required =
                    BigDecimal.valueOf(request.getQuantity());

            if (position == null ||
                    position.getQuantity().compareTo(required) < 0) {

                BigDecimal available =
                        position == null
                                ? BigDecimal.ZERO
                                : position.getQuantity();

                throw new InsufficientHoldingsException(
                        request.getAccountId(),
                        instrument.getInstrumentId(),
                        available,
                        required
                );
            }
        }

    }
}

