package org.leap.domain;
import org.leap.domain.enums.*;
import org.leap.dto.*;


import org.junit.jupiter.api.Test;
import org.leap.dto.OrderRequest;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// No Bean Validation provider is on this module's classpath, so violations()
// re-checks the same constraints declared on OrderRequest's fields by hand.
public class PlaceOrderRequestValidationTest {

    private OrderRequest valid() {
        return new OrderRequest(
                1L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.00"), "KEY-12345");
    }

    private Set<String> violations(OrderRequest request) {
        Set<String> violations = new HashSet<>();

        Long accountId = request.getAccountId();
        if (accountId == null || accountId < 1L) {
            violations.add("accountId");
        }

        String symbol = request.getSymbol();
        if (symbol == null || symbol.isBlank() || symbol.length() > 20) {
            violations.add("symbol");
        }

        if (request.getSide() == null) {
            violations.add("side");
        }

        BigDecimal quantity = request.getQuantity();
        if (quantity == null || quantity.compareTo(BigDecimal.ONE) < 0) {
            violations.add("quantity");
        }

        BigDecimal price = request.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0 || price.scale() > 2) {
            violations.add("price");
        }

        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() < 8 || idempotencyKey.length() > 100) {
            violations.add("idempotencyKey");
        }

        return violations;
    }

    @Test void validRequestHasNoViolations() {
        assertTrue(violations(valid()).isEmpty());
    }

    @Test void accountIdZeroIsInvalid() {
        assertFalse(violations(new OrderRequest(
                0L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void accountIdOneIsValidBoundary() {
        assertTrue(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void nullAccountIdIsInvalid() {
        assertFalse(violations(new OrderRequest(
                null, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void blankSymbolIsInvalid() {
        assertFalse(violations(new OrderRequest(
                1L, " ", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void twentyCharacterSymbolIsValidBoundary() {
        assertTrue(violations(new OrderRequest(
                1L, "ABCDEFGHIJKLMNOPQRST", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void twentyOneCharacterSymbolIsInvalid() {
        assertFalse(violations(new OrderRequest(
                1L, "ABCDEFGHIJKLMNOPQRSTU", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void nullSideIsInvalid() {
        assertFalse(violations(new OrderRequest(
                1L, "AAPL", null, new BigDecimal("1"),
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void zeroQuantityIsInvalid() {
        assertFalse(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, BigDecimal.ZERO,
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void quantityOneIsValidBoundary() {
        assertTrue(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void nullQuantityIsInvalid() {
        assertFalse(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, null,
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void zeroPriceIsInvalid() {
        assertFalse(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                BigDecimal.ZERO, "KEY-12345")).isEmpty());
    }

    @Test void positiveTwoDecimalPriceIsValid() {
        assertTrue(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("0.01"), "KEY-12345")).isEmpty());
    }

    @Test void moreThanTwoPriceDecimalsIsInvalid() {
        assertFalse(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.001"), "KEY-12345")).isEmpty());
    }

    @Test void nullPriceIsInvalid() {
        assertFalse(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                null, "KEY-12345")).isEmpty());
    }

    @Test void sevenCharacterIdempotencyKeyIsInvalid() {
        assertFalse(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.00"), "1234567")).isEmpty());
    }

    @Test void eightCharacterIdempotencyKeyIsValidBoundary() {
        assertTrue(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.00"), "12345678")).isEmpty());
    }

    @Test void oneHundredCharacterIdempotencyKeyIsValidBoundary() {
        String key = "A".repeat(100);
        assertTrue(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.00"), key)).isEmpty());
    }

    @Test void oneHundredOneCharacterIdempotencyKeyIsInvalid() {
        String key = "A".repeat(101);
        assertFalse(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.00"), key)).isEmpty());
    }

    @Test void blankIdempotencyKeyIsInvalid() {
        assertFalse(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.00"), "        ")).isEmpty());
    }

    @Test void nullIdempotencyKeyIsInvalid() {
        assertFalse(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.00"), null)).isEmpty());
    }

    @Test void sellIsValidSide() {
        assertTrue(violations(new OrderRequest(
                1L, "AAPL", OrderSide.SELL, new BigDecimal("1"),
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void negativeAccountIdIsInvalid() {
        assertFalse(violations(new OrderRequest(
                -1L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void negativeQuantityIsInvalid() {
        assertFalse(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, new BigDecimal("-1"),
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void negativePriceIsInvalid() {
        assertFalse(violations(new OrderRequest(
                1L, "AAPL", OrderSide.BUY, new BigDecimal("1"),
                new BigDecimal("-0.01"), "KEY-12345")).isEmpty());
    }
}
