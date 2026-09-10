package com.leap.tradeapi.mapper.row;

import java.math.BigDecimal;
import java.time.Instant;

import org.leap.domain.enums.AccountStatus;

/** Row backing {@code AccountResponse}. */
public record AccountDetailsRow(
        Long id,
        String accountReference,
        String holderName,
        BigDecimal cashBalance,
        AccountStatus status,
        Long version,
        Instant lastUpdated) {
}
