package org.leap.domain;

import java.util.Objects;

import org.leap.domain.enums.InstrumentType;

public class Instrument {

    private final Long instrumentId;
    private final String isin;
    private final String ticker;
    private final String name;
    private final InstrumentType type;
    private final String exchange;
    private final String currency;
    private boolean active;

    public Instrument(
            Long instrumentId,
            String isin,
            String ticker,
            String name,
            InstrumentType type,
            String exchange,
            String currency,
            boolean active) {

        this.instrumentId = Objects.requireNonNull(instrumentId);
        this.isin = Objects.requireNonNull(isin);
        this.ticker = Objects.requireNonNull(ticker);
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.exchange = Objects.requireNonNull(exchange);
        this.currency = Objects.requireNonNull(currency);
        this.active = active;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public String getIsin() {
        return isin;
    }

    public String getTicker() {
        return ticker;
    }

    public String getName() {
        return name;
    }

    public InstrumentType getType() {
        return type;
    }

    public String getExchange() {
        return exchange;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isTradable() {
        return active;
    }

    public void delist() {
        this.active = false;
    }
}