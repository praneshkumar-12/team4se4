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

/**
 * Minimal stand-in for SEC4-614's consumer: reads {@link Topics#ORDERS},
 * hands each record's raw bytes to a {@link ResilientRecordProcessor}. The
 * real base loop (SEC4-614) is expected to replace this class at merge
 * time; this ticket's failure-handling wrapping layers on top of whatever
 * loop shape they land on.
 */
public class OrderEventConsumer implements Runnable {

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
                for (ConsumerRecord<String, byte[]> consumerRecord : records) {
                    processor.process(consumerRecord.topic(), consumerRecord.key(), consumerRecord.value());
                    consumer.commitSync(Map.of(
                            new TopicPartition(consumerRecord.topic(), consumerRecord.partition()),
                            new OffsetAndMetadata(consumerRecord.offset() + 1)));
                }
            }
        } finally {
            consumer.close();
        }
    }

    public void stop() {
        running = false;
    }
}
