package com.leap.tradeapi.service;

import java.time.Instant;
import java.util.List;

import org.leap.domain.enums.OrderStatus;
import org.leap.exceptions.AccountNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leap.tradeapi.controller.dto.AccountResponse;
import com.leap.tradeapi.controller.dto.BalanceResponse;
import com.leap.tradeapi.controller.dto.OrderHistoryEntry;
import com.leap.tradeapi.controller.dto.PositionResponse;
import com.leap.tradeapi.mapper.AccountMapper;
import com.leap.tradeapi.mapper.OrderMapper;
import com.leap.tradeapi.mapper.PositionMapper;
import com.leap.tradeapi.mapper.row.AccountDetailsRow;
import com.leap.tradeapi.mapper.row.BalanceRow;

/**
 * The account read side: details, cash balance, positions and order history,
 * each shaped through the contract DTOs.
 *
 * <p>{@code ACC-403} for an account the token does not reach is decided before
 * {@code ACC-404}, so a caller cannot probe which account keys exist.
 */
@Service
public class AccountService {

    private final AccountMapper accountMapper;
    private final PositionMapper positionMapper;
    private final OrderMapper orderMapper;
    private final AccountAccessGuard guard;

    public AccountService(AccountMapper accountMapper, PositionMapper positionMapper,
                          OrderMapper orderMapper, AccountAccessGuard guard) {
        this.accountMapper = accountMapper;
        this.positionMapper = positionMapper;
        this.orderMapper = orderMapper;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(long accountId) {
        guard.requireReaches(accountId);
        AccountDetailsRow row = accountMapper.findDetails(accountId);
        if (row == null) {
            throw new AccountNotFoundException(accountId);
        }
        return new AccountResponse(row.id(), row.accountReference(), row.holderName(),
                row.cashBalance(), row.status(), row.version().intValue(), row.lastUpdated());
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(long accountId) {
        guard.requireReaches(accountId);
        BalanceRow row = accountMapper.findBalance(accountId);
        if (row == null) {
            throw new AccountNotFoundException(accountId);
        }
        return new BalanceResponse(row.accountId(), row.cashBalance(), row.currency(), Instant.now());
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(long accountId) {
        guard.requireReaches(accountId);
        requireAccount(accountId);
        return positionMapper.findByAccount(accountId).stream()
                .map(p -> new PositionResponse(
                        p.accountId(), p.symbol(), p.quantity().intValueExact(), p.averageCost()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderHistoryEntry> getOrders(long accountId, OrderStatus status, Instant from, Instant to) {
        guard.requireReaches(accountId);
        requireAccount(accountId);
        String statusFilter = status == null ? null : status.name();
        return orderMapper.findHistory(accountId, statusFilter, from, to).stream()
                .map(r -> new OrderHistoryEntry(
                        "ORD-" + r.publicId(), r.accountId(), r.symbol(), r.side(),
                        r.quantity().intValueExact(), r.limitPrice(), r.executedPrice(),
                        r.status(), r.idempotencyKey(), r.createdOn()))
                .toList();
    }

    private void requireAccount(long accountId) {
        if (!accountMapper.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
    }
}
