package org.leap.pricing;

import java.math.BigDecimal;

public record FillDecision(boolean fill, BigDecimal executionPrice, String rejectionReason) {
    public static FillDecision fillAt(BigDecimal price) { return new FillDecision(true, price, null); }
    public static FillDecision reject(String reason) { return new FillDecision(false, null, reason); }
}
