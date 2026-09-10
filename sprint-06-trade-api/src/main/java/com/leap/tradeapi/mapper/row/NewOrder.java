package com.leap.tradeapi.mapper.row;

import java.math.BigDecimal;

/**
 * Parameter object for inserting an order row. {@code orderId} is left null and
 * populated by MyBatis from the generated key, so the caller can then write the
 * matching trade row.
 */
public class NewOrder {

    private final String publicId;
    private final String idempotencyKey;
    private final long accountId;
    private final long instrumentId;
    private final String side;
    private final String orderType;
    private final BigDecimal quantity;
    private final BigDecimal limitPrice;
    private final String status;

    private Long orderId;

    public NewOrder(String publicId, String idempotencyKey, long accountId, long instrumentId,
                    String side, String orderType, BigDecimal quantity, BigDecimal limitPrice, String status) {
        this.publicId = publicId;
        this.idempotencyKey = idempotencyKey;
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.side = side;
        this.orderType = orderType;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.status = status;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public long getAccountId() {
        return accountId;
    }

    public long getInstrumentId() {
        return instrumentId;
    }

    public String getSide() {
        return side;
    }

    public String getOrderType() {
        return orderType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public String getStatus() {
        return status;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
}
