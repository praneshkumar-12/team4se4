package org.leap.executor.exec;

/**
 * Thrown by SettlementService (SEC4-615) when its own optimistic-lock retry
 * budget on {@code accounts.version} is spent. Treated as transient at the
 * message level: the contention that caused it may have cleared by the next
 * delivery attempt.
 */
public class LockBudgetExhaustedException extends TransientProcessingException {

    public LockBudgetExhaustedException(String message) {
        super(message);
    }

    public LockBudgetExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
