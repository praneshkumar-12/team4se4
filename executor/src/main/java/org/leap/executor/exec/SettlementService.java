package org.leap.executor.exec;

import org.leap.pricing.FillDecision;

/**
 * Applies a fill/reject decision durably (order status, cash, position,
 * trade row).
 */
public interface SettlementService {
    SettlementOutcome settle(long orderId, FillDecision decision);
}
