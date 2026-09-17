package org.leap.executor.kafka;

/** Minimal order-event payload the executor's failure-handling wrapper needs: just enough to classify a message. */
public record OrderEvent(long orderId) {}
