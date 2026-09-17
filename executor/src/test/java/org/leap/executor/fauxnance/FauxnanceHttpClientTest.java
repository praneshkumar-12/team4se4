package org.leap.executor.fauxnance;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.leap.pricing.Quote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Parsing/mapping against real response shapes from the repo's
 * {@code openapi.yaml} (Fauxnance API): every response is a {@code
 * {data, meta}} envelope, narrowed here to the fixed {@code Quote(symbol,
 * bid, ask, price)} shape {@code FillRule} depends on.
 */
class FauxnanceHttpClientTest {

    private HttpClient httpClient;
    private FauxnanceHttpClient client;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        client = new FauxnanceHttpClient(httpClient, mapper, "https://fauxnance.example/v1", "fnx_test_key");
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int statusCode, String body) throws IOException, InterruptedException {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @Test
    void a_single_quote_unwraps_the_data_envelope() throws Exception {
        stubResponse(200, """
                {
                  "data": {
                    "symbol": "AAPL",
                    "price": 313.53,
                    "bid": 313.50,
                    "ask": 313.56,
                    "spreadBps": 1.8587,
                    "currency": "USD",
                    "change": 3.63,
                    "changePercent": 1.1713,
                    "previousClose": 309.90,
                    "asOf": "2026-08-26T16:13:24Z",
                    "marketState": "open"
                  },
                  "meta": {
                    "asOf": "2026-08-26T16:13:24Z",
                    "disclaimer": "Educational data. Not for investment use.",
                    "symbol": "AAPL",
                    "source": "cache",
                    "stale": false
                  }
                }
                """);

        Quote quote = client.getQuote("AAPL");

        assertEquals("AAPL", quote.symbol());
        assertEquals(0, new BigDecimal("313.53").compareTo(quote.price()));
        assertEquals(0, new BigDecimal("313.50").compareTo(quote.bid()));
        assertEquals(0, new BigDecimal("313.56").compareTo(quote.ask()));
    }

    @Test
    void a_batch_response_skips_per_symbol_errors_and_keeps_successes() throws Exception {
        stubResponse(200, """
                {
                  "data": {
                    "quotes": [
                      {
                        "symbol": "AAPL",
                        "source": "cache",
                        "stale": false,
                        "quote": {"symbol": "AAPL", "price": 313.53, "bid": 313.50, "ask": 313.56}
                      },
                      {
                        "symbol": "BAD",
                        "error": {"code": "SYMBOL_NOT_FOUND", "message": "Symbol was not recognized.", "details": {}}
                      }
                    ]
                  },
                  "meta": {
                    "asOf": "2026-08-26T16:13:24Z",
                    "disclaimer": "Educational data. Not for investment use.",
                    "spreadSource": "modelled"
                  }
                }
                """);

        Map<String, Quote> quotes = client.getQuotes(List.of("AAPL", "BAD"));

        assertEquals(1, quotes.size());
        assertTrue(quotes.containsKey("AAPL"));
        assertEquals(0, new BigDecimal("313.56").compareTo(quotes.get("AAPL").ask()));
    }

    @Test
    void the_remaining_daily_budget_is_quota_minus_used() throws Exception {
        stubResponse(200, """
                {
                  "data": {
                    "keyLabel": "student-1",
                    "cohort": "cohort-1",
                    "dailyQuota": 500,
                    "usedToday": 137,
                    "resetsAt": "2026-08-27T00:00:00Z"
                  },
                  "meta": {
                    "asOf": "2026-08-26T16:13:24Z",
                    "disclaimer": "Educational data. Not for investment use."
                  }
                }
                """);

        assertEquals(363, client.getRemainingDailyBudget());
    }

    @Test
    void a_202_backfill_in_progress_is_retried_then_surfaced_as_a_failure() throws Exception {
        stubResponse(202, """
                {"error": {"code": "BACKFILL_IN_PROGRESS", "message": "Market data is being fetched.", "details": {}}}
                """);

        assertThrows(FauxnanceException.class, () -> client.getQuote("AAPL"));
    }

    @Test
    void a_404_symbol_not_found_fails_without_retrying() throws Exception {
        stubResponse(404, """
                {"error": {"code": "SYMBOL_NOT_FOUND", "message": "Symbol was not recognized.", "details": {}}}
                """);

        assertThrows(FauxnanceException.class, () -> client.getQuote("NOPE"));
    }
}
