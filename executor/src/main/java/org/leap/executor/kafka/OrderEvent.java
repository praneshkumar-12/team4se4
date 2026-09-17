package org.leap.executor.kafka;

/**
 * Minimal order-event payload the executor's failure-handling wrapper needs:
 * just enough to classify a message. {@code orderId} is the order's public
 * UUID (the {@code orders.public_id} column, matching {@code contracts/kafka-topics.md}
 * and what {@code trade-api}'s {@code OrderPlacedPayload} actually sends) —
 * never the internal numeric {@code orders.order_id} surrogate key, which
 * this platform never puts on the wire.
 */
public record OrderEvent(String orderId) {}
