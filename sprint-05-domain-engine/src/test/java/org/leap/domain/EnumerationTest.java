package org.leap.domain;

import org.junit.jupiter.api.Test;
import org.leap.domain.enums.*;


import static org.junit.jupiter.api.Assertions.*;

class EnumerationTest {

    @Test
    void accountStatusShouldContainExactlyRequiredValues() {
        assertArrayEquals(
                new AccountStatus[]{
                        AccountStatus.ACTIVE,
                        AccountStatus.SUSPENDED,
                        AccountStatus.CLOSED
                },
                AccountStatus.values()
        );
    }

    @Test
    void orderSideShouldContainExactlyRequiredValues() {
        assertArrayEquals(
                new OrderSide[]{
                        OrderSide.BUY,
                        OrderSide.SELL
                },
                OrderSide.values()
        );
    }

    @Test
    void orderStatusShouldContainExactlyRequiredValues() {
        assertArrayEquals(
                new OrderStatus[]{
                        OrderStatus.NEW,
                        OrderStatus.FILLED,
                        OrderStatus.REJECTED,
                        OrderStatus.CANCELLED
                },
                OrderStatus.values()
        );
    }

    @Test
    void cancelledSpellingShouldBeExactlyCorrect() {
        assertNotNull(
                OrderStatus.valueOf("CANCELLED")
        );
    }

    @Test
    void shouldNotContainPartialFillStatus() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderStatus.valueOf("PARTIALLY_FILLED")
        );
    }
}