package com.leap.tradeapi.controller.dto;

import java.math.BigDecimal;
import java.time.Instant;

import org.leap.domain.enums.AccountStatus;

/**
 * {@code AccountResponse} from {@code contracts/trade-api.yaml}.
 *
 * <p>{@code accountId} here is the string business reference
 * ({@code ACCOUNTS.account_reference}), not the numeric key. This is the one
 * place in the platform where the name carries that meaning.
 */
public record AccountResponse(
        Long id,
        String accountId,
        String holderName,
        BigDecimal cashBalance,
        AccountStatus status,
        Integer version,
        Instant lastUpdated) {
}