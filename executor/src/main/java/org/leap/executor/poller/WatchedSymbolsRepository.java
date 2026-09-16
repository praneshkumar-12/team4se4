package org.leap.executor.poller;

import org.leap.executor.db.ConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * There is no literal watchlist table in the schema. "Worth polling" means:
 * instruments with a non-zero {@code client_holdings.quantity} for any
 * account, plus instruments on any resting order at status {@code NEW}.
 * Symbol here is {@code instruments.ticker}.
 */
public class WatchedSymbolsRepository {

    private static final String QUERY = """
            SELECT DISTINCT i.ticker AS symbol
            FROM instruments i
            JOIN client_holdings ch ON ch.instrument_id = i.instrument_id
            WHERE ch.quantity <> 0

            UNION

            SELECT DISTINCT i.ticker AS symbol
            FROM instruments i
            JOIN orders o ON o.instrument_id = i.instrument_id
            WHERE o.status = 'NEW'
            """;

    private final ConnectionFactory connectionFactory;

    public WatchedSymbolsRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public Set<String> findWatchedSymbols() {
        Set<String> symbols = new LinkedHashSet<>();
        try (Connection connection = connectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                symbols.add(resultSet.getString("symbol"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load watched symbols", e);
        }
        return symbols;
    }
}
