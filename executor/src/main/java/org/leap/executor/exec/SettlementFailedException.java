package org.leap.executor.exec;

/**
 * Thrown when settlement cannot be completed - the transaction has already
 * been rolled back by the time this propagates. The caller must not
 * acknowledge the Kafka offset when this is thrown, so at-least-once
 * redelivery gets another attempt.
 */
public class SettlementFailedException extends RuntimeException {

    public SettlementFailedException(String message) {
        super(message);
    }

    public SettlementFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
