package org.leap.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class Trade {

    private final Long tradeId;
    private final Long orderId;
    private final BigDecimal executedPrice;
    private final BigDecimal executedQuantity;
    private final Instant executedAt;
    private final BigDecimal fee;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Trade(
            Long tradeId,
            Long orderId,
            BigDecimal executedPrice,
            BigDecimal executedQuantity,
            Instant executedAt,
            BigDecimal fee,
            Instant createdAt,
            Instant updatedAt) {

        this.tradeId = Objects.requireNonNull(tradeId);
        this.orderId = Objects.requireNonNull(orderId);
        this.executedPrice = Objects.requireNonNull(executedPrice);
        this.executedQuantity = Objects.requireNonNull(executedQuantity);
        this.executedAt = Objects.requireNonNull(executedAt);
        this.fee = Objects.requireNonNull(fee);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;

        validate();
    }

    private void validate() {
        if (executedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Executed price must be greater than zero");
        }

        if (executedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Executed quantity must be greater than zero");
        }

        if (fee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Fee cannot be negative");
        }
    }

    public Long getTradeId() {
        return tradeId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getExecutedPrice() {
        return executedPrice;
    }

    public BigDecimal getExecutedQuantity() {
        return executedQuantity;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}