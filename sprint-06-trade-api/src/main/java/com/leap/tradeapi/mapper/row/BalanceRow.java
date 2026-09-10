package com.leap.tradeapi.mapper.row;

import java.math.BigDecimal;

/** Row backing {@code BalanceResponse}; {@code asOf} is stamped by the service. */
public record BalanceRow(
        Long accountId,
        BigDecimal cashBalance,
        String currency) {
}
