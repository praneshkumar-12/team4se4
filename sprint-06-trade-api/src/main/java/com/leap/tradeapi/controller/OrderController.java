package com.leap.tradeapi.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.leap.tradeapi.controller.dto.OrderResponse;
import com.leap.tradeapi.controller.dto.PlaceOrderRequest;
import com.leap.tradeapi.service.OrderService;

import jakarta.validation.Valid;

/**
 * {@code POST /api/v1/orders} and {@code DELETE /api/v1/orders/{id}}.
 * HTTP and DTOs only: no SQL, no transaction, no business rule.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return orderService.placeOrder(request);
    }

    @DeleteMapping("/{id}")
    public OrderResponse cancelOrder(@PathVariable UUID id) {
        return orderService.cancelOrder(id);
    }
}
