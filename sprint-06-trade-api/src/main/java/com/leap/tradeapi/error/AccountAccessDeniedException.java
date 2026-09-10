package com.leap.tradeapi.error;

/**
 * The caller's token does not reach the account being addressed. Maps to
 * {@code ACC-403}, with the same message a suspended account gets, so that
 * account keys cannot be enumerated.
 */
public class AccountAccessDeniedException extends RuntimeException {

    public AccountAccessDeniedException() {
        super("Token does not reach the addressed account");
    }
}
