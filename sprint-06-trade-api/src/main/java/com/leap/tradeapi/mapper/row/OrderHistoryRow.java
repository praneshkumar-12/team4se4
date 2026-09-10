package com.leap.tradeapi.mapper.row;

import java.math.BigDecimal;
import java.time.Instant;

import org.leap.domain.enums.OrderSide;
import org.leap.domain.enums.OrderStatus;

/** Row backing {@code OrderHistoryEntry}. */
public record OrderHistoryRow(
        String publicId,
        Long accountId,
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal executedPrice,
        OrderStatus status,
        String idempotencyKey,
        Instant createdOn) {
}
