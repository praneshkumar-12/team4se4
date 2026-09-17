package org.leap.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.leap.events.Topics;
import org.leap.executor.db.AccountRepository;
import org.leap.executor.db.ConnectionFactory;
import org.leap.executor.db.InstrumentRepository;
import org.leap.executor.db.JdbcOrderExistenceChecker;
import org.leap.executor.db.OrderRepository;
import org.leap.executor.exec.ExecutionService;
import org.leap.executor.exec.JdbcSettlementService;
import org.leap.executor.exec.RetryPolicy;
import org.leap.executor.exec.SettlementService;
import org.leap.executor.fauxnance.FauxnanceClient;
import org.leap.executor.fauxnance.FauxnanceHttpClient;
import org.leap.executor.kafka.DeadLetterPublisher;
import org.leap.executor.kafka.ExecutionServiceOrderProcessor;
import org.leap.executor.kafka.KafkaDeadLetterPublisher;
import org.leap.executor.kafka.KafkaTradeEventPublisher;
import org.leap.executor.kafka.OrderEventConsumer;
import org.leap.executor.kafka.OrderExistenceChecker;
import org.leap.executor.kafka.OrderProcessor;
import org.leap.executor.kafka.ResilientRecordProcessor;
import org.leap.executor.kafka.TradeEventPublisher;
import org.leap.executor.poller.MarketDataPoller;
import org.leap.executor.poller.WatchedSymbolsRepository;
import org.leap.executor.db.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Starts the order consumer and the market-data poller in the same process. */
public final class Main {

    private static final String KAFKA_BOOTSTRAP_SERVERS_ENV = "KAFKA_BOOTSTRAP_SERVERS";

    private Main() {}

    public static void main(String[] args) {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Producer<String, byte[]> producer = new KafkaProducer<>(producerProps());
        ConnectionFactory connectionFactory = jdbcConnectionFactory();

        DeadLetterPublisher deadLetterPublisher = new KafkaDeadLetterPublisher(producer);
        RetryPolicy retryPolicy = new RetryPolicy(deadLetterPublisher);
        OrderExistenceChecker orderExistenceChecker = new JdbcOrderExistenceChecker(connectionFactory);

        DataSource dataSource = jdbcDataSource();
        TradeEventPublisher tradeEventPublisher = new KafkaTradeEventPublisher(requireEnv(KAFKA_BOOTSTRAP_SERVERS_ENV));
        SettlementService settlementService = new JdbcSettlementService(dataSource, tradeEventPublisher);
        FauxnanceClient fauxnanceClient = new FauxnanceHttpClient();
        ExecutionService executionService = new ExecutionService(
                new OrderRepository(connectionFactory),
                new InstrumentRepository(connectionFactory),
                new AccountRepository(connectionFactory),
                fauxnanceClient,
                settlementService);
        OrderProcessor orderProcessor = new ExecutionServiceOrderProcessor(executionService);

        ResilientRecordProcessor recordProcessor = new ResilientRecordProcessor(
                objectMapper, orderExistenceChecker, orderProcessor, retryPolicy, deadLetterPublisher,
                Set.of("ORDER_PLACED"));

        OrderEventConsumer orderEventConsumer = new OrderEventConsumer(consumerProps(), recordProcessor);
        Thread consumerThread = new Thread(orderEventConsumer, "order-event-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();

        WatchedSymbolsRepository watchedSymbolsRepository = new WatchedSymbolsRepository(connectionFactory);
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
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, requireEnv(KAFKA_BOOTSTRAP_SERVERS_ENV));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        return props;
    }

    private static Properties consumerProps() {
        Properties props = new Properties();
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, requireEnv(KAFKA_BOOTSTRAP_SERVERS_ENV));
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, Topics.EXECUTOR_CONSUMER_GROUP);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.ByteArrayDeserializer.class.getName());
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return props;
    }

    private static ConnectionFactory jdbcConnectionFactory() {
        String url = requireEnv("DB_URL");
        String user = requireEnv("DB_USER");
        String password = requireEnv("DB_PASSWORD");
        return () -> DriverManager.getConnection(url, user, password);
    }

    private static DataSource jdbcDataSource() {
        return new DriverManagerDataSource(requireEnv("DB_URL"), requireEnv("DB_USER"), requireEnv("DB_PASSWORD"));
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
}