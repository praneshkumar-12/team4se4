package org.leap.executor.fauxnance;

import org.leap.pricing.Quote;

import java.util.List;
import java.util.Map;

public interface FauxnanceClient {
    Quote getQuote(String symbol);

    /** Internally chunks to <=25 symbols/request. */
    Map<String, Quote> getQuotes(List<String> symbols);

    int getRemainingDailyBudget();
}