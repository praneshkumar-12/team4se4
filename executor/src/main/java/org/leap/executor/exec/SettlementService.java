package org.leap.executor.exec;

import org.leap.pricing.FillDecision;

/**
 * Applies a fill/reject decision durably (order status, cash, position,
 * trade row). Implemented by a teammate's SEC4-615, not built here.
 */
public interface SettlementService {
    SettlementOutcome settle(long orderId, FillDecision decision);
}
