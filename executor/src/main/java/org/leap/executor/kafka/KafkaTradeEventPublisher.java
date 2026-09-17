package org.leap.executor.kafka;

import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.leap.events.EventEnvelope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON-over-Kafka implementation of {@link TradeEventPublisher}. {@code
 * publish} blocks on the producer's future so a caller that returns
 * normally knows the broker has acked the record - that's what lets
 * SettlementService guarantee "publish before offset commit".
 */
public final class KafkaTradeEventPublisher implements TradeEventPublisher, AutoCloseable {

    private final Producer<String, String> producer;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public KafkaTradeEventPublisher(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        this.producer = new KafkaProducer<>(props);
    }

    KafkaTradeEventPublisher(Producer<String, String> producer) {
        this.producer = producer;
    }

    @Override
    public void publish(String topic, String key, EventEnvelope<?> envelope) {
        try {
            String json = mapper.writeValueAsString(envelope);
            producer.send(new ProducerRecord<>(topic, key, json)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted publishing event " + envelope.eventId() + " to " + topic, e);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to publish event " + envelope.eventId() + " to " + topic, e);
        }
    }

    @Override
    public void close() {
        producer.close();
    }
}
