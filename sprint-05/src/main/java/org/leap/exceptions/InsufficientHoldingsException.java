package org.leap.exceptions;

import java.math.BigDecimal;

public class InsufficientHoldingsException extends DomainException {

    private final Long accountId;
    private final Long instrumentId;
    private final BigDecimal requestedQuantity;
    private final BigDecimal availableQuantity;

    public InsufficientHoldingsException(
            Long accountId,
            Long instrumentId,
            BigDecimal requestedQuantity,
            BigDecimal availableQuantity) {

        super("ORD-409", "Insufficient holdings");

        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public BigDecimal getRequestedQuantity() {
        return requestedQuantity;
    }

    public BigDecimal getAvailableQuantity() {
        return availableQuantity;
    }
}
