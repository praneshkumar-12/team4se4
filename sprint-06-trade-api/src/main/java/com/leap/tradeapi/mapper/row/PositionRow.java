package com.leap.tradeapi.mapper.row;

import java.math.BigDecimal;

/** Row backing {@code PositionResponse}. */
public record PositionRow(
        Long accountId,
        String symbol,
        BigDecimal quantity,
        BigDecimal averageCost) {
}
