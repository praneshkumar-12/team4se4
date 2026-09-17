package org.leap.executor.fauxnance;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.leap.pricing.Quote;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Real Fauxnance client over {@link HttpClient}, against the contract in the
 * repo's {@code openapi.yaml} (Fauxnance API, {@code /quotes/{symbol}},
 * {@code /quotes}, {@code /usage}). Retries live in here
 * ({@value #MAX_ATTEMPTS} attempts with a short linear backoff) against a
 * bounded budget, before surfacing a {@link FauxnanceException}: an outage or
 * an exhausted daily budget is a business outcome the caller resolves
 * ({@code NO_PRICE_AVAILABLE}), not something the caller retries.
 *
 * <p>Reads {@code FAUXNANCE_BASE_URL}/{@code FAUXNANCE_API_KEY} from the
 * environment — no properties file, no constants, per the ticket.
 *
 * <p>Every Fauxnance response is wrapped in a {@code {data, meta}} envelope;
 * this class unwraps {@code data} and narrows it down to the fixed
 * {@link Quote} shape ({@code symbol}/{@code bid}/{@code ask}/{@code price})
 * that {@code FillRule} and a teammate's {@code SettlementService} depend on
 * — the richer Fauxnance fields (spread, change, market state, ...) are not
 * part of that contract and are dropped here.
 */
public class FauxnanceHttpClient implements FauxnanceClient {

    private static final Logger log = Logger.getLogger(FauxnanceHttpClient.class.getName());
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration BACKOFF_UNIT = Duration.ofMillis(200);
    private static final int BATCH_SIZE = 25;

    // 202 = BackfillInProgress (no anchor yet), 429 = RateLimited, 5xx = upstream/server
    // trouble. All worth a short retry. Everything else (400/401/403/404/...) is a
    // request- or key-level problem retrying will not fix.
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(202, 429, 500, 502, 503, 504);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;

    public FauxnanceHttpClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
                requireEnv("FAUXNANCE_BASE_URL"),
                requireEnv("FAUXNANCE_API_KEY"));
    }

    public FauxnanceHttpClient(HttpClient httpClient, ObjectMapper mapper, String baseUrl, String apiKey) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    @Override
    public Quote getQuote(String symbol) {
        String body = getWithRetry(baseUrl + "/quotes/" + symbol);
        try {
            QuoteEnvelope envelope = mapper.readValue(body, QuoteEnvelope.class);
            return toDomainQuote(envelope.data());
        } catch (IOException e) {
            throw new FauxnanceException("Malformed quote response for " + symbol, e);
        }
    }

    @Override
    public Map<String, Quote> getQuotes(List<String> symbols) {
        Map<String, Quote> result = new LinkedHashMap<>();
        for (int start = 0; start < symbols.size(); start += BATCH_SIZE) {
            List<String> chunk = symbols.subList(start, Math.min(start + BATCH_SIZE, symbols.size()));
            String body = getWithRetry(baseUrl + "/quotes?symbols=" + String.join(",", chunk));
            try {
                BatchQuotesEnvelope envelope = mapper.readValue(body, BatchQuotesEnvelope.class);
                for (BatchQuoteItem item : envelope.data().quotes()) {
                    if (item.quote() != null) {
                        result.put(item.symbol(), toDomainQuote(item.quote()));
                    } else {
                        log.log(Level.WARNING, "Fauxnance batch quote skipped for {0}: {1}",
                                new Object[]{item.symbol(), item.error()});
                    }
                }
            } catch (IOException e) {
                throw new FauxnanceException("Malformed batch quote response for " + chunk, e);
            }
        }
        return result;
    }

    @Override
    public int getRemainingDailyBudget() {
        String body = getWithRetry(baseUrl + "/usage");
        try {
            UsageEnvelope envelope = mapper.readValue(body, UsageEnvelope.class);
            return Math.max(0, envelope.data().dailyQuota() - envelope.data().usedToday());
        } catch (IOException e) {
            throw new FauxnanceException("Malformed usage response", e);
        }
    }

    private static Quote toDomainQuote(FauxnanceQuote quote) {
        return new Quote(quote.symbol(), quote.bid(), quote.ask(), quote.price());
    }

    // ---- openapi.yaml response shapes, narrowed to the fields this client needs ----

    private record QuoteEnvelope(FauxnanceQuote data) {
    }

    private record FauxnanceQuote(String symbol, BigDecimal price, BigDecimal bid, BigDecimal ask) {
    }

    private record BatchQuotesEnvelope(BatchQuotesData data) {
    }

    private record BatchQuotesData(List<BatchQuoteItem> quotes) {
    }

    /** {@code quote} is present on success, {@code error} on a per-symbol failure — never both. */
    private record BatchQuoteItem(String symbol, FauxnanceQuote quote, FauxnanceError error) {
    }

    private record FauxnanceError(String code, String message) {
    }

    private record UsageEnvelope(UsageData data) {
    }

    private record UsageData(int dailyQuota, int usedToday) {
    }

    private String getWithRetry(String uri) {
        FauxnanceException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(uri))
                        .header("X-Api-Key", apiKey)
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 200) {
                    return response.body();
                }

                if (!RETRYABLE_STATUS_CODES.contains(status)) {
                    throw new FauxnanceException("Fauxnance returned non-retryable HTTP " + status + " for " + uri
                            + ": " + response.body());
                }

                lastFailure = new FauxnanceException("Fauxnance returned HTTP " + status + " for " + uri);
            } catch (IOException e) {
                lastFailure = new FauxnanceException("Fauxnance call failed for " + uri, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FauxnanceException("Interrupted while calling Fauxnance for " + uri, e);
            }

            if (attempt < MAX_ATTEMPTS) {
                sleep(BACKOFF_UNIT.multipliedBy(attempt));
            }
        }

        log.log(Level.WARNING, "Fauxnance exhausted {0} attempts for {1}", new Object[]{MAX_ATTEMPTS, uri});
        throw lastFailure != null
                ? lastFailure
                : new FauxnanceException("Fauxnance exhausted " + MAX_ATTEMPTS + " attempts for " + uri);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
