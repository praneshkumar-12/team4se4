package org.leap.executor.fauxnance;

/**
 * A Fauxnance call could not get a price, after retries: an outage, a
 * malformed response, or a spent daily budget. The caller ({@code
 * ExecutionService}) treats this as a business outcome — {@code
 * NO_PRICE_AVAILABLE} — not something it retries itself.
 */
public class FauxnanceException extends RuntimeException {
    public FauxnanceException(String message) {
        super(message);
    }

    public FauxnanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
