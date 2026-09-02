package org.leap.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void newOrderShouldStartInNewStatus() {
        Order order = marketBuyOrder();

        assertEquals(
                OrderStatus.NEW,
                order.getStatus()
        );
    }

    @Test
    void shouldFillNewOrder() {
        Order order = marketBuyOrder();

        order.fill();

        assertEquals(
                OrderStatus.FILLED,
                order.getStatus()
        );
    }

    @Test
    void shouldRejectNewOrder() {
        Order order = marketBuyOrder();

        order.reject();

        assertEquals(
                OrderStatus.REJECTED,
                order.getStatus()
        );
    }

    @Test
    void shouldCancelNewOrder() {
        Order order = marketBuyOrder();

        order.cancel();

        assertEquals(
                OrderStatus.CANCELLED,
                order.getStatus()
        );
    }

    @Test
    void shouldNotFillAlreadyFilledOrder() {
        Order order = marketBuyOrder();

        order.fill();

        assertThrows(
                IllegalStateException.class,
                order::fill
        );
    }

    @Test
    void shouldNotCancelFilledOrder() {
        Order order = marketBuyOrder();

        order.fill();

        assertThrows(
                IllegalStateException.class,
                order::cancel
        );
    }

    @Test
    void shouldNotRejectFilledOrder() {
        Order order = marketBuyOrder();

        order.fill();

        assertThrows(
                IllegalStateException.class,
                order::reject
        );
    }

    @Test
    void shouldStoreSubmittedLimitPrice() {
        Order order = new Order(
                1L,
                "IDEMP-001",
                100L,
                200L,
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("150.50")
        );

        assertEquals(
                new BigDecimal("150.50"),
                order.getLimitPrice()
        );
    }

    @Test
    void marketOrderShouldNotHaveLimitPrice() {
        Order order = marketBuyOrder();

        assertNull(order.getLimitPrice());
        assertEquals(
                OrderType.MARKET,
                order.getOrderType()
        );
    }

    @Test
    void shouldExposeOrderDetails() {
        Order order = new Order(
                1L,
                "IDEMP-001",
                100L,
                200L,
                OrderSide.SELL,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("150.50")
        );

        assertEquals(1L, order.getOrderId());
        assertEquals("IDEMP-001", order.getIdempotencyKey());
        assertEquals(100L, order.getAccountId());
        assertEquals(200L, order.getInstrumentId());
        assertEquals(OrderSide.SELL, order.getSide());
        assertEquals(OrderType.LIMIT, order.getOrderType());
        assertEquals(new BigDecimal("10"), order.getQuantity());
        assertEquals(new BigDecimal("150.50"), order.getLimitPrice());
    }

    private Order marketBuyOrder() {
        return new Order(
                1L,
                "IDEMP-001",
                100L,
                200L,
                OrderSide.BUY,
                OrderType.MARKET,
                new BigDecimal("10"),
                null
        );
    }
}