package org.leap.executor.kafka;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaDeadLetterPublisherTest {

    @Test
    void publishesOriginalBytesToDltTopicWithFailureReasonInHeaderNotPayload() {
        MockProducer<String, byte[]> mockProducer = new MockProducer<>(true, new StringSerializer(),
                (topic, data) -> data);
        KafkaDeadLetterPublisher publisher = new KafkaDeadLetterPublisher(mockProducer);
        byte[] originalValue = "{\"orderId\":42}".getBytes(StandardCharsets.UTF_8);

        publisher.sendToDlt("orders", "acc-1", originalValue, "Missing order identifier");

        List<ProducerRecord<String, byte[]>> sent = mockProducer.history();
        assertEquals(1, sent.size());
        ProducerRecord<String, byte[]> producerRecord = sent.get(0);
        assertEquals("orders.DLT", producerRecord.topic());
        assertEquals("acc-1", producerRecord.key());
        assertArrayEquals(originalValue, producerRecord.value());
        assertArrayEquals("Missing order identifier".getBytes(StandardCharsets.UTF_8),
                producerRecord.headers().lastHeader(KafkaDeadLetterPublisher.FAILURE_REASON_HEADER).value());
    }
}