package com.leap.tradeapi.error;

/**
 * The order exists but is not in a cancellable state (it is already
 * {@code FILLED}, {@code REJECTED} or {@code CANCELLED}). The contract fixes
 * this as {@code ORD-409} at HTTP 409.
 */
public class OrderNotCancellableException extends RuntimeException {

    public OrderNotCancellableException() {
        super("Order is not in a cancellable state");
    }
}