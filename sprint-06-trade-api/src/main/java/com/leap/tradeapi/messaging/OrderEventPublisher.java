package com.leap.tradeapi.messaging;

import org.leap.events.EventEnvelope;
import org.leap.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Publishes {@code ORDER_PLACED} to {@link Topics#ORDERS}, keyed by account,
 * after the caller's transaction has committed - never before.
 *
 * <p>Why after and not before: an order that committed but was never
 * published can always be replayed later, because it is sitting right there
 * in the {@code orders} table at {@code NEW}. An event published for a
 * transaction that then rolls back can never be un-sent. On any doubt,
 * "committed but not yet published" is the recoverable failure, so that is
 * the one this class risks.
 *
 * <p>{@link #publishOrderPlaced(OrderPlacedPayload)} must be called from
 * inside an active Spring transaction (it registers a
 * {@link TransactionSynchronization} and defers the actual send to that
 * synchronization's {@code afterCommit()}); calling it with no transaction
 * active is a programming error and Spring throws for it.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    // A dedicated mapper, not the request-parsing one Spring wires for the
    // controllers: FAIL_ON_UNKNOWN_PROPERTIES is disabled here as the
    // platform-wide convention every consumer must follow (design/kafka.md),
    // which is a Kafka-payload concern, not an HTTP-request-parsing one.
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderPlaced(OrderPlacedPayload payload) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(payload);
            }
        });
    }

    private void send(OrderPlacedPayload payload) {
        EventEnvelope<OrderPlacedPayload> envelope = EventEnvelope.of("ORDER_PLACED", "trade-api", payload);
        String key = String.valueOf(payload.accountId());

        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(Topics.ORDERS, key, json);
        } catch (Exception e) {
            // Publishing is best-effort after commit: the order is already
            // durable at NEW, so a failure here is logged, not thrown - there
            // is no request left to fail, and the order can be replayed later.
            log.error("Failed to publish ORDER_PLACED for order {} (account {})",
                    payload.orderId(), payload.accountId(), e);
        }
    }
}
