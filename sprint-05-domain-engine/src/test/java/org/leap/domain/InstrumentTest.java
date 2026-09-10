package org.leap.domain;

import org.junit.jupiter.api.Test;
import org.leap.domain.enums.*;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentTest {

    @Test
    void shouldCreateTradableInstrument() {
        Instrument instrument = new Instrument(
                1L,
                "US0378331005",
                "AAPL",
                "Apple Inc.",
                InstrumentType.EQUITY,
                "NASDAQ",
                "USD",
                true
        );

        assertEquals(1L, instrument.getInstrumentId());
        assertEquals("US0378331005", instrument.getIsin());
        assertEquals("AAPL", instrument.getTicker());
        assertEquals("Apple Inc.", instrument.getName());
        assertEquals(InstrumentType.EQUITY, instrument.getType());
        assertEquals("NASDAQ", instrument.getExchange());
        assertEquals("USD", instrument.getCurrency());
        assertTrue(instrument.isActive());
        assertTrue(instrument.isTradable());
    }

    @Test
    void inactiveInstrumentShouldNotBeTradable() {
        Instrument instrument = new Instrument(
                1L,
                "US0378331005",
                "AAPL",
                "Apple Inc.",
                InstrumentType.EQUITY,
                "NASDAQ",
                "USD",
                false
        );

        assertFalse(instrument.isActive());
        assertFalse(instrument.isTradable());
    }

    @Test
    void shouldDelistInstrumentWithoutDeletingIt() {
        Instrument instrument = activeInstrument();

        instrument.delist();

        assertFalse(instrument.isActive());
        assertFalse(instrument.isTradable());
        assertEquals("AAPL", instrument.getTicker());
    }

    @Test
    void shouldSupportEquityInstrument() {
        Instrument instrument = activeInstrument();

        assertEquals(
                InstrumentType.EQUITY,
                instrument.getType()
        );
    }

    @Test
    void shouldSupportEtfInstrument() {
        Instrument instrument = new Instrument(
                2L,
                "IE00B4L5Y983",
                "VWCE",
                "Vanguard FTSE All-World",
                InstrumentType.ETF,
                "XETRA",
                "EUR",
                true
        );

        assertEquals(InstrumentType.ETF, instrument.getType());
    }

    @Test
    void shouldSupportCurrencyPairInstrument() {
        Instrument instrument = new Instrument(
                3L,
                "EURUSDPAIR01",
                "EURUSD",
                "Euro US Dollar",
                InstrumentType.CURRENCY_PAIR,
                "FX",
                "USD",
                true
        );

        assertEquals(
                InstrumentType.CURRENCY_PAIR,
                instrument.getType()
        );
    }

    @Test
    void shouldSupportCryptoPairInstrument() {
        Instrument instrument = new Instrument(
                4L,
                "BTCUSDPAIR01",
                "BTCUSD",
                "Bitcoin US Dollar",
                InstrumentType.CRYPTO_PAIR,
                "CRYPTO",
                "USD",
                true
        );

        assertEquals(
                InstrumentType.CRYPTO_PAIR,
                instrument.getType()
        );
    }

    private Instrument activeInstrument() {
        return new Instrument(
                1L,
                "US0378331005",
                "AAPL",
                "Apple Inc.",
                InstrumentType.EQUITY,
                "NASDAQ",
                "USD",
                true
        );
    }
}