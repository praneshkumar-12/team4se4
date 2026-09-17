package org.leap.executor.exec;

public record SettlementOutcome(boolean applied, String status, String reason) {}
