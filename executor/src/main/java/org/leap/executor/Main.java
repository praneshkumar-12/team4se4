package org.leap.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.leap.events.Topics;
import org.leap.executor.db.ConnectionFactory;
import org.leap.executor.db.JdbcOrderExistenceChecker;
import org.leap.executor.exec.RetryPolicy;
import org.leap.executor.kafka.DeadLetterPublisher;
import org.leap.executor.kafka.KafkaDeadLetterPublisher;
import org.leap.executor.kafka.OrderEventConsumer;
import org.leap.executor.kafka.OrderExistenceChecker;
import org.leap.executor.kafka.OrderProcessor;
import org.leap.executor.kafka.ResilientRecordProcessor;

import java.sql.DriverManager;
import java.util.Properties;
import java.util.Set;

/**
 * Starts the order consumer. SEC4-614/615 own the real order-processing
 * wiring; the no-op {@code orderProcessor} below exists only so this module
 * runs end-to-end and is expected to be replaced at merge time.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Producer<String, byte[]> producer = new KafkaProducer<>(producerProps());
        ConnectionFactory connectionFactory = jdbcConnectionFactory();

        DeadLetterPublisher deadLetterPublisher = new KafkaDeadLetterPublisher(producer);
        RetryPolicy retryPolicy = new RetryPolicy(deadLetterPublisher);
        OrderExistenceChecker orderExistenceChecker = new JdbcOrderExistenceChecker(connectionFactory);
        OrderProcessor orderProcessor = envelope -> { };

        ResilientRecordProcessor recordProcessor = new ResilientRecordProcessor(
                objectMapper, orderExistenceChecker, orderProcessor, retryPolicy, deadLetterPublisher,
                Set.of("ORDER_PLACED"));

        OrderEventConsumer orderEventConsumer = new OrderEventConsumer(consumerProps(), recordProcessor);
        Thread consumerThread = new Thread(orderEventConsumer, "order-event-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            orderEventConsumer.stop();
            producer.close();
        }));
    }

    private static Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, requireEnv("KAFKA_BOOTSTRAP_SERVERS"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        return props;
    }

    private static Properties consumerProps() {
        Properties props = new Properties();
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, requireEnv("KAFKA_BOOTSTRAP_SERVERS"));
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, Topics.EXECUTOR_CONSUMER_GROUP);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.ByteArrayDeserializer.class.getName());
        return props;
    }

    private static ConnectionFactory jdbcConnectionFactory() {
        String url = requireEnv("DB_URL");
        String user = requireEnv("DB_USER");
        String password = requireEnv("DB_PASSWORD");
        return () -> DriverManager.getConnection(url, user, password);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }
}
