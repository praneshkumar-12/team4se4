package org.leap.executor.fauxnance;

import org.leap.pricing.Quote;

import java.util.List;
import java.util.Map;

public interface FauxnanceClient {
    Quote getQuote(String symbol);
    Map<String, Quote> getQuotes(List<String> symbols); // chunks internally to <=25/request, used later by a teammate's poller
    int getRemainingDailyBudget();
}
