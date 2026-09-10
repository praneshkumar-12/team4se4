package org.leap.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import org.leap.domain.enums.TransactionType;

public class TransactionHistory {

    private final Long transactionId;
    private final Long accountId;
    private final BigDecimal amount;
    private final TransactionType transactionType;
    private final String currency;
    private final Instant createdAt;
    private final Instant updatedAt;

    public TransactionHistory(
            Long transactionId,
            Long accountId,
            BigDecimal amount,
            TransactionType transactionType,
            String currency,
            Instant createdAt,
            Instant updatedAt) {

        this.transactionId = Objects.requireNonNull(transactionId);
        this.accountId = Objects.requireNonNull(accountId);
        this.amount = Objects.requireNonNull(amount);
        this.transactionType = Objects.requireNonNull(transactionType);
        this.currency = Objects.requireNonNull(currency);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Transaction amount must be greater than zero");
        }
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}