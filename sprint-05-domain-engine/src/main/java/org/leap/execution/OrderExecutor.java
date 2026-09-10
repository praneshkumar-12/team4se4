package org.leap.execution;

import org.leap.domain.Order;

public interface OrderExecutor {

    void execute(Order order);
}
