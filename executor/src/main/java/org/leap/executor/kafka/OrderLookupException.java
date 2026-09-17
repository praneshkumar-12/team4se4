package org.leap.executor.kafka;

/** Raised by {@link OrderExistenceChecker} when the lookup itself fails (e.g. lost DB connection). */
public class OrderLookupException extends Exception {

    public OrderLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
