package com.leap.tradeapi.controller.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code BalanceResponse} from {@code contracts/trade-api.yaml}. */
public record BalanceResponse(
        Long accountId,
        BigDecimal cashBalance,
        String currency,
        Instant asOf) {
}