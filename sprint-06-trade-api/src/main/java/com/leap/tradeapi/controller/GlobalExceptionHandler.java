package com.leap.tradeapi.controller;

import org.leap.exceptions.AccountNotActiveException;
import org.leap.exceptions.AccountNotFoundException;
import org.leap.exceptions.DomainValidationException;
import org.leap.exceptions.DuplicateOrderException;
import org.leap.exceptions.InstrumentNotFoundException;
import org.leap.exceptions.InsufficientFundsException;
import org.leap.exceptions.InsufficientHoldingsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.leap.tradeapi.controller.dto.ErrorResponse;
import com.leap.tradeapi.error.AccountAccessDeniedException;
import com.leap.tradeapi.error.ConcurrentUpdateException;
import com.leap.tradeapi.error.OrderNotCancellableException;
import com.leap.tradeapi.error.OrderNotFoundException;

/**
 * The one place a failure becomes an error envelope. Every handler returns
 * {@code {"errorCode": "...", "message": "..."}} and nothing else. The message
 * is written for a human reading a screen: no class name, no SQL fragment, no
 * account key, no internal identifier. What an investigation needs is logged
 * here on the server.
 *
 * <p>The mapping is keyed by exception type, not by parsing a code string,
 * because {@code ORD-409} is returned at both 404 (order not found) and 409
 * (order not cancellable), as the contract's two DELETE examples fix.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- Account ----------------------------------------------------------

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException e) {
        log.warn("ACC-404: no account with key {}", e.getAccountId());
        return envelope(HttpStatus.NOT_FOUND, "ACC-404", "Account not found");
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotActive(AccountNotActiveException e) {
        log.warn("ACC-403: account {} is not active", e.getAccountId());
        return envelope(HttpStatus.FORBIDDEN, "ACC-403", "Account not active");
    }

    @ExceptionHandler(AccountAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccountAccessDeniedException e) {
        // Same code and message a suspended account gets, so keys cannot be enumerated.
        log.warn("ACC-403: token does not reach the addressed account");
        return envelope(HttpStatus.FORBIDDEN, "ACC-403", "Account not active");
    }

    // ---- Instrument ------------------------------------------------------

    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInstrumentNotFound(InstrumentNotFoundException e) {
        log.warn("INS-404: instrument {} unknown or not tradable", e.getSymbol());
        return envelope(HttpStatus.NOT_FOUND, "INS-404", "Instrument not found");
    }

    // ---- Order placement ------------------------------------------------

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException e) {
        log.warn("ORD-400: account {} needs {} but holds {}",
                e.getAccountId(), e.getRequiredAmount(), e.getAvailableAmount());
        return envelope(HttpStatus.BAD_REQUEST, "ORD-400", "Insufficient funds");
    }

    @ExceptionHandler(InsufficientHoldingsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientHoldings(InsufficientHoldingsException e) {
        log.warn("ORD-409: account {} instrument {} wants {} but holds {}",
                e.getAccountId(), e.getInstrumentId(), e.getRequestedQuantity(), e.getAvailableQuantity());
        return envelope(HttpStatus.CONFLICT, "ORD-409", "Insufficient holdings");
    }

    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateOrder(DuplicateOrderException e) {
        log.warn("ORD-409: idempotency key already used: {}", e.getIdempotencyKey());
        return envelope(HttpStatus.CONFLICT, "ORD-409", "Duplicate order");
    }

    @ExceptionHandler(ConcurrentUpdateException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentUpdate(ConcurrentUpdateException e) {
        log.warn("ORD-409: optimistic lock lost on the account row");
        return envelope(HttpStatus.CONFLICT, "ORD-409", "Order could not be completed, please retry");
    }

    // ---- Order cancellation -------------------------------------------

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException e) {
        log.warn("ORD-409 (404): cancel requested for an unknown order");
        return envelope(HttpStatus.NOT_FOUND, "ORD-409", "Order not found");
    }

    @ExceptionHandler(OrderNotCancellableException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotCancellable(OrderNotCancellableException e) {
        log.warn("ORD-409 (409): cancel requested for an order not in NEW state");
        return envelope(HttpStatus.CONFLICT, "ORD-409", "Order is not cancellable");
    }

    // ---- Validation ---------------------------------------------------

    @ExceptionHandler({
            DomainValidationException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleValidation(Exception e) {
        log.warn("VAL-422: request failed validation: {}", e.getMessage());
        return envelope(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");
    }

    // ---- Fallback ---------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return envelope(HttpStatus.INTERNAL_SERVER_ERROR, "ERR-500", "Internal server error");
    }

    private static ResponseEntity<ErrorResponse> envelope(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}