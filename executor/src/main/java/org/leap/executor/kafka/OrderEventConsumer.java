package org.leap.executor.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.leap.events.Topics;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Raw {@code kafka-clients} consumer for {@link Topics#ORDERS} — no Spring
 * Kafka, matching this sprint's teaching idiom. Each record's raw bytes are
 * handed to a {@link ResilientRecordProcessor}, which classifies and
 * resolves every failure itself (poison -> dead-letter immediately,
 * transient -> retry with backoff, then dead-letter once the budget is
 * spent) and always returns normally once a record is fully resolved,
 * whether that resolution was a real settlement or a dead-letter.
 *
 * <p>{@code enable.auto.commit} must be off in the {@link Properties} this
 * is constructed with: a record's offset is committed here, manually, only
 * once {@link ResilientRecordProcessor#process} has returned normally, so a
 * crash mid-processing replays that message rather than losing it (the
 * duplicate-delivery check in {@code ExecutionService} is what makes that
 * replay safe). If {@code process} lets an exception escape — something
 * neither classified as poison nor transient, i.e. a genuine bug — this
 * loop logs it and does not commit, rather than losing the record or
 * crashing the consumer thread.
 */
public class OrderEventConsumer implements Runnable {

    private static final Logger log = Logger.getLogger(OrderEventConsumer.class.getName());

    private final KafkaConsumer<String, byte[]> consumer;
    private final ResilientRecordProcessor processor;
    private volatile boolean running = true;

    public OrderEventConsumer(Properties consumerProps, ResilientRecordProcessor processor) {
        this.consumer = new KafkaConsumer<>(consumerProps);
        this.consumer.subscribe(List.of(Topics.ORDERS));
        this.processor = processor;
    }

    @Override
    public void run() {
        try {
            while (running) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, byte[]> record : records) {
                    if (handle(record)) {
                        commit(record);
                    }
                }
            }
        } finally {
            consumer.close();
        }
    }

    /** @return true if the record was fully resolved (settled or dead-lettered) and its offset should be committed. */
    private boolean handle(ConsumerRecord<String, byte[]> record) {
        try {
            processor.process(record.topic(), record.key(), record.value());
            return true;
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Unexpected failure processing record at offset " + record.offset()
                    + " on partition " + record.partition() + " — not committing, will be redelivered", e);
            return false;
        }
    }

    private void commit(ConsumerRecord<String, byte[]> record) {
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        consumer.commitSync(Map.of(partition, new OffsetAndMetadata(record.offset() + 1)));
    }

    public void stop() {
        running = false;
    }
}
