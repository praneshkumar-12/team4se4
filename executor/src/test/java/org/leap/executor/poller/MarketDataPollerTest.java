package org.leap.executor.poller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leap.executor.fauxnance.FauxnanceClient;
import org.leap.pricing.Quote;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataPollerTest {

    @Mock
    private WatchedSymbolsRepository watchedSymbolsRepository;

    @Mock
    private FauxnanceClient fauxnanceClient;

    @Mock
    private ScheduledExecutorService scheduler;

    private MockProducer<String, byte[]> producer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        producer = new MockProducer<>(true, new StringSerializer(), (topic, data) -> data);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private MarketDataPoller poller(long configuredIntervalSeconds) {
        return new MarketDataPoller(watchedSymbolsRepository, fauxnanceClient, producer, objectMapper,
                scheduler, configuredIntervalSeconds);
    }

    private static Set<String> symbols(int count) {
        Set<String> symbols = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            symbols.add("SYM" + i);
        }
        return symbols;
    }

    @Test
    void batchesUpToTwentyFiveSymbolsPerFauxnanceRequest() {
        when(watchedSymbolsRepository.findWatchedSymbols()).thenReturn(symbols(30));
        when(fauxnanceClient.getQuotes(anyList())).thenReturn(Map.of());

        poller(120L).pollOnce();

        ArgumentCaptor<List<String>> chunkCaptor = ArgumentCaptor.forClass(List.class);
        verify(fauxnanceClient, times(2)).getQuotes(chunkCaptor.capture());
        List<List<String>> chunks = chunkCaptor.getAllValues();
        assertEquals(25, chunks.get(0).size());
        assertEquals(5, chunks.get(1).size());
    }

    @Test
    void publishesOneKeyedMessagePerSymbolNeverOnePerBatch() {
        when(watchedSymbolsRepository.findWatchedSymbols()).thenReturn(symbols(2));
        Quote quoteA = new Quote("SYM0", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(5));
        Quote quoteB = new Quote("SYM1", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(6));
        when(fauxnanceClient.getQuotes(anyList())).thenReturn(Map.of("SYM0", quoteA, "SYM1", quoteB));

        poller(120L).pollOnce();

        List<ProducerRecord<String, byte[]>> sent = producer.history();
        assertEquals(2, sent.size());
        assertEquals(Set.of("SYM0", "SYM1"), Set.of(sent.get(0).key(), sent.get(1).key()));
        for (ProducerRecord<String, byte[]> record : sent) {
            assertEquals("market-data", record.topic());
        }
    }

    @Test
    void configuredIntervalStaysInsideDailyQuotaForDeclaredSymbolSet() {
        // Declared symbol set for this deployment: up to 50 watched symbols (two batches/tick).
        long requestsPerDay = MarketDataPoller.estimateDailyRequests(50, 120L);
        assertEquals(1440L, requestsPerDay);
        assertTrue(requestsPerDay <= 2000L, "must stay within the 2000 requests/day Fauxnance quota");
    }

    @Test
    void intervalFloorIsEnforcedInCodeNotJustDocumented() {
        assertEquals(MarketDataPoller.MIN_POLL_INTERVAL_SECONDS, MarketDataPoller.enforceFloor(15L));
        assertEquals(MarketDataPoller.MIN_POLL_INTERVAL_SECONDS, MarketDataPoller.enforceFloor(0L));
        assertEquals(300L, MarketDataPoller.enforceFloor(300L));

        MarketDataPoller clamped = poller(15L);
        assertEquals(MarketDataPoller.MIN_POLL_INTERVAL_SECONDS, clamped.pollIntervalSeconds());
    }
}
