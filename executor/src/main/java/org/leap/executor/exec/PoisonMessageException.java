package org.leap.executor.exec;

/**
 * Signals a message that will never succeed no matter how many times it is
 * retried (malformed JSON, a missing/unknown order identifier, an
 * unexpected event type). Callers must dead-letter on the first occurrence,
 * never retry.
 */
public class PoisonMessageException extends RuntimeException {

    public PoisonMessageException(String message) {
        super(message);
    }

    public PoisonMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}