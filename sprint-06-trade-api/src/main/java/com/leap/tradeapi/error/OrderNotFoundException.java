package com.leap.tradeapi.error;

/**
 * No order exists with the given identifier. The contract fixes this as
 * {@code ORD-409} at HTTP 404 (see the DELETE examples), so it is a distinct
 * type from {@link OrderNotCancellableException}.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException() {
        super("Order not found");
    }
}