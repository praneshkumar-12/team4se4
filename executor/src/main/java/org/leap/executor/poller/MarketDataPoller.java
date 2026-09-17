package org.leap.executor.poller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.leap.events.EventEnvelope;
import org.leap.events.Topics;
import org.leap.executor.fauxnance.FauxnanceClient;
import org.leap.pricing.Quote;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runs inside the same Trade Executor process as the order consumer (no
 * separate deployable). On each tick, chunks the watched symbol set into
 * groups of at most {@value #MAX_SYMBOLS_PER_REQUEST} for the Fauxnance
 * batch-quotes call (the quota-relevant HTTP request), then publishes one
 * Kafka message per symbol so {@code market-data}'s per-symbol key ordering
 * holds. See design/market-data-polling.md for the requests/day arithmetic
 * behind {@link #MIN_POLL_INTERVAL_SECONDS}.
 */
public class MarketDataPoller {

    public static final int MAX_SYMBOLS_PER_REQUEST = 25;
    public static final long MIN_POLL_INTERVAL_SECONDS = 120L;
    private static final long SECONDS_PER_DAY = 86_400L;
    private static final String EVENT_TYPE = "QUOTE_UPDATED";
    private static final String SOURCE = "market-poller";

    private final WatchedSymbolsRepository watchedSymbolsRepository;
    private final FauxnanceClient fauxnanceClient;
    private final Producer<String, byte[]> producer;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final long pollIntervalSeconds;

    public MarketDataPoller(WatchedSymbolsRepository watchedSymbolsRepository,
                             FauxnanceClient fauxnanceClient,
                             Producer<String, byte[]> producer,
                             ObjectMapper objectMapper,
                             ScheduledExecutorService scheduler,
                             long configuredPollIntervalSeconds) {
        this.watchedSymbolsRepository = watchedSymbolsRepository;
        this.fauxnanceClient = fauxnanceClient;
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.scheduler = scheduler;
        this.pollIntervalSeconds = enforceFloor(configuredPollIntervalSeconds);
    }

    /** The configured interval can never go below {@link #MIN_POLL_INTERVAL_SECONDS}. */
    public static long enforceFloor(long configuredSeconds) {
        return Math.max(configuredSeconds, MIN_POLL_INTERVAL_SECONDS);
    }

    /** How many Fauxnance requests/day a given watched-symbol count and interval would use. */
    public static long estimateDailyRequests(int symbolCount, long intervalSeconds) {
        long chunksPerTick = Math.max(1, (symbolCount + MAX_SYMBOLS_PER_REQUEST - 1) / MAX_SYMBOLS_PER_REQUEST);
        long ticksPerDay = SECONDS_PER_DAY / enforceFloor(intervalSeconds);
        return ticksPerDay * chunksPerTick;
    }

    public long pollIntervalSeconds() {
        return pollIntervalSeconds;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::pollOnce, 0, pollIntervalSeconds, TimeUnit.SECONDS);
    }

    void pollOnce() {
        Set<String> symbols = watchedSymbolsRepository.findWatchedSymbols();
        for (List<String> chunk : chunk(symbols, MAX_SYMBOLS_PER_REQUEST)) {
            Map<String, Quote> quotes = fauxnanceClient.getQuotes(chunk);
            for (Map.Entry<String, Quote> entry : quotes.entrySet()) {
                publish(entry.getKey(), entry.getValue());
            }
        }
    }

    private void publish(String symbol, Quote quote) {
        EventEnvelope<Quote> envelope = EventEnvelope.of(EVENT_TYPE, SOURCE, quote);
        byte[] value;
        try {
            value = objectMapper.writeValueAsBytes(envelope);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize quote for " + symbol, e);
        }
        producer.send(new ProducerRecord<>(Topics.MARKET_DATA, symbol, value));
    }

    static List<List<String>> chunk(Collection<String> items, int size) {
        List<List<String>> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>(size);
        for (String item : items) {
            current.add(item);
            if (current.size() == size) {
                chunks.add(current);
                current = new ArrayList<>(size);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }
}