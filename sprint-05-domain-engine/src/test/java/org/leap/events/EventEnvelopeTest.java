package org.leap.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    void constructorStoresEveryFieldAsGiven() {

        Instant eventTime = Instant.parse("2026-09-16T09:00:00Z");
        String payload = "payload-value";

        EventEnvelope<String> envelope = new EventEnvelope<>(
                "b19d2c5a-8f31-4d0e-9a77-1c3e5f7a9b0d",
                "ORDER_PLACED",
                eventTime,
                "trade-api",
                1,
                payload
        );

        assertEquals("b19d2c5a-8f31-4d0e-9a77-1c3e5f7a9b0d", envelope.eventId());
        assertEquals("ORDER_PLACED", envelope.eventType());
        assertEquals(eventTime, envelope.eventTime());
        assertEquals("trade-api", envelope.source());
        assertEquals(1, envelope.schemaVersion());
        assertEquals(payload, envelope.payload());
    }

    @Test
    void ofStampsARandomEventIdOnEveryCall() {

        EventEnvelope<String> first = EventEnvelope.of("ORDER_PLACED", "trade-api", "a");
        EventEnvelope<String> second = EventEnvelope.of("ORDER_PLACED", "trade-api", "a");

        assertNotNull(first.eventId());
        assertTrue(isValidUuid(first.eventId()));
        assertNotNull(second.eventId());
        assertTrue(isValidUuid(second.eventId()));
        assertTrue(!first.eventId().equals(second.eventId()));
    }

    @Test
    void ofStampsTheCurrentTimeAsTheEventTime() {

        Instant before = Instant.now();
        EventEnvelope<String> envelope = EventEnvelope.of("ORDER_PLACED", "trade-api", "a");
        Instant after = Instant.now();

        assertNotNull(envelope.eventTime());
        assertTrue(!envelope.eventTime().isBefore(before));
        assertTrue(!envelope.eventTime().isAfter(after));
    }

    @Test
    void ofStampsSchemaVersionOne() {

        EventEnvelope<String> envelope = EventEnvelope.of("ORDER_PLACED", "trade-api", "a");

        assertEquals(1, envelope.schemaVersion());
    }

    @Test
    void ofCarriesTheGivenEventTypeSourceAndPayload() {

        EventEnvelope<String> envelope = EventEnvelope.of("ORDER_PLACED", "trade-api", "payload-value");

        assertEquals("ORDER_PLACED", envelope.eventType());
        assertEquals("trade-api", envelope.source());
        assertEquals("payload-value", envelope.payload());
    }

    private static boolean isValidUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}