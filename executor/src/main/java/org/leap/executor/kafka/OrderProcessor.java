package org.leap.executor.kafka;

import org.leap.events.EventEnvelope;

/** The actual business processing for a validated order event (pricing, fill decision, settlement). */
@FunctionalInterface
public interface OrderProcessor {
    void process(EventEnvelope<OrderEvent> envelope);
}
