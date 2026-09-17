package org.leap.executor.exec;

import org.junit.jupiter.api.Test;
import org.leap.executor.kafka.DeadLetterPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    private static class RecordingDeadLetterPublisher implements DeadLetterPublisher {
        int calls = 0;
        String lastReason;

        @Override
        public void sendToDlt(String originalTopic, String key, byte[] originalMessageValue, String failureReason) {
            calls++;
            lastReason = failureReason;
        }
    }

    private static class RecordingSleeper implements BackoffSleeper {
        final List<Long> sleptMillis = new ArrayList<>();

        @Override
        public void sleep(long millis) {
            sleptMillis.add(millis);
        }
    }

    @Test
    void transientFailureIsRetriedWithGrowingBackoffAndThenSucceeds() {
        RecordingDeadLetterPublisher dlt = new RecordingDeadLetterPublisher();
        RecordingSleeper sleeper = new RecordingSleeper();
        RetryPolicy policy = new RetryPolicy(5, 1000L, dlt, sleeper);

        AtomicInteger attempts = new AtomicInteger();
        policy.execute("orders", "acc-1", "payload".getBytes(), () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new TransientProcessingException("db connection lost");
            }
        });

        assertEquals(3, attempts.get());
        assertEquals(List.of(1000L, 2000L), sleeper.sleptMillis);
        assertEquals(0, dlt.calls);
    }

    @Test
    void poisonMessageIsNotRetriedAndIsDeadLetteredOnFirstAttempt() {
        RecordingDeadLetterPublisher dlt = new RecordingDeadLetterPublisher();
        RecordingSleeper sleeper = new RecordingSleeper();
        RetryPolicy policy = new RetryPolicy(5, 1000L, dlt, sleeper);

        AtomicInteger attempts = new AtomicInteger();
        byte[] payload = "payload".getBytes();
        org.junit.jupiter.api.Assertions.assertThrows(PoisonMessageException.class, () ->
                policy.execute("orders", "acc-1", payload, () -> {
                    attempts.incrementAndGet();
                    throw new PoisonMessageException("malformed JSON");
                }));

        assertEquals(1, attempts.get());
        assertEquals(0, sleeper.sleptMillis.size());
        assertEquals(0, dlt.calls);
    }

    @Test
    void retryBudgetExhaustedDeadLettersAfterBoundedAttempts() {
        RecordingDeadLetterPublisher dlt = new RecordingDeadLetterPublisher();
        RecordingSleeper sleeper = new RecordingSleeper();
        RetryPolicy policy = new RetryPolicy(3, 1000L, dlt, sleeper);

        AtomicInteger attempts = new AtomicInteger();
        policy.execute("orders", "acc-1", "payload".getBytes(), () -> {
            attempts.incrementAndGet();
            throw new TransientProcessingException("optimistic lock budget exhausted");
        });

        assertEquals(3, attempts.get());
        assertEquals(List.of(1000L, 2000L), sleeper.sleptMillis);
        assertEquals(1, dlt.calls);
        assertTrue(dlt.lastReason.contains("Retry budget exhausted"));
    }
}