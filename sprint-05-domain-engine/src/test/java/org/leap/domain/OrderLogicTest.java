package org.leap.domain;

import org.leap.domain.enums.*;
import org.leap.exceptions.*;
import org.leap.dto.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderLogicTest {

    private OrderLogic service;

    private Map<Long, Account> accounts;
    private Map<String, Instrument> instruments;
    private Map<String, Order> orders;
    private Map<String, Position> positions;


    @BeforeEach
    void setUp() {
        accounts = new HashMap<>();
        instruments = new HashMap<>();
        orders = new HashMap<>();
        positions = new HashMap<>();

        service = new OrderLogic(
                accounts,
                instruments,
                orders,
                positions
        );

        accounts.put(
                1L,
                activeAccount("1000.00")
        );

        instruments.put(
                "AAPL",
                activeInstrument()
        );
    }

    // =========================================================
    // RULE 1
    // Account must exist
    // =========================================================

    @Test
    void rule1_shouldRejectWhenAccountDoesNotExist() {

        OrderRequest request = request(
                999L,
                "AAPL",
                OrderSide.BUY,
                "10",
                "100.00",
                "IDEMP001"
        );

        AccountNotFoundException exception =
                assertThrows(
                        AccountNotFoundException.class,
                        () -> service.acceptOrder(request)
                );

        assertEquals(
                "ACC-404",
                exception.getCode()
        );
    }

    @Test
    void rule1_shouldNotFireWhenAccountExists() {

        OrderRequest request = validBuy();

        assertDoesNotThrow(
                () -> service.acceptOrder(request)
        );
    }

    // =========================================================
    // RULE 2
    // Account must be ACTIVE
    // =========================================================

    @Test
    void rule2_shouldRejectSuspendedAccount() {

        accounts.put(
                1L,
                suspendedAccount()
        );

        AccountNotActiveException exception =
                assertThrows(
                        AccountNotActiveException.class,
                        () -> service.acceptOrder(validBuy())
                );

        assertEquals(
                "ACC-403",
                exception.getCode()
        );
    }

    @Test
    void rule2_shouldRejectClosedAccount() {

        accounts.put(
                1L,
                closedAccount()
        );

        AccountNotActiveException exception =
                assertThrows(
                        AccountNotActiveException.class,
                        () -> service.acceptOrder(validBuy())
                );

        assertEquals(
                "ACC-403",
                exception.getCode()
        );
    }

    @Test
    void rule2_shouldNotFireForActiveAccount() {

        assertDoesNotThrow(
                () -> service.acceptOrder(validBuy())
        );
    }

    // =========================================================
    // RULE 3
    // Instrument must exist and be tradable
    // =========================================================

    @Test
    void rule3_shouldRejectUnknownInstrument() {

        OrderRequest request = request(
                1L,
                "UNKNOWN",
                OrderSide.BUY,
                "10",
                "100.00",
                "IDEMP002"
        );

        InstrumentNotFoundException exception =
                assertThrows(
                        InstrumentNotFoundException.class,
                        () -> service.acceptOrder(request)
                );

        assertEquals(
                "INS-404",
                exception.getCode()
        );
    }

    @Test
    void rule3_shouldRejectDelistedInstrument() {

        Instrument instrument = activeInstrument();
        instrument.delist();

        instruments.put("AAPL", instrument);

        InstrumentNotFoundException exception =
                assertThrows(
                        InstrumentNotFoundException.class,
                        () -> service.acceptOrder(validBuy())
                );

        assertEquals(
                "INS-404",
                exception.getCode()
        );
    }

    @Test
    void rule3_shouldNotFireForTradableInstrument() {

        assertDoesNotThrow(
                () -> service.acceptOrder(validBuy())
        );
    }

    // =========================================================
    // RULE 4
    // Quantity must be > 0
    // =========================================================

    @Test
    void rule4_shouldRejectZeroQuantity() {

        OrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "0",
                "100.00",
                "IDEMP003"
        );

        DomainException exception =
                assertThrows(
                        DomainException.class,
                        () -> service.acceptOrder(request)
                );

        assertEquals(
                "VAL-422",
                exception.getCode()
        );
    }

    @Test
    void rule4_shouldRejectNegativeQuantity() {

        OrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "-1",
                "100.00",
                "IDEMP004"
        );

        DomainException exception =
                assertThrows(
                        DomainException.class,
                        () -> service.acceptOrder(request)
                );

        assertEquals(
                "VAL-422",
                exception.getCode()
        );
    }

    @Test
    void rule4_shouldNotFireForPositiveWholeQuantity() {

        assertDoesNotThrow(
                () -> service.acceptOrder(validBuy())
        );
    }

    // =========================================================
    // RULE 5
    // Price must be > 0
    // =========================================================

    @Test
    void rule5_shouldRejectZeroPrice() {

        OrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "10",
                "0",
                "IDEMP005"
        );

        DomainException exception =
                assertThrows(
                        DomainException.class,
                        () -> service.acceptOrder(request)
                );

        assertEquals(
                "VAL-422",
                exception.getCode()
        );
    }

    @Test
    void rule5_shouldRejectNegativePrice() {

        OrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "10",
                "-1",
                "IDEMP006"
        );

        DomainException exception =
                assertThrows(
                        DomainException.class,
                        () -> service.acceptOrder(request)
                );

        assertEquals(
                "VAL-422",
                exception.getCode()
        );
    }

    @Test
    void rule5_shouldNotFireForPositivePrice() {

        assertDoesNotThrow(
                () -> service.acceptOrder(validBuy())
        );
    }

    // =========================================================
    // RULE 6
    // BUY must have enough cash
    // =========================================================

    @Test
    void rule6_shouldRejectBuyWhenInsufficientFunds() {

        OrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "11",
                "100.00",
                "IDEMP007"
        );

        InsufficientFundsException exception =
                assertThrows(
                        InsufficientFundsException.class,
                        () -> service.acceptOrder(request)
                );

        assertEquals(
                "ORD-400",
                exception.getCode()
        );
    }

    @Test
    void rule6_shouldAllowBuyWhenCashExactlyEqualsCost() {

        OrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "10",
                "100.00",
                "IDEMP008"
        );

        assertDoesNotThrow(
                () -> service.acceptOrder(request)
        );
    }

    // =========================================================
    // RULE 7
    // SELL must have sufficient holdings
    // =========================================================

    @Test
    void rule7_shouldRejectSellWhenInsufficientHoldings() {

        positions.put(
                positionKey(1L, "AAPL"),
                new Position(
                        1L,
                        1L,
                        100L,
                        new BigDecimal("5"),
                        new BigDecimal("100")
                )
        );

        OrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.SELL,
                "6",
                "100.00",
                "IDEMP009"
        );

        InsufficientHoldingsException exception =
                assertThrows(
                        InsufficientHoldingsException.class,
                        () -> service.acceptOrder(request)
                );

        assertEquals(
                "ORD-409",
                exception.getCode()
        );
    }

    @Test
    void rule7_shouldAllowSellWhenHoldingExactlyEqualsQuantity() {

        positions.put(
                positionKey(1L, "AAPL"),
                new Position(
                        1L,
                        1L,
                        100L,
                        new BigDecimal("5"),
                        new BigDecimal("100")
                )
        );

        OrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.SELL,
                "5",
                "100.00",
                "IDEMP010"
        );

        assertDoesNotThrow(
                () -> service.acceptOrder(request)
        );
    }

    // =========================================================
    // RULE 8
    // Idempotency key must be unique
    // =========================================================

    @Test
    void rule8_shouldRejectDuplicateIdempotencyKey() {

        OrderRequest first = validBuy();

        service.acceptOrder(first);

        // Placing the first order spends the account's cash; top it up so the
        // duplicate is rejected for its idempotency key, not insufficient funds.
        accounts.get(1L).credit(new BigDecimal("1000.00"));

        OrderRequest duplicate = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "10",
                "100.00",
                "IDEMP001"
        );

        DuplicateOrderException exception =
                assertThrows(
                        DuplicateOrderException.class,
                        () -> service.acceptOrder(duplicate)
                );

        assertEquals(
                "ORD-409",
                exception.getCode()
        );
    }

    @Test
    void rule8_shouldAllowDifferentIdempotencyKey() {

        service.acceptOrder(validBuy());

        // Placing the first order spends the account's cash; top it up so the
        // second order is evaluated on its idempotency key, not insufficient funds.
        accounts.get(1L).credit(new BigDecimal("1000.00"));

        OrderRequest second = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "5",
                "100.00",
                "IDEMP011"
        );

        assertDoesNotThrow(
                () -> service.acceptOrder(second)
        );
    }

    // =========================================================
    // EVALUATION ORDER
    // =========================================================

    @Test
    void ruleEvaluationShouldFollowRequiredOrder() {

        // Account does not exist AND instrument does not exist.
        OrderRequest request = request(
                999L,
                "UNKNOWN",
                OrderSide.BUY,
                "10",
                "100.00",
                "IDEMP012"
        );

        DomainException exception =
                assertThrows(
                        DomainException.class,
                        () -> service.acceptOrder(request)
                );

        // Rule 1 must win over rule 3.
        assertEquals(
                "ACC-404",
                exception.getCode()
        );
    }

    @Test
    void accountStatusFailureShouldWinBeforeInsufficientFunds() {

        accounts.put(
                1L,
                suspendedAccount()
        );

        OrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "100000",
                "100000",
                "IDEMP013"
        );

        DomainException exception =
                assertThrows(
                        DomainException.class,
                        () -> service.acceptOrder(request)
                );

        // Rule 2 must win before rule 6.
        assertEquals(
                "ACC-403",
                exception.getCode()
        );
    }

    @Test
    void instrumentFailureShouldWinBeforeInsufficientFunds() {

        Instrument instrument = activeInstrument();
        instrument.delist();

        instruments.put("AAPL", instrument);

        OrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "100000",
                "100000",
                "IDEMP014"
        );

        DomainException exception =
                assertThrows(
                        DomainException.class,
                        () -> service.acceptOrder(request)
                );

        assertEquals(
                "INS-404",
                exception.getCode()
        );
    }

    @Test
    void quantityFailureShouldWinBeforePriceFailure() {

        OrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "0",
                "0",
                "IDEMP015"
        );

        DomainException exception =
                assertThrows(
                        DomainException.class,
                        () -> service.acceptOrder(request)
                );

        // Rule 4 must win over rule 5.
        assertEquals(
                "VAL-422",
                exception.getCode()
        );
    }

    @Test
    void priceFailureShouldWinBeforeInsufficientFunds() {

        OrderRequest request = request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "10",
                "0",
                "IDEMP016"
        );

        DomainException exception =
                assertThrows(
                        DomainException.class,
                        () -> service.acceptOrder(request)
                );

        assertEquals(
                "VAL-422",
                exception.getCode()
        );
    }

    // =========================================================
    // Helpers
    // =========================================================

    private OrderRequest validBuy() {
        return request(
                1L,
                "AAPL",
                OrderSide.BUY,
                "10",
                "100.00",
                "IDEMP001"
        );
    }

    private OrderRequest request(
            Long accountId,
            String symbol,
            OrderSide side,
            String quantity,
            String price,
            String idempotencyKey
    ) {
        return new OrderRequest(
                accountId,
                symbol,
                side,
                new BigDecimal(quantity),      
                new BigDecimal(price),
                idempotencyKey
        );
    }

    private Account activeAccount(String balance) {
        return new Account(
                1L,
                "ACC-001",
                "Abinaya",
                "K",
                "EUR",
                new BigDecimal(balance),
                AccountStatus.ACTIVE,
                0L
        );
    }

    private Account suspendedAccount() {
        return new Account(
                1L,
                "ACC-001",
                "Abinaya",
                "K",
                "EUR",
                new BigDecimal("0.00"),
                AccountStatus.SUSPENDED,
                0L
        );
    }

    private Account closedAccount() {
        return new Account(
                1L,
                "ACC-001",
                "Abinaya",
                "K",
                "EUR",
                new BigDecimal("0.00"),
                AccountStatus.CLOSED,
                0L
        );
    }

    private Instrument activeInstrument() {
        return new Instrument(
                100L,
                "US0378331005",
                "AAPL",
                "Apple Inc.",
                InstrumentType.EQUITY,
                "NASDAQ",
                "USD",
                true
        );
    }

    private String positionKey(
            Long accountId,
            String symbol
    ) {
        return accountId + ":" + symbol;
    }
}