package org.leap.executor.kafka;

import org.leap.events.EventEnvelope;
import org.leap.executor.exec.ExecutionService;

/**
 * Bridges {@link ResilientRecordProcessor}'s failure-handling wrapper (SEC4-617)
 * to the real pricing/settlement flow (SEC4-614/615): once a message has
 * passed structural validation and the order-existence check, this is what
 * actually prices and settles it.
 */
public class ExecutionServiceOrderProcessor implements OrderProcessor {

    private final ExecutionService executionService;

    public ExecutionServiceOrderProcessor(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @Override
    public void process(EventEnvelope<OrderEvent> envelope) {
        executionService.execute(envelope.payload().orderId());
    }
}
