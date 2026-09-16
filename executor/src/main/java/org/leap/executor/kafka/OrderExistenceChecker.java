package org.leap.executor.kafka;

public interface OrderExistenceChecker {
    boolean exists(long orderId) throws OrderLookupException;
}
