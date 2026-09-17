package org.leap.executor.exec;

/**
 * Signals a failure that may succeed on a later attempt (a briefly
 * unreachable broker, a lost database connection, an exhausted
 * optimistic-lock budget). Callers should retry with backoff and
 * dead-letter only once the retry budget is spent.
 */
public class TransientProcessingException extends RuntimeException {

    public TransientProcessingException(String message) {
        super(message);
    }

    public TransientProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
