package org.leap.executor.db;

import org.leap.executor.kafka.OrderExistenceChecker;
import org.leap.executor.kafka.OrderLookupException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class JdbcOrderExistenceChecker implements OrderExistenceChecker {

    private static final String QUERY = "SELECT 1 FROM orders WHERE public_id = ?";

    private final ConnectionFactory connectionFactory;

    public JdbcOrderExistenceChecker(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public boolean exists(String orderId) throws OrderLookupException {
        try (Connection connection = connectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(QUERY)) {
            statement.setString(1, orderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new OrderLookupException("Failed to look up order " + orderId, e);
        }
    }
}
