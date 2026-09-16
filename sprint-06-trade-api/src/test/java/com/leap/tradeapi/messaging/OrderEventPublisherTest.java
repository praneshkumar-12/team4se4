package com.leap.tradeapi.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leap.events.Topics;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private OrderEventPublisher publisher;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(kafkaTemplate);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private OrderPlacedPayload payload() {
        return new OrderPlacedPayload(
                "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e",
                1L,
                "AAPL",
                "BUY",
                100,
                new BigDecimal("233.00"),
                "idem-key-001",
                Instant.parse("2026-09-28T09:14:22Z"));
    }

    @Test
    void nothingIsSentBeforeTheTransactionCommits() {
        publisher.publishOrderPlaced(payload());

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void theEnvelopeIsSentToTheOrdersTopicKeyedByAccountOnlyAfterCommit() throws Exception {
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishOrderPlaced(payload());
        verify(kafkaTemplate, never()).send(any(), any(), any());

        triggerAfterCommit();

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topic.capture(), key.capture(), value.capture());

        assertThat(topic.getValue()).isEqualTo(Topics.ORDERS);
        assertThat(key.getValue()).isEqualTo("1");

        JsonNode envelope = objectMapper.readTree(value.getValue());
        assertThat(envelope.get("eventType").asText()).isEqualTo("ORDER_PLACED");
        assertThat(envelope.get("source").asText()).isEqualTo("trade-api");
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("eventId").asText()).isNotBlank();
        assertThat(envelope.get("payload").get("symbol").asText()).isEqualTo("AAPL");
        assertThat(envelope.get("payload").get("accountId").asLong()).isEqualTo(1L);
    }

    /** Simulates Spring committing the transaction: fires every registered synchronization's afterCommit(). */
    private void triggerAfterCommit() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
    }
}
