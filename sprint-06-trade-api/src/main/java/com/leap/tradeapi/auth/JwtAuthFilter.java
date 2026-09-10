package com.leap.tradeapi.auth;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leap.tradeapi.controller.dto.ErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers "is this a valid token" once, for every route under {@code /api/v1/},
 * before any controller runs.
 *
 * <p>A missing header, a wrong scheme, an expired token and a forged signature
 * are one answer: {@code AUTH-401} with an identical body, so an attacker cannot
 * tell which of the four they got. Whether the caller may reach a given account
 * is a separate question, answered in the service where the account key is known.
 */
@Component
@Order(1)
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String API_PREFIX = "/api/v1/";
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final CallerContext callerContext;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, CallerContext callerContext, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.callerContext = callerContext;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            log.warn("Rejected {} {}: missing or non-bearer Authorization header",
                    request.getMethod(), request.getRequestURI());
            writeUnauthorised(response);
            return;
        }

        try {
            JwtService.VerifiedToken token = jwtService.verify(header.substring(BEARER.length()));
            callerContext.setAccountId(token.accountId());
            callerContext.setRoles(token.roles());
        } catch (RuntimeException e) {
            log.warn("Rejected {} {}: token verification failed ({})",
                    request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName());
            writeUnauthorised(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeUnauthorised(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse("AUTH-401", "Unauthorised"));
    }
}