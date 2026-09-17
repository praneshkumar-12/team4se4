package org.leap.executor.exec;

import java.math.BigDecimal;

/** Payload carried by the ORDER_FILLED / ORDER_REJECTED events on {@code trade-events}. */
public record TradeSettledPayload(
        long orderId,
        long accountId,
        String status,
        BigDecimal executionPrice,
        String rejectionReason) {
}
