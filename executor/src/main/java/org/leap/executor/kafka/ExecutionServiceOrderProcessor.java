package org.leap.executor.kafka;

import org.leap.events.EventEnvelope;
import org.leap.executor.exec.ExecutionService;

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
