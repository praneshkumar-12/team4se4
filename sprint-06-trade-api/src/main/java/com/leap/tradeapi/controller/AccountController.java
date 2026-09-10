package com.leap.tradeapi.controller;

import java.time.Instant;
import java.util.List;

import org.leap.domain.enums.OrderStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.leap.tradeapi.controller.dto.AccountResponse;
import com.leap.tradeapi.controller.dto.BalanceResponse;
import com.leap.tradeapi.controller.dto.OrderHistoryEntry;
import com.leap.tradeapi.controller.dto.PositionResponse;
import com.leap.tradeapi.service.AccountService;

/**
 * The four account read endpoints. Path {@code id} is the numeric account key,
 * {@code ACCOUNTS.id}, everywhere in this controller.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(@PathVariable long id) {
        return accountService.getAccount(id);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse getBalance(@PathVariable long id) {
        return accountService.getBalance(id);
    }

    @GetMapping("/{id}/positions")
    public List<PositionResponse> getPositions(@PathVariable long id) {
        return accountService.getPositions(id);
    }

    @GetMapping("/{id}/orders")
    public List<OrderHistoryEntry> getOrders(
            @PathVariable long id,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return accountService.getOrders(id, status, from, to);
    }
}
