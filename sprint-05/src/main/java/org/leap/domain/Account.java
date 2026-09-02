package org.leap.domain;

import java.math.BigDecimal;
import java.util.Objects;

import org.leap.domain.enums.AccountStatus;

public class Account {

    private final Long accountId;
    private final String accountReference;
    private final String firstName;
    private final String lastName;
    private final String currency;
    private BigDecimal cashBalance;
    private AccountStatus status;
    private Long version;

    public Account(
            Long accountId,
            String accountReference,
            String firstName,
            String lastName,
            String currency,
            BigDecimal cashBalance,
            AccountStatus status,
            Long version) {

        this.accountId = Objects.requireNonNull(accountId);
        this.accountReference = Objects.requireNonNull(accountReference);
        this.firstName = Objects.requireNonNull(firstName);
        this.lastName = Objects.requireNonNull(lastName);
        this.currency = Objects.requireNonNull(currency);
        this.cashBalance = Objects.requireNonNull(cashBalance);
        this.status = Objects.requireNonNull(status);
        this.version = Objects.requireNonNull(version);

        if (cashBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cash balance cannot be negative");
        }

        if (version < 0) {
            throw new IllegalArgumentException("Version cannot be negative");
        }
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getAccountReference() {
        return accountReference;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean canTrade() {
        return status == AccountStatus.ACTIVE;
    }

    public void suspend() {
        if (status != AccountStatus.CLOSED) {
            status = AccountStatus.SUSPENDED;
        }
    }

    public void activate() {
        if (status == AccountStatus.SUSPENDED) {
            status = AccountStatus.ACTIVE;
        }
    }

    public void close() {
        status = AccountStatus.CLOSED;
    }

    public void credit(BigDecimal amount) {
        validatePositiveAmount(amount);

        cashBalance = cashBalance.add(amount);
    }

    public void debit(BigDecimal amount) {
        validatePositiveAmount(amount);

        if (!canAfford(amount)) {
            throw new InsufficientFundsException(
                    "Insufficient funds for debit");
        }

        cashBalance = cashBalance.subtract(amount);
    }

    public boolean canAfford(BigDecimal amount) {
        Objects.requireNonNull(amount);
        return cashBalance.compareTo(amount) >= 0;
    }

    public void setVersion(Long version) {
        if (version == null || version < 0) {
            throw new IllegalArgumentException("Version cannot be negative");
        }

        this.version = version;
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero");
        }
    }
}