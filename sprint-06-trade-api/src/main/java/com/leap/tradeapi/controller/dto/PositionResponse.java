package com.leap.tradeapi.controller.dto;

import java.math.BigDecimal;

/** {@code PositionResponse} from {@code contracts/trade-api.yaml}. */
public record PositionResponse(
        Long accountId,
        String symbol,
        Integer quantity,
        BigDecimal averageCost) {
}