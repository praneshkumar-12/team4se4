package org.leap.domain;

import org.junit.jupiter.api.Test;

import org.leap.exceptions.*;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionHierarchyTest {

    @Test
    void accountNotFoundShouldBeDomainException() {
        AccountNotFoundException exception =
                new AccountNotFoundException(10L);

        assertInstanceOf(
                DomainException.class,
                exception
        );

        assertEquals(
                "ACC-404",
                exception.getCode()
        );
    }

    @Test
    void accountNotActiveShouldBeDomainException() {
        AccountNotActiveException exception =
            new AccountNotActiveException(10L);

        assertInstanceOf(
            DomainException.class,
            exception
        );

        assertEquals(
            "ACC-403",
            exception.getCode()
        );
    }

    @Test
    void instrumentNotFoundShouldBeDomainException() {
        InstrumentNotFoundException exception =
                new InstrumentNotFoundException("AAPL");

        assertInstanceOf(
                DomainException.class,
                exception
        );

        assertEquals(
                "INS-404",
                exception.getCode()
        );
    }

    @Test
    void insufficientFundsShouldBeDomainException() {
        InsufficientFundsException exception =
                new InsufficientFundsException(
                    10L,
                    new BigDecimal("1000"),
                    new BigDecimal("1500")
                );

        assertInstanceOf(
                DomainException.class,
                exception
        );

    assertEquals(
            "ORD-400",
            exception.getCode()
    );
}

    @Test
    void insufficientHoldingsShouldBeDomainException() {
        InsufficientHoldingsException exception =
                new InsufficientHoldingsException(
                        10L,
                        20L,
                        new BigDecimal("5"),
                        new BigDecimal("10")
                );

        assertInstanceOf(
                DomainException.class,
                exception
        );

        assertEquals(
                "ORD-409",
                exception.getCode()
        );
    }

    @Test
    void duplicateOrderShouldBeDomainException() {
        DuplicateOrderException exception =
                new DuplicateOrderException("IDEMP001");

        assertInstanceOf(
                DomainException.class,
                exception
        );

        assertEquals(
                "ORD-409",
                exception.getCode()
        );
    }

    @Test
    void domainExceptionShouldExposeCodeNotHttpStatus() {
        DomainException exception =
                new AccountNotFoundException(10L);

        assertEquals(
                "ACC-404",
                exception.getCode()
        );

        // This deliberately verifies that the domain exception
        // does not need to expose an HTTP status.
        assertFalse(
                exception.getClass()
                        .getDeclaredFields()
                        .length > 0 &&
                java.util.Arrays.stream(
                        exception.getClass().getDeclaredFields()
                ).anyMatch(
                        field -> field.getName()
                                .equalsIgnoreCase("httpStatus")
                )
        );
    }
}