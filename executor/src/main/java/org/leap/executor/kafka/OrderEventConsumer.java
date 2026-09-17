package org.leap.executor.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.leap.events.EventEnvelope;
import org.leap.events.Topics;
import org.leap.executor.exec.ExecutionService;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Raw {@code kafka-clients} consumer for {@link Topics#ORDERS} — no Spring
 * Kafka, matching this sprint's teaching idiom. {@code enable.auto.commit} is
 * off: a record's offset is committed only once {@link ExecutionService} has
 * fully handled it, so a crash mid-processing replays that message rather
 * than losing it (the duplicate-delivery check in {@code ExecutionService}
 * is what makes that replay safe).
 *
 * <p>Retry/dead-letter handling is explicitly out of scope for this ticket
 * (a teammate's SEC4-617/618 build that around this loop): on any unhandled
 * exception, this loop logs and moves on to the next record without
 * committing the failed one's offset, rather than crashing.
 */
public class OrderEventConsumer implements AutoCloseable {

    private static final Logger log = Logger.getLogger(OrderEventConsumer.class.getName());
    private static final TypeReference<EventEnvelope<OrderPlacedPayload>> ORDER_PLACED_ENVELOPE =
            new TypeReference<>() {
            };

    private final KafkaConsumer<String, String> consumer;
    private final ExecutionService executionService;
    private final ObjectMapper mapper;
    private volatile boolean running = true;

    public OrderEventConsumer(String bootstrapServers, ExecutionService executionService) {
        this(buildConsumer(bootstrapServers), executionService, new ObjectMapper());
    }

    OrderEventConsumer(KafkaConsumer<String, String> consumer, ExecutionService executionService, ObjectMapper mapper) {
        this.consumer = consumer;
        this.executionService = executionService;
        this.mapper = mapper;
    }

    private static KafkaConsumer<String, String> buildConsumer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, Topics.EXECUTOR_CONSUMER_GROUP);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(props);
    }

    /** Blocks, polling {@link Topics#ORDERS} until {@link #close()} is called. */
    public void run() {
        consumer.subscribe(List.of(Topics.ORDERS));
        try {
            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {
                    if (handle(record)) {
                        commit(record);
                    }
                }
            }
        } finally {
            consumer.close();
        }
    }

    /** @return true if the record was fully handled and its offset should be committed. */
    private boolean handle(ConsumerRecord<String, String> record) {
        try {
            EventEnvelope<OrderPlacedPayload> envelope = mapper.readValue(record.value(), ORDER_PLACED_ENVELOPE);
            executionService.execute(envelope.payload().orderId());
            return true;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Skipping record at offset " + record.offset()
                    + " on partition " + record.partition() + " without committing", e);
            return false;
        }
    }

    private void commit(ConsumerRecord<String, String> record) {
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        consumer.commitSync(Map.of(partition, new OffsetAndMetadata(record.offset() + 1)));
    }

    @Override
    public void close() {
        running = false;
    }

    /** The ORDER_PLACED event payload: just enough to look the order up. */
    public record OrderPlacedPayload(long orderId) {
    }
}
