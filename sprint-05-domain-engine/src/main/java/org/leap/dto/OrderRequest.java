package org.leap.dto;

import java.math.BigDecimal;
import java.util.Objects;

import org.leap.domain.enums.OrderSide;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class OrderRequest {

    @NotNull(message = "accountId is required")
    @Min(value = 1, message = "accountId must be at least 1")
    private final Long accountId;

    @NotBlank(message = "symbol is required")
    @Size(max = 20, message = "symbol must be at most 20 characters")
    private final String symbol;

    @NotNull(message = "side is required")
    private final OrderSide side;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be greater than zero")
    private final BigDecimal quantity;

    @NotNull(message = "price is required")
    @DecimalMin(
            value = "0.0",
            inclusive = false,
            message = "price must be greater than zero"
    )
    @Digits(
            integer = 17,
            fraction = 2,
            message = "price must have at most two decimal places"
    )
    private final BigDecimal price;

    @NotBlank(message = "idempotencyKey is required")
    @Size(
            min = 8,
            max = 100,
            message = "idempotencyKey must be between 8 and 100 characters"
    )
    private final String idempotencyKey;

    public OrderRequest(
            Long accountId,
            String symbol,
            OrderSide side,
            BigDecimal quantity,
            BigDecimal price,
            String idempotencyKey) {

        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof OrderRequest)) {
            return false;
        }

        OrderRequest that = (OrderRequest) o;

        return Objects.equals(accountId, that.accountId)
                && Objects.equals(symbol, that.symbol)
                && side == that.side
                && Objects.equals(quantity, that.quantity)
                && Objects.equals(price, that.price)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                accountId,
                symbol,
                side,
                quantity,
                price,
                idempotencyKey
        );
    }

    @Override
    public String toString() {
        return "OrderRequest{"
                + "accountId=" + accountId
                + ", symbol='" + symbol + '\''
                + ", side=" + side
                + ", quantity=" + quantity
                + ", price=" + price
                + ", idempotencyKey='" + idempotencyKey + '\''
                + '}';
    }
}