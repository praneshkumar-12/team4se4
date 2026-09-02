package org.leap.domain;

import java.math.BigDecimal;
import java.util.Objects;

public class Position {

    private final Long holdingId;
    private final Long accountId;
    private final Long instrumentId;

    private BigDecimal quantity;
    private BigDecimal averageCost;

    public Position(
            Long holdingId,
            Long accountId,
            Long instrumentId,
            BigDecimal quantity,
            BigDecimal averageCost) {

        this.holdingId = Objects.requireNonNull(holdingId);
        this.accountId = Objects.requireNonNull(accountId);
        this.instrumentId = Objects.requireNonNull(instrumentId);
        this.quantity = Objects.requireNonNull(quantity);
        this.averageCost = Objects.requireNonNull(averageCost);

        validate();
    }

    private void validate() {
        if (quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Position quantity cannot be negative");
        }

        if (averageCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Average cost cannot be negative");
        }
    }

    public Long getHoldingId() {
        return holdingId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public void buy(BigDecimal buyQuantity, BigDecimal price) {

        validatePositive(buyQuantity, "Buy quantity");
        validatePositive(price, "Buy price");

        BigDecimal oldValue =
                quantity.multiply(averageCost);

        BigDecimal newValue =
                buyQuantity.multiply(price);

        BigDecimal newQuantity =
                quantity.add(buyQuantity);

        averageCost =
                oldValue.add(newValue)
                        .divide(
                                newQuantity,
                                8,
                                java.math.RoundingMode.HALF_UP);

        quantity = newQuantity;
    }

    public void sell(BigDecimal sellQuantity) {

        if (sellQuantity == null ||
                sellQuantity.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Sell quantity must be greater than zero");
        }

        if (sellQuantity.compareTo(quantity) > 0) {
            throw new InsufficientHoldingsException(
                    "Insufficient holdings");
        }

        quantity = quantity.subtract(sellQuantity);

        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            averageCost = BigDecimal.ZERO;
        }
    }

    private void validatePositive(
            BigDecimal value,
            String fieldName) {

        if (value == null ||
                value.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    fieldName + " must be greater than zero");
        }
    }
}