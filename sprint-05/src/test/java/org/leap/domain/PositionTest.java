package org.leap.domain;

import org.junit.jupiter.api.Test;
import org.leap.exceptions.*;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PositionTest {

    @Test
    void shouldCreateEmptyPosition() {
        Position position = new Position(
                1L,
                100L,
                200L,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        assertEquals(1L, position.getHoldingId());
        assertEquals(100L, position.getAccountId());
        assertEquals(200L, position.getInstrumentId());
        assertEquals(BigDecimal.ZERO, position.getQuantity());
        assertEquals(BigDecimal.ZERO, position.getAverageCost());
    }

    @Test
    void buyShouldIncreaseQuantity() {
        Position position = position(
                "10",
                "100"
        );

        position.buy(
                new BigDecimal("5"),
                new BigDecimal("120")
        );

        assertEquals(
                new BigDecimal("15"),
                position.getQuantity()
        );
    }

    @Test
    void buyShouldRecalculateAverageCost() {
        Position position = position(
                "10",
                "100"
        );

        position.buy(
                new BigDecimal("10"),
                new BigDecimal("120")
        );

        assertEquals(
                new BigDecimal("110"),
                position.getAverageCost()
        );
    }

    @Test
    void buyIntoZeroPositionShouldSetAverageCostToBuyPrice() {
        Position position = position(
                "0",
                "0"
        );

        position.buy(
                new BigDecimal("10"),
                new BigDecimal("150")
        );

        assertEquals(
                new BigDecimal("10"),
                position.getQuantity()
        );

        assertEquals(
                new BigDecimal("150"),
                position.getAverageCost()
        );
    }

    @Test
    void sellShouldReduceQuantity() {
        Position position = position(
                "10",
                "100"
        );

        position.sell(new BigDecimal("4"));

        assertEquals(
                new BigDecimal("6"),
                position.getQuantity()
        );
    }

    @Test
    void sellShouldNotChangeAverageCost() {
        Position position = position(
                "10",
                "100"
        );

        position.sell(new BigDecimal("4"));

        assertEquals(
                new BigDecimal("100"),
                position.getAverageCost()
        );
    }

    @Test
    void shouldAllowSellingEntirePosition() {
        Position position = position(
                "10",
                "100"
        );

        position.sell(new BigDecimal("10"));

        assertEquals(
                BigDecimal.ZERO,
                position.getQuantity()
        );

        assertEquals(
                new BigDecimal("100"),
                position.getAverageCost()
        );
    }

    @Test
    void shouldRejectSellingMoreThanHeld() {
        Position position = position(
                "10",
                "100"
        );

        assertThrows(
                InsufficientHoldingsException.class,
                () -> position.sell(new BigDecimal("10.00000001"))
        );

        assertEquals(
                new BigDecimal("10"),
                position.getQuantity()
        );
    }

    @Test
    void shouldRejectNegativeQuantityBuy() {
        Position position = position(
                "10",
                "100"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> position.buy(
                        new BigDecimal("-1"),
                        new BigDecimal("100")
                )
        );
    }

    @Test
    void shouldRejectZeroQuantityBuy() {
        Position position = position(
                "10",
                "100"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> position.buy(
                        BigDecimal.ZERO,
                        new BigDecimal("100")
                )
        );
    }

    @Test
    void shouldRejectNegativeSellQuantity() {
        Position position = position(
                "10",
                "100"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> position.sell(new BigDecimal("-1"))
        );
    }

    private Position position(String quantity, String averageCost) {
        return new Position(
                1L,
                100L,
                200L,
                new BigDecimal(quantity),
                new BigDecimal(averageCost)
        );
    }
}