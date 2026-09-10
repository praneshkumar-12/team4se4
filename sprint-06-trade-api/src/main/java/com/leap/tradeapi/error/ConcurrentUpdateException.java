package com.leap.tradeapi.error;

/**
 * An optimistic-locked update to the account row affected zero rows: another
 * writer moved the version between our read and our write. Refused with
 * {@code ORD-409} rather than treated as success.
 */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException() {
        super("Optimistic lock: account row changed under the transaction");
    }
}