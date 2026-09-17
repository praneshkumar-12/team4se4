package org.leap.executor.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.leap.domain.Instrument;
import org.leap.domain.enums.InstrumentType;

/** Plain-JDBC reads against {@code instruments}. */
public class InstrumentRepository {

    private final ConnectionFactory connectionFactory;

    public InstrumentRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * The instrument as it stands right now. {@code null} if it does not
     * exist; callers check {@link Instrument#isTradable()} separately, since
     * "unknown" and "delisted" are both resolved the same way by the caller.
     */
    public Instrument findById(long instrumentId) {
        String sql = "SELECT instrument_id, isin, ticker, name, type, exchange, currency, is_active "
                + "FROM instruments WHERE instrument_id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, instrumentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Instrument(
                        rs.getLong("instrument_id"),
                        rs.getString("isin"),
                        rs.getString("ticker"),
                        rs.getString("name"),
                        InstrumentType.valueOf(rs.getString("type")),
                        rs.getString("exchange"),
                        rs.getString("currency"),
                        rs.getBoolean("is_active"));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load instrument " + instrumentId, e);
        }
    }
}
