package org.leap.pricing;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.leap.domain.Order;
import org.leap.domain.enums.OrderSide;
import org.leap.domain.enums.OrderType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FillRuleTest {

    private static Order order(OrderSide side, String limitPrice) {
        return new Order(1L, "idem-1", 1L, 1L, side, OrderType.LIMIT, BigDecimal.TEN, new BigDecimal(limitPrice));
    }

    @Test
    void a_buy_limit_at_or_above_the_quote_fills_at_the_quote() {
        Quote quote = new Quote("ACME", new BigDecimal("25.00"), new BigDecimal("25.40"), new BigDecimal("25.20"));

        FillDecision decision = FillRule.decide(order(OrderSide.BUY, "25.50"), quote);

        assertTrue(decision.fill());
        // Execution price is the ask, rounded to scale 8 (NUMERIC(20,8)) before being returned.
        assertEquals(0, new BigDecimal("25.40000000").compareTo(decision.executionPrice()));
        assertNull(decision.rejectionReason());
    }

    @Test
    void a_sell_limit_at_or_below_the_quote_fills_at_the_quote() {
        Quote quote = new Quote("ACME", new BigDecimal("25.00"), new BigDecimal("25.40"), new BigDecimal("25.20"));

        FillDecision decision = FillRule.decide(order(OrderSide.SELL, "24.90"), quote);

        assertTrue(decision.fill());
        // Execution price is the bid, rounded to scale 8 before being returned.
        assertEquals(0, new BigDecimal("25.00000000").compareTo(decision.executionPrice()));
        assertNull(decision.rejectionReason());
    }

    @Test
    void an_order_outside_the_marketable_range_is_rejected() {
        Quote quote = new Quote("ACME", new BigDecimal("25.00"), new BigDecimal("25.40"), new BigDecimal("25.20"));

        // A BUY limit below the ask cannot be filled at the ask.
        FillDecision decision = FillRule.decide(order(OrderSide.BUY, "25.00"), quote);

        assertFalse(decision.fill());
        assertNull(decision.executionPrice());
        assertEquals("NOT_MARKETABLE", decision.rejectionReason());
    }
}
