package org.leap.executor.kafka;

import org.leap.events.EventEnvelope;

/**
 * Seam between {@code SettlementService} and Kafka, so settlement's
 * commit-then-publish ordering can be unit tested with a mock instead of a
 * live broker.
 */
public interface TradeEventPublisher {
    void publish(String topic, String key, EventEnvelope<?> envelope);
}
