package com.leap.tradeapi.service;

import org.springframework.stereotype.Component;

import com.leap.tradeapi.auth.CallerContext;
import com.leap.tradeapi.error.AccountAccessDeniedException;

/**
 * Authorisation, decided where the account key is known rather than in the
 * filter. A valid token proves who the caller is; it does not say which account
 * they may reach.
 */
@Component
public class AccountAccessGuard {

    private final CallerContext caller;

    public AccountAccessGuard(CallerContext caller) {
        this.caller = caller;
    }

    public void requireReaches(long accountId) {
        if (!caller.reaches(accountId)) {
            throw new AccountAccessDeniedException();
        }
    }
}
