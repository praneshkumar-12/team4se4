package com.leap.tradeapi.controller.dto;

import java.math.BigDecimal;

import org.leap.domain.enums.OrderSide;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code PlaceOrderRequest} from {@code contracts/trade-api.yaml}.
 *
 * <p>Field validation here produces {@code VAL-422}; the business rules 1 to 8
 * are enforced by the Sprint 5 domain.
 */
public record PlaceOrderRequest(

        @NotNull(message = "accountId is required")
        @Min(value = 1, message = "accountId must be at least 1")
        Long accountId,

        @NotBlank(message = "symbol is required")
        @Size(min = 1, max = 20, message = "symbol must be between 1 and 20 characters")
        String symbol,

        @NotNull(message = "side is required")
        OrderSide side,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be greater than zero")
        Integer quantity,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "price must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "price must have at most two decimal places")
        BigDecimal price,

        @NotBlank(message = "idempotencyKey is required")
        @Size(min = 8, max = 100, message = "idempotencyKey must be between 8 and 100 characters")
        String idempotencyKey) {
}