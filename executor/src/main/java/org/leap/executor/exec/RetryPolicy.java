package org.leap.executor.exec;

import org.leap.executor.kafka.DeadLetterPublisher;

/**
 * Runs a message-processing attempt, retrying {@link TransientProcessingException}
 * failures with exponentially growing backoff (1s, 2s, 4s, 8s, ...) up to a
 * bounded attempt count. Once the budget is spent, the message is
 * dead-lettered instead of retried forever. Any other exception (in
 * particular {@link PoisonMessageException}) propagates immediately,
 * without consuming a retry attempt or sleeping.
 */
public class RetryPolicy {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    public static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 1000L;
    private static final int BACKOFF_MULTIPLIER = 2;

    private final int maxAttempts;
    private final long initialBackoffMillis;
    private final DeadLetterPublisher deadLetterPublisher;
    private final BackoffSleeper sleeper;

    public RetryPolicy(DeadLetterPublisher deadLetterPublisher) {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_BACKOFF_MILLIS, deadLetterPublisher, Thread::sleep);
    }

    public RetryPolicy(int maxAttempts, long initialBackoffMillis, DeadLetterPublisher deadLetterPublisher, BackoffSleeper sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoffMillis = initialBackoffMillis;
        this.deadLetterPublisher = deadLetterPublisher;
        this.sleeper = sleeper;
    }

    public void execute(String topic, String key, byte[] value, Runnable attempt) {
        long backoffMillis = initialBackoffMillis;
        for (int attemptNumber = 1; attemptNumber <= maxAttempts; attemptNumber++) {
            try {
                attempt.run();
                return;
            } catch (TransientProcessingException e) {
                if (attemptNumber == maxAttempts) {
                    deadLetterPublisher.sendToDlt(topic, key, value,
                            "Retry budget exhausted after " + attemptNumber + " attempts: " + e.getMessage());
                    return;
                }
                sleepQuietly(backoffMillis);
                backoffMillis *= BACKOFF_MULTIPLIER;
            }
        }
    }

    private void sleepQuietly(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
