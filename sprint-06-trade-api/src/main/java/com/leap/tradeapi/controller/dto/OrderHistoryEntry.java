package com.leap.tradeapi.controller.dto;

import java.math.BigDecimal;
import java.time.Instant;

import org.leap.domain.enums.OrderSide;
import org.leap.domain.enums.OrderStatus;

/** {@code OrderHistoryEntry} from {@code contracts/trade-api.yaml}. */
public record OrderHistoryEntry(
        String orderId,
        Long accountId,
        String symbol,
        OrderSide side,
        Integer quantity,
        BigDecimal price,
        BigDecimal executedPrice,
        OrderStatus status,
        String idempotencyKey,
        Instant createdOn) {
}