package org.leap.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.leap.domain.Order;
import org.leap.domain.enums.OrderSide;


public final class FillRule {

    private static final int PRICE_SCALE = 8;

    private FillRule() {
    }

    public static FillDecision decide(Order order, Quote quote) {
        BigDecimal limitPrice = order.getLimitPrice();

        if (order.getSide() == OrderSide.BUY) {
            BigDecimal ask = round(quote.ask());
            if (limitPrice.compareTo(ask) >= 0) {
                return FillDecision.fillAt(ask);
            }
            return FillDecision.reject("NOT_MARKETABLE");
        }

        BigDecimal bid = round(quote.bid());
        if (limitPrice.compareTo(bid) <= 0) {
            return FillDecision.fillAt(bid);
        }
        return FillDecision.reject("NOT_MARKETABLE");
    }

    private static BigDecimal round(BigDecimal price) {
        return price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
