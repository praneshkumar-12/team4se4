package org.leap.executor.exec;

/** applied=false + status="DUPLICATE" means guard 1 found the order already terminal (not NEW). */
public record SettlementOutcome(boolean applied, String status, String reason) {}
