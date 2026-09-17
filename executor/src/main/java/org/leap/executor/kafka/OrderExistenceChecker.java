package org.leap.executor.kafka;

public interface OrderExistenceChecker {
    /** {@code orderId} is the order's public UUID (orders.public_id), not the internal numeric id. */
    boolean exists(String orderId) throws OrderLookupException;
}
