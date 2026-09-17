package com.leap.tradeapi.characterisation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderPlacementCharacterisationTest {

    @Autowired
    private MockMvc mvc;

    private String tokenFor(long accountId) {
        return TestTokens.bearer(TestTokens.validFor(accountId));
    }

    private String orderBody(long accountId, String symbol, String side, int qty, String price, String key) {
        return """
                {"accountId":%d,"symbol":"%s","side":"%s","quantity":%d,"price":%s,"idempotencyKey":"%s"}
                """.formatted(accountId, symbol, side, qty, price, key);
    }

    
    @Test
    void an_affordable_buy_is_accepted_at_NEW_without_a_synchronous_fill() throws Exception {
        mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(1L, "ACME", "BUY", 100, "25.50", "char-buy-key-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(org.hamcrest.Matchers.startsWith("ORD-")))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.message").value("Order accepted"))
                .andExpect(jsonPath("$.symbol").value("ACME"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(100))
                .andExpect(jsonPath("$.price").value(25.50));

        // Cash is untouched at acceptance (still the 25000.00 seed value): the
        // fill, and the cash movement that comes with it, is the executor's
        // job now (SEC4-614/615), not something that happens inside this request.
        mvc.perform(get("/api/v1/accounts/1/balance").header(HttpHeaders.AUTHORIZATION, tokenFor(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(25000.00));

        // Position is untouched at acceptance too: still the 40 ACME seed value.
        mvc.perform(get("/api/v1/accounts/1/positions").header(HttpHeaders.AUTHORIZATION, tokenFor(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("ACME"))
                .andExpect(jsonPath("$[0].quantity").value(40));

        // Order row only: status NEW, no trade row exists yet so executedPrice is null.
        mvc.perform(get("/api/v1/accounts/1/orders").header(HttpHeaders.AUTHORIZATION, tokenFor(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("ACME"))
                .andExpect(jsonPath("$[0].side").value("BUY"))
                .andExpect(jsonPath("$[0].quantity").value(100))
                .andExpect(jsonPath("$[0].price").value(25.50))
                .andExpect(jsonPath("$[0].executedPrice").doesNotExist())
                .andExpect(jsonPath("$[0].status").value("NEW"))
                .andExpect(jsonPath("$[0].idempotencyKey").value("char-buy-key-001"));
    }

    
    @Test
    void a_reused_idempotency_key_is_rejected_as_ORD_409() throws Exception {
        String body = orderBody(1L, "ACME", "BUY", 10, "25.50", "char-dup-key-001");

        mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, tokenFor(1L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, tokenFor(1L))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORD-409"))
                .andExpect(jsonPath("$.message").value("Duplicate order"));
    }

    
    @Test
    void an_unaffordable_buy_is_rejected_as_ORD_400() throws Exception {
        mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, tokenFor(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(1L, "ACME", "BUY", 100000, "25.50", "char-poor-key-001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORD-400"))
                .andExpect(jsonPath("$.message").value("Insufficient funds"));

        // Cash balance is untouched: the rejected order left no trace.
        mvc.perform(get("/api/v1/accounts/1/balance").header(HttpHeaders.AUTHORIZATION, tokenFor(1L)))
                .andExpect(jsonPath("$.cashBalance").value(25000.00));
    }

   
    @Test
    void an_unknown_symbol_is_rejected_as_INS_404() throws Exception {
        mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, tokenFor(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(1L, "NOSUCHSYMBOL", "BUY", 1, "25.50", "char-unknown-key-001")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INS-404"))
                .andExpect(jsonPath("$.message").value("Instrument not found"));
    }

    
    @Test
    void an_order_against_a_suspended_account_is_rejected_as_ACC_403() throws Exception {
        mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, tokenFor(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(2L, "ACME", "BUY", 1, "25.50", "char-suspended-key-001")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"))
                .andExpect(jsonPath("$.message").value("Account not active"));
    }
}
