package org.leap.exceptions;

import java.math.BigDecimal;

public class InsufficientFundsException extends DomainException {

    private final Long accountId;
    private final BigDecimal requiredAmount;
    private final BigDecimal availableAmount;

    public InsufficientFundsException(
            Long accountId,
            BigDecimal requiredAmount,
            BigDecimal availableAmount) {

        super("ORD-400", "Insufficient funds");

        this.accountId = accountId;
        this.requiredAmount = requiredAmount;
        this.availableAmount = availableAmount;
    }

    public Long getAccountId() {
        return accountId;
    }

    public BigDecimal getRequiredAmount() {
        return requiredAmount;
    }

    public BigDecimal getAvailableAmount() {
        return availableAmount;
    }
}