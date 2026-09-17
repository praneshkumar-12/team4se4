package org.leap.executor.exec;

import org.leap.pricing.FillDecision;

public interface SettlementService {
    SettlementOutcome settle(long orderId, FillDecision decision);
}
