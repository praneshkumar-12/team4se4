package com.leap.tradeapi.messaging;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code orders} topic payload for {@code eventType=ORDER_PLACED}, from contracts/kafka-topics.md. */
public record OrderPlacedPayload(
        String orderId,
        long accountId,
        String symbol,
        String side,
        int quantity,
        BigDecimal price,
        String idempotencyKey,
        Instant createdOn) {
}