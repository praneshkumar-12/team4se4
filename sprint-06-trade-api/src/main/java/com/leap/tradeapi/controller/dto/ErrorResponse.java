package com.leap.tradeapi.controller.dto;

/**
 * The single error envelope for every failure, from
 * {@code contracts/trade-api.yaml}. Clients branch on {@code errorCode}.
 */
public record ErrorResponse(String errorCode, String message) {
}