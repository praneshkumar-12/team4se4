package org.leap.executor.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leap.executor.exec.RetryPolicy;
import org.leap.executor.exec.TransientProcessingException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadLetterClassificationTest {

    @Mock
    private OrderExistenceChecker orderExistenceChecker;

    @Mock
    private OrderProcessor orderProcessor;

    @Mock
    private DeadLetterPublisher deadLetterPublisher;

    private ObjectMapper objectMapper;
    private ResilientRecordProcessor processor;

    private static final byte[] MALFORMED = "{not-json".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        // No sleeping in tests: RetryPolicy only sleeps between TransientProcessingException
        // retries, and these tests use small max attempts with a no-op sleeper.
        RetryPolicy retryPolicy = new RetryPolicy(3, 1L, deadLetterPublisher, millis -> { });
        processor = new ResilientRecordProcessor(objectMapper, orderExistenceChecker, orderProcessor,
                retryPolicy, deadLetterPublisher, Set.of("ORDER_PLACED"));
    }

    private byte[] validMessage(long orderId) {
        return ("{\"eventId\":\"e1\",\"eventType\":\"ORDER_PLACED\",\"eventTime\":\"2026-09-16T00:00:00Z\","
                + "\"source\":\"trade-api\",\"schemaVersion\":1,\"payload\":{\"orderId\":" + orderId + "}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void malformedMessageIsDeadLetteredOnFirstAttempt() {
        processor.process("orders", "acc-1", MALFORMED);

        verify(deadLetterPublisher, times(1)).sendToDlt(eq("orders"), eq("acc-1"), eq(MALFORMED), any());
        verify(orderProcessor, never()).process(any());
    }

    @Test
    void unknownOrderIdIsDeadLetteredOnFirstAttempt() throws OrderLookupException {
        byte[] message = validMessage(999L);
        when(orderExistenceChecker.exists(999L)).thenReturn(false);

        processor.process("orders", "acc-1", message);

        verify(deadLetterPublisher, times(1)).sendToDlt(eq("orders"), eq("acc-1"), eq(message), any());
        verify(orderProcessor, never()).process(any());
    }

    @Test
    void transientLookupFailureIsRetriedThenSucceeds() throws OrderLookupException {
        byte[] message = validMessage(42L);
        AtomicInteger calls = new AtomicInteger();
        when(orderExistenceChecker.exists(42L)).thenAnswer(invocation -> {
            if (calls.incrementAndGet() < 2) {
                throw new OrderLookupException("connection lost", null);
            }
            return true;
        });

        processor.process("orders", "acc-1", message);

        assertEquals(2, calls.get());
        verify(orderProcessor, times(1)).process(any());
        verify(deadLetterPublisher, never()).sendToDlt(any(), any(), any(), any());
    }

    @Test
    void lockBudgetExhaustionIsRetriedThenDeadLetteredOnceBudgetIsSpent() throws OrderLookupException {
        byte[] message = validMessage(7L);
        when(orderExistenceChecker.exists(7L)).thenReturn(true);
        org.mockito.Mockito.doThrow(new TransientProcessingException("optimistic lock budget exhausted"))
                .when(orderProcessor).process(any());

        processor.process("orders", "acc-1", message);

        verify(orderProcessor, times(3)).process(any());
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(deadLetterPublisher, times(1)).sendToDlt(eq("orders"), eq("acc-1"), eq(message), reasonCaptor.capture());
        assertTrue(reasonCaptor.getValue().contains("Retry budget exhausted"));
    }

    @Test
    void poisonMessageDoesNotBlockThePartitionSubsequentMessagesStillProcess() throws OrderLookupException {
        byte[] goodMessage = validMessage(5L);
        when(orderExistenceChecker.exists(5L)).thenReturn(true);

        processor.process("orders", "acc-1", MALFORMED);
        processor.process("orders", "acc-1", goodMessage);

        verify(deadLetterPublisher, times(1)).sendToDlt(eq("orders"), eq("acc-1"), eq(MALFORMED), any());
        verify(orderProcessor, times(1)).process(any());
    }
}