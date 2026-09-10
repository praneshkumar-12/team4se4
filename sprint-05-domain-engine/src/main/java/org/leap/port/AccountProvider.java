package org.leap.port;

import org.leap.domain.Account;

import java.util.Optional;

public interface AccountProvider {

    Optional<Account> findById(String accountId);
}
