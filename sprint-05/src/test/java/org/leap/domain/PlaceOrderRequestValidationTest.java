package org.leap.domain;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PlaceOrderRequestValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        factory.close();
    }

    private PlaceOrderRequest valid() {
        return new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 1L,
                new BigDecimal("10.00"), "KEY-12345");
    }

    private Set<?> violations(PlaceOrderRequest request) {
        return validator.validate(request);
    }

    @Test void validRequestHasNoViolations() {
        assertTrue(violations(valid()).isEmpty());
    }

    @Test void accountIdZeroIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                0L, "AAPL", OrderSide.BUY, 1L,
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void accountIdOneIsValidBoundary() {
        assertTrue(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 1L,
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void nullAccountIdIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                null, "AAPL", OrderSide.BUY, 1L,
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void blankSymbolIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                1L, " ", OrderSide.BUY, 1L,
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void twentyCharacterSymbolIsValidBoundary() {
        assertTrue(violations(new PlaceOrderRequest(
                1L, "ABCDEFGHIJKLMNOPQRST", OrderSide.BUY, 1L,
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void twentyOneCharacterSymbolIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                1L, "ABCDEFGHIJKLMNOPQRSTU", OrderSide.BUY, 1L,
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void nullSideIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                1L, "AAPL", null, 1L,
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void zeroQuantityIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 0L,
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void quantityOneIsValidBoundary() {
        assertTrue(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 1L,
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void nullQuantityIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, null,
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void zeroPriceIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 1L,
                BigDecimal.ZERO, "KEY-12345")).isEmpty());
    }

    @Test void positiveTwoDecimalPriceIsValid() {
        assertTrue(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 1L,
                new BigDecimal("0.01"), "KEY-12345")).isEmpty());
    }

    @Test void moreThanTwoPriceDecimalsIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 1L,
                new BigDecimal("10.001"), "KEY-12345")).isEmpty());
    }

    @Test void nullPriceIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 1L,
                null, "KEY-12345")).isEmpty());
    }

    @Test void sevenCharacterIdempotencyKeyIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 1L,
                new BigDecimal("10.00"), "1234567")).isEmpty());
    }

    @Test void eightCharacterIdempotencyKeyIsValidBoundary() {
        assertTrue(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 1L,
                new BigDecimal("10.00"), "12345678")).isEmpty());
    }

    @Test void oneHundredCharacterIdempotencyKeyIsValidBoundary() {
        String key = "A".repeat(100);
        assertTrue(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 1L,
                new BigDecimal("10.00"), key)).isEmpty());
    }

    @Test void oneHundredOneCharacterIdempotencyKeyIsInvalid() {
        String key = "A".repeat(101);
        assertFalse(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 1L,
                new BigDecimal("10.00"), key)).isEmpty());
    }

    @Test void blankIdempotencyKeyIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 1L,
                new BigDecimal("10.00"), "        ")).isEmpty());
    }

    @Test void nullIdempotencyKeyIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 1L,
                new BigDecimal("10.00"), null)).isEmpty());
    }

    @Test void sellIsValidSide() {
        assertTrue(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.SELL, 1L,
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void negativeAccountIdIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                -1L, "AAPL", OrderSide.BUY, 1L,
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void negativeQuantityIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, -1L,
                new BigDecimal("10.00"), "KEY-12345")).isEmpty());
    }

    @Test void negativePriceIsInvalid() {
        assertFalse(violations(new PlaceOrderRequest(
                1L, "AAPL", OrderSide.BUY, 1L,
                new BigDecimal("-0.01"), "KEY-12345")).isEmpty());
    }
}
