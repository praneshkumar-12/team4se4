package org.leap.executor.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.leap.domain.Account;
import org.leap.domain.enums.AccountStatus;

/**
 * Plain-JDBC reads against {@code accounts}. Read-only for now — a
 * teammate's SEC4-615 adds the optimistic-locked write method to this same
 * file when settlement lands; expect that as a small merge conflict later,
 * not a mistake.
 */
public class AccountRepository {

    private final ConnectionFactory connectionFactory;

    public AccountRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /** The account as the Sprint 5 domain object. {@code null} if it does not exist. */
    public Account findById(long accountId) {
        String sql = "SELECT a.account_id, a.account_reference, c.first_name, c.last_name, "
                + "a.currency, a.cash_balance, a.account_status, a.version "
                + "FROM accounts a JOIN clients c ON c.client_id = a.client_id "
                + "WHERE a.account_id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Account(
                        rs.getLong("account_id"),
                        rs.getString("account_reference"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("currency"),
                        rs.getBigDecimal("cash_balance"),
                        AccountStatus.valueOf(rs.getString("account_status")),
                        rs.getLong("version"));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load account " + accountId, e);
        }
    }
}
