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
import org.leap.executor.fauxnance.FauxnanceClient;
import org.leap.executor.kafka.DeadLetterPublisher;
import org.leap.executor.kafka.KafkaDeadLetterPublisher;
import org.leap.executor.kafka.OrderEventConsumer;
import org.leap.executor.kafka.OrderExistenceChecker;
import org.leap.executor.kafka.OrderProcessor;
import org.leap.executor.kafka.ResilientRecordProcessor;
import org.leap.executor.poller.MarketDataPoller;
import org.leap.executor.poller.WatchedSymbolsRepository;
import org.leap.pricing.Quote;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Starts the order consumer and the market-data poller in the same process.
 * SEC4-614/615 own the real order-processing and Fauxnance-client wiring;
 * the placeholders below exist only so this module runs end-to-end and are
 * expected to be replaced at merge time.
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

        WatchedSymbolsRepository watchedSymbolsRepository = new WatchedSymbolsRepository(connectionFactory);
        FauxnanceClient fauxnanceClient = new UnwiredFauxnanceClient();
        long configuredPollIntervalSeconds = envLong("POLL_INTERVAL_SECONDS", MarketDataPoller.MIN_POLL_INTERVAL_SECONDS);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        MarketDataPoller poller = new MarketDataPoller(watchedSymbolsRepository, fauxnanceClient, producer,
                objectMapper, scheduler, configuredPollIntervalSeconds);
        poller.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            orderEventConsumer.stop();
            scheduler.shutdownNow();
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

    private static long envLong(String name, long defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    /** Replaced by SEC4-614's real Fauxnance HTTP client at merge time. */
    private static final class UnwiredFauxnanceClient implements FauxnanceClient {
        @Override
        public Quote getQuote(String symbol) {
            throw new UnsupportedOperationException("Fauxnance client not wired yet (see SEC4-614)");
        }

        @Override
        public Map<String, Quote> getQuotes(List<String> symbols) {
            throw new UnsupportedOperationException("Fauxnance client not wired yet (see SEC4-614)");
        }

        @Override
        public int getRemainingDailyBudget() {
            throw new UnsupportedOperationException("Fauxnance client not wired yet (see SEC4-614)");
        }
    }
}
