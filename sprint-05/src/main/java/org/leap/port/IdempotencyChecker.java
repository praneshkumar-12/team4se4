package org.leap.port;

public interface IdempotencyChecker {

    boolean hasBeenProcessed(String idempotencyKey);

    void markAsProcessed(String idempotencyKey);
}
