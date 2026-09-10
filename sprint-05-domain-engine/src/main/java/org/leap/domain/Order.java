package org.leap.domain;

import java.math.BigDecimal;
import java.util.Objects;

import org.leap.domain.enums.OrderSide;
import org.leap.domain.enums.OrderStatus;
import org.leap.domain.enums.OrderType;

public class Order {

    private final Long orderId;
    private final String idempotencyKey;
    private final Long accountId;
    private final Long instrumentId;
    private final OrderSide side;
    private final OrderType orderType;
    private final BigDecimal quantity;
    private final BigDecimal limitPrice;

    private OrderStatus status;

    public Order(
            Long orderId,
            String idempotencyKey,
            Long accountId,
            Long instrumentId,
            OrderSide side,
            OrderType orderType,
            BigDecimal quantity,
            BigDecimal limitPrice) {

        this.orderId = Objects.requireNonNull(orderId);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.accountId = Objects.requireNonNull(accountId);
        this.instrumentId = Objects.requireNonNull(instrumentId);
        this.side = Objects.requireNonNull(side);
        this.orderType = Objects.requireNonNull(orderType);
        this.quantity = Objects.requireNonNull(quantity);
        this.limitPrice = limitPrice;
        this.status = OrderStatus.NEW;

        validate();
    }

    private void validate() {
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Order quantity must be greater than zero");
        }

        if (orderType == OrderType.MARKET && limitPrice != null) {
            throw new IllegalArgumentException(
                    "Market order cannot have a limit price");
        }

        if (orderType == OrderType.LIMIT) {
            if (limitPrice == null ||
                    limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Limit order must have a positive limit price");
            }
        }
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void fill() {
        requireNew();
        status = OrderStatus.FILLED;
    }

    public void reject() {
        requireNew();
        status = OrderStatus.REJECTED;
    }

    public void cancel() {
        requireNew();
        status = OrderStatus.CANCELLED;
    }

    private void requireNew() {
        if (status != OrderStatus.NEW) {
            throw new IllegalStateException(
                    "Order cannot transition from " + status);
        }
    }
}