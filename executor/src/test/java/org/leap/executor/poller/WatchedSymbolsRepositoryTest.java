package org.leap.executor.poller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises the watched-symbols query against an H2 rendering of the relevant Sprint 3 tables. */
class WatchedSymbolsRepositoryTest {

    private String jdbcUrl;
    private Connection keepAliveConnection;
    private WatchedSymbolsRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        jdbcUrl = "jdbc:h2:mem:watched-symbols-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        keepAliveConnection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = keepAliveConnection.createStatement()) {
            statement.execute("""
                    CREATE TABLE instruments (
                        instrument_id BIGINT PRIMARY KEY,
                        ticker VARCHAR(20) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE client_holdings (
                        holding_id BIGINT PRIMARY KEY,
                        account_id BIGINT NOT NULL,
                        instrument_id BIGINT NOT NULL,
                        quantity NUMERIC(20,8) NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE orders (
                        order_id BIGINT PRIMARY KEY,
                        account_id BIGINT NOT NULL,
                        instrument_id BIGINT NOT NULL,
                        status VARCHAR(20) NOT NULL
                    )
                    """);
        }
        repository = new WatchedSymbolsRepository(() -> DriverManager.getConnection(jdbcUrl));
    }

    @AfterEach
    void tearDown() throws SQLException {
        keepAliveConnection.close();
    }

    private void insertInstrument(long id, String ticker) throws SQLException {
        try (Statement statement = keepAliveConnection.createStatement()) {
            statement.execute("INSERT INTO instruments (instrument_id, ticker) VALUES (" + id + ", '" + ticker + "')");
        }
    }

    private void insertHolding(long instrumentId, java.math.BigDecimal quantity) throws SQLException {
        try (Statement statement = keepAliveConnection.createStatement()) {
            statement.execute("INSERT INTO client_holdings (holding_id, account_id, instrument_id, quantity) VALUES ("
                    + instrumentId + ", 1, " + instrumentId + ", " + quantity + ")");
        }
    }

    private void insertOrder(long instrumentId, String status) throws SQLException {
        try (Statement statement = keepAliveConnection.createStatement()) {
            statement.execute("INSERT INTO orders (order_id, account_id, instrument_id, status) VALUES ("
                    + instrumentId + ", 1, " + instrumentId + ", '" + status + "')");
        }
    }

    @Test
    void includesInstrumentsWithNonZeroHoldings() throws SQLException {
        insertInstrument(1, "AAPL");
        insertHolding(1, new java.math.BigDecimal("10"));

        assertEquals(Set.of("AAPL"), repository.findWatchedSymbols());
    }

    @Test
    void excludesInstrumentsWithZeroHoldingsAndNoRestingOrder() throws SQLException {
        insertInstrument(1, "AAPL");
        insertHolding(1, java.math.BigDecimal.ZERO);

        assertEquals(Set.of(), repository.findWatchedSymbols());
    }

    @Test
    void includesInstrumentsWithARestingNewOrder() throws SQLException {
        insertInstrument(2, "MSFT");
        insertOrder(2, "NEW");

        assertEquals(Set.of("MSFT"), repository.findWatchedSymbols());
    }

    @Test
    void excludesInstrumentsWhoseOnlyOrderIsNotNew() throws SQLException {
        insertInstrument(2, "MSFT");
        insertOrder(2, "FILLED");

        assertEquals(Set.of(), repository.findWatchedSymbols());
    }

    @Test
    void deduplicatesASymbolThatIsBothHeldAndOnARestingOrder() throws SQLException {
        insertInstrument(3, "GOOG");
        insertHolding(3, new java.math.BigDecimal("5"));
        insertOrder(3, "NEW");

        assertEquals(Set.of("GOOG"), repository.findWatchedSymbols());
    }
}