package org.leap.executor.exec;

import java.util.ArrayList;
import java.util.List;

import org.leap.pricing.FillDecision;

/**
 * Trivial in-memory fake used by this module's own tests. The real
 * settlement service (order/cash/position/trade persistence) is a
 * teammate's SEC4-615, not built here.
 */
class FakeSettlementService implements SettlementService {

    final List<FillDecision> decisions = new ArrayList<>();

    @Override
    public SettlementOutcome settle(long orderId, FillDecision decision) {
        decisions.add(decision);
        return new SettlementOutcome(decision.fill(), decision.fill() ? "FILLED" : "REJECTED", decision.rejectionReason());
    }

    FillDecision lastDecision() {
        return decisions.get(decisions.size() - 1);
    }
}
