package com.leap.tradeapi.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.leap.tradeapi.support.TestTokens;

/**
 * Exercises all six operations through the real controller, service, MyBatis
 * persistence and JWT filter, against H2 standing in for Postgres. No container.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TradeApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private String tokenFor(long accountId) {
        return TestTokens.bearer(TestTokens.validFor(accountId));
    }

    private String orderBody(String symbol, String side, int qty, String price, String key) {
        return """
                {"accountId":1,"symbol":"%s","side":"%s","quantity":%d,"price":%s,"idempotencyKey":"%s"}
                """.formatted(symbol, side, qty, price, key);
    }

    // ---- Authentication -------------------------------------------------

    @Test
    void a_protected_route_without_a_token_is_AUTH_401() throws Exception {
        mvc.perform(get("/api/v1/accounts/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
                .andExpect(jsonPath("$.message").value("Unauthorised"));
    }

    @Test
    void an_expired_token_is_AUTH_401() throws Exception {
        mvc.perform(get("/api/v1/accounts/1")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.expiredFor(1L))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"));
    }

    @Test
    void a_token_that_does_not_reach_the_account_is_ACC_403() throws Exception {
        mvc.perform(get("/api/v1/accounts/1").header(HttpHeaders.AUTHORIZATION, tokenFor(2L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"));
    }

    // ---- Account reads ------------------------------------------------

    @Test
    void account_details_are_shaped_by_the_contract() throws Exception {
        mvc.perform(get("/api/v1/accounts/1").header(HttpHeaders.AUTHORIZATION, tokenFor(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.accountId").value("ACC-000001"))
                .andExpect(jsonPath("$.holderName").value("Priya Menon"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void an_unknown_account_is_ACC_404() throws Exception {
        mvc.perform(get("/api/v1/accounts/4242").header(HttpHeaders.AUTHORIZATION, tokenFor(4242L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACC-404"));
    }

    @Test
    void balance_and_positions_are_returned() throws Exception {
        mvc.perform(get("/api/v1/accounts/1/balance").header(HttpHeaders.AUTHORIZATION, tokenFor(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.cashBalance").value(25000.00));

        mvc.perform(get("/api/v1/accounts/1/positions").header(HttpHeaders.AUTHORIZATION, tokenFor(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol").value("ACME"))
                .andExpect(jsonPath("$[0].quantity").value(40));
    }

    // ---- Order placement --------------------------------------------

    @Test
    void placing_a_buy_accepts_it_at_NEW_without_moving_cash_or_position() throws Exception {
        mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("ACME", "BUY", 100, "25.50", "buy-key-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.message").value("Order accepted"))
                .andExpect(jsonPath("$.orderId").value(org.hamcrest.Matchers.startsWith("ORD-")));

        // Acceptance no longer moves cash or the position: pricing and
        // settlement happen later, in the Trade Executor (a teammate's ticket).
        mvc.perform(get("/api/v1/accounts/1/balance").header(HttpHeaders.AUTHORIZATION, tokenFor(1L)))
                .andExpect(jsonPath("$.cashBalance").value(25000.00));

        mvc.perform(get("/api/v1/accounts/1/positions").header(HttpHeaders.AUTHORIZATION, tokenFor(1L)))
                .andExpect(jsonPath("$[0].quantity").value(40));

        mvc.perform(get("/api/v1/accounts/1/orders").header(HttpHeaders.AUTHORIZATION, tokenFor(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("NEW"))
                .andExpect(jsonPath("$[0].symbol").value("ACME"));
    }

    @Test
    void a_reused_idempotency_key_is_ORD_409() throws Exception {
        String body = orderBody("ACME", "BUY", 10, "25.50", "dup-key-001");
        mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, tokenFor(1L))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, tokenFor(1L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORD-409"));
    }

    @Test
    void a_buy_beyond_the_cash_balance_is_ORD_400() throws Exception {
        mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, tokenFor(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("ACME", "BUY", 100000, "25.50", "poor-key-001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORD-400"));
    }

    @Test
    void a_sell_beyond_the_held_quantity_is_ORD_409() throws Exception {
        mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, tokenFor(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("ACME", "SELL", 999, "25.50", "sell-key-001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORD-409"));
    }

    @Test
    void an_unknown_instrument_is_INS_404() throws Exception {
        mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, tokenFor(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("NOPE", "BUY", 1, "25.50", "nope-key-001")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INS-404"));
    }

    @Test
    void a_delisted_instrument_is_INS_404() throws Exception {
        mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, tokenFor(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("DELISTED", "BUY", 1, "25.50", "delisted-key-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INS-404"));
    }

    @Test
    void an_invalid_field_is_VAL_422() throws Exception {
        mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, tokenFor(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("ACME", "BUY", 0, "25.50", "zeroqty-key-1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    // ---- Order cancellation ---------------------------------------

    @Test
    void cancelling_an_unknown_order_is_ORD_409_at_404() throws Exception {
        mvc.perform(delete("/api/v1/orders/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORD-409"))
                .andExpect(jsonPath("$.message").value("Order not found"));
    }
}
