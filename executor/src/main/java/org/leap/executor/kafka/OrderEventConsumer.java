package org.leap.executor.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.leap.events.Topics;

import java.time.Duration;
import java.util.List;
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
                for (ConsumerRecord<String, byte[]> record : records) {
                    processor.process(record.topic(), record.key(), record.value());
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