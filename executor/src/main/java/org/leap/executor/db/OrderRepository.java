package org.leap.executor.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.leap.domain.Order;
import org.leap.domain.enums.OrderSide;
import org.leap.domain.enums.OrderStatus;
import org.leap.domain.enums.OrderType;

/** Plain-JDBC reads against {@code orders}. */
public class OrderRepository {

    private final ConnectionFactory connectionFactory;

    public OrderRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * The order as the Sprint 5 domain object {@code FillRule} evaluates.
     * {@code null} if the order does not exist.
     *
     * <p>Note this always comes back as a fresh, freshly-constructed
     * {@link Order}: the Sprint 5 constructor has no status parameter and
     * always starts an instance at {@code NEW} (status only moves forward
     * via {@code fill()}/{@code reject()}/{@code cancel()}). It cannot
     * represent an already-FILLED/REJECTED row, which is exactly why
     * {@link #findStatus(long)} exists as a separate read.
     */
    public Order findById(long orderId) {
        String sql = "SELECT order_id, idempotency_key, account_id, instrument_id, side, order_type, quantity, limit_price "
                + "FROM orders WHERE order_id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Order(
                        rs.getLong("order_id"),
                        rs.getString("idempotency_key"),
                        rs.getLong("account_id"),
                        rs.getLong("instrument_id"),
                        OrderSide.valueOf(rs.getString("side")),
                        OrderType.valueOf(rs.getString("order_type")),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("limit_price"));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load order " + orderId, e);
        }
    }

    /**
     * The internal numeric {@code order_id} for a given public UUID — the
     * only place this module needs to cross from the identifier Kafka
     * messages carry (see {@code OrderEvent}) to the one every other query
     * here is keyed by. {@code null} if no order has that public id.
     */
    public Long resolveNumericId(String publicId) {
        String sql = "SELECT order_id FROM orders WHERE public_id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, publicId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("order_id") : null;
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to resolve order " + publicId, e);
        }
    }

    /**
     * The order's current persisted status — the duplicate-delivery check's
     * source of truth. {@code null} if the order does not exist.
     */
    public OrderStatus findStatus(long orderId) {
        String sql = "SELECT status FROM orders WHERE order_id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return OrderStatus.valueOf(rs.getString("status"));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load status for order " + orderId, e);
        }
    }
}
