package com.leap.tradeapi.controller.dto;

import java.math.BigDecimal;

import org.leap.domain.enums.OrderSide;
import org.leap.domain.enums.OrderStatus;

/** {@code OrderResponse} from {@code contracts/trade-api.yaml}. */
public record OrderResponse(
        String orderId,
        OrderStatus status,
        String message,
        String symbol,
        OrderSide side,
        Integer quantity,
        BigDecimal price) {
}