package org.leap.executor.kafka;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;

/**
 * Publishes to {@code <originalTopic>.DLT}, keeping the original message
 * bytes as the value unchanged and carrying the failure reason in a header
 * rather than the payload.
 */
public class KafkaDeadLetterPublisher implements DeadLetterPublisher {

    public static final String DLT_SUFFIX = ".DLT";
    public static final String FAILURE_REASON_HEADER = "x-failure-reason";

    private final Producer<String, byte[]> producer;

    public KafkaDeadLetterPublisher(Producer<String, byte[]> producer) {
        this.producer = producer;
    }

    @Override
    public void sendToDlt(String originalTopic, String key, byte[] originalMessageValue, String failureReason) {
        String dltTopic = originalTopic.endsWith(DLT_SUFFIX) ? originalTopic : originalTopic + DLT_SUFFIX;
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(dltTopic, key, originalMessageValue);
        record.headers().add(new RecordHeader(FAILURE_REASON_HEADER, failureReason.getBytes(StandardCharsets.UTF_8)));
        producer.send(record);
    }
}
