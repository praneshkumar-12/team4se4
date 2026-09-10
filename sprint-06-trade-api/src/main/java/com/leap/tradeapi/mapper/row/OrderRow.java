package com.leap.tradeapi.mapper.row;

import java.math.BigDecimal;

import org.leap.domain.enums.OrderSide;
import org.leap.domain.enums.OrderStatus;

/** Identifying row for an existing order, used by the cancel path. */
public record OrderRow(
        Long orderId,
        String publicId,
        Long accountId,
        String symbol,
        OrderSide side,
        OrderStatus status,
        BigDecimal quantity,
        BigDecimal limitPrice) {
}
