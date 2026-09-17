package org.leap.executor.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.leap.events.EventEnvelope;
import org.leap.executor.exec.PoisonMessageException;
import org.leap.executor.exec.RetryPolicy;
import org.leap.executor.exec.TransientProcessingException;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Wraps a single Kafka record's processing with the failure classification
 * this ticket requires:
 * <ul>
 *   <li>malformed JSON, a missing order identifier, an order identifier not
 *       in Postgres, or an unexpected event type -&gt; dead-lettered immediately,
 *       never retried;</li>
 *   <li>everything else (broker/DB connectivity, an exhausted optimistic-lock
 *       budget) -&gt; retried with growing backoff, dead-lettered only once the
 *       budget is spent.</li>
 * </ul>
 * A poison record dead-letters and returns normally, so the caller's loop
 * over the rest of the batch/partition continues.
 */
public class ResilientRecordProcessor {

    private final ObjectMapper objectMapper;
    private final OrderExistenceChecker orderExistenceChecker;
    private final OrderProcessor orderProcessor;
    private final RetryPolicy retryPolicy;
    private final DeadLetterPublisher deadLetterPublisher;
    private final Set<String> expectedEventTypes;

    public ResilientRecordProcessor(ObjectMapper objectMapper,
                                     OrderExistenceChecker orderExistenceChecker,
                                     OrderProcessor orderProcessor,
                                     RetryPolicy retryPolicy,
                                     DeadLetterPublisher deadLetterPublisher,
                                     Set<String> expectedEventTypes) {
        this.objectMapper = objectMapper;
        this.orderExistenceChecker = orderExistenceChecker;
        this.orderProcessor = orderProcessor;
        this.retryPolicy = retryPolicy;
        this.deadLetterPublisher = deadLetterPublisher;
        this.expectedEventTypes = expectedEventTypes;
    }

    public void process(String topic, String key, byte[] value) {
        EventEnvelope<OrderEvent> envelope;
        try {
            envelope = parseStructure(value);
        } catch (PoisonMessageException e) {
            deadLetterPublisher.sendToDlt(topic, key, value, e.getMessage());
            return;
        }

        try {
            retryPolicy.execute(topic, key, value, () -> validateAndHandle(envelope));
        } catch (PoisonMessageException e) {
            deadLetterPublisher.sendToDlt(topic, key, value, e.getMessage());
        }
    }

    /** Structural checks that can never be fixed by retrying: bad JSON, missing order id, unknown event type. */
    private EventEnvelope<OrderEvent> parseStructure(byte[] value) {
        JsonNode root;
        try {
            root = objectMapper.readTree(value);
        } catch (IOException e) {
            throw new PoisonMessageException("Malformed JSON: " + e.getMessage());
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            throw new PoisonMessageException("Empty message");
        }

        String eventType = root.path("eventType").asText(null);
        if (eventType == null || !expectedEventTypes.contains(eventType)) {
            throw new PoisonMessageException("Unexpected event type: " + eventType);
        }

        JsonNode payload = root.path("payload");
        if (!payload.hasNonNull("orderId")) {
            throw new PoisonMessageException("Missing order identifier");
        }
        long orderId = payload.get("orderId").asLong();

        String eventId = root.path("eventId").asText(null);
        if (eventId == null) {
            eventId = UUID.randomUUID().toString();
        }
        String source = root.path("source").asText(null);
        int schemaVersion = root.path("schemaVersion").asInt(1);
        Instant eventTime = parseEventTime(root.path("eventTime").asText(null));

        return new EventEnvelope<>(eventId, eventType, eventTime, source, schemaVersion, new OrderEvent(orderId));
    }

    private Instant parseEventTime(String raw) {
        if (raw == null) {
            return Instant.now();
        }
        try {
            return Instant.parse(raw);
        } catch (RuntimeException e) {
            return Instant.now();
        }
    }

    /** The order-existence lookup and downstream processing, both retryable on transient failure. */
    private void validateAndHandle(EventEnvelope<OrderEvent> envelope) {
        boolean exists;
        try {
            exists = orderExistenceChecker.exists(envelope.payload().orderId());
        } catch (OrderLookupException e) {
            throw new TransientProcessingException("Order lookup failed for order " + envelope.payload().orderId(), e);
        }
        if (!exists) {
            throw new PoisonMessageException("Order " + envelope.payload().orderId() + " does not exist");
        }
        orderProcessor.process(envelope);
    }
}