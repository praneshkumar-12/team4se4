package org.leap.exceptions;

public class AccountNotActiveException extends DomainException {

    private final Long accountId;

    public AccountNotActiveException(Long accountId) {
        super("ACC-403", "Account is not active");
        this.accountId = accountId;
    }

    public Long getAccountId() {
        return accountId;
    }
}
