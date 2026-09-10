package org.leap.exceptions;

public class AccountNotFoundException extends DomainException {

    private final Long accountId;

    public AccountNotFoundException(Long accountId) {
        super("ACC-404", "Account not found");
        this.accountId = accountId;
    }

    public Long getAccountId() {
        return accountId;
    }
}