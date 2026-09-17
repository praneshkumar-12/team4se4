package org.leap.executor.exec;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.leap.domain.Position;
import org.leap.domain.enums.OrderSide;
import org.leap.events.EventEnvelope;
import org.leap.events.Topics;
import org.leap.executor.kafka.TradeEventPublisher;
import org.leap.pricing.FillDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plain-JDBC settlement: the guarded status transition, the cash movement and
 * the position write happen in one transaction that either fully commits or
 * fully rolls back. The event is published only after that commit succeeds.
 *
 * <p>Offset handling is deliberately NOT done here: the Kafka consumer
 * offset must be committed by the caller only after {@link #settle} returns
 * normally (i.e. after the publish inside it already succeeded) - never
 * before. If this method throws, the caller must not commit the offset, so
 * Kafka's at-least-once redelivery gets another attempt at a clean slate.
 */
public final class JdbcSettlementService implements SettlementService {

    private static final Logger log = LoggerFactory.getLogger(JdbcSettlementService.class);
    private static final int MAX_OPTIMISTIC_LOCK_ATTEMPTS = 5;

    private final DataSource dataSource;
    private final TradeEventPublisher publisher;

    public JdbcSettlementService(DataSource dataSource, TradeEventPublisher publisher) {
        this.dataSource = dataSource;
        this.publisher = publisher;
    }

    @Override
    public SettlementOutcome settle(long orderId, FillDecision decision) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            return settleGuarded(conn, orderId, decision);
        } catch (SQLException e) {
            throw new SettlementFailedException("Could not obtain a connection to settle order " + orderId, e);
        }
    }

    private SettlementOutcome settleGuarded(Connection conn, long orderId, FillDecision decision) {
        try {
            SettlementResult result = settleInTransaction(conn, orderId, decision);
            if (result.outcome().applied()) {
                conn.commit();
                publish(orderId, result.accountId(), decision, result.outcome());
            } else {
                conn.rollback();
            }
            return result.outcome();
        } catch (RuntimeException | SQLException e) {
            rollbackQuietly(conn);
            throw (e instanceof SettlementFailedException sfe)
                    ? sfe
                    : new SettlementFailedException("Settlement failed for order " + orderId, e);
        }
    }

    /**
     * Guard 1 (first write): the guarded status transition, conditional on
     * the order still being NEW. Zero rows affected means another delivery
     * of the same message already resolved it - the duplicate-delivery
     * no-op SEC4-616 demonstrates.
     */
    private SettlementResult settleInTransaction(Connection conn, long orderId, FillDecision decision)
            throws SQLException {
        String newStatus = decision.fill() ? "FILLED" : "REJECTED";
        String rejectionReason = decision.fill() ? null : decision.rejectionReason();

        int guardRows;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE orders SET status = ?, rejection_reason = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE order_id = ? AND status = 'NEW'")) {
            ps.setString(1, newStatus);
            ps.setString(2, rejectionReason);
            ps.setLong(3, orderId);
            guardRows = ps.executeUpdate();
        }

        if (guardRows == 0) {
            log.warn("SETTLEMENT_DUPLICATE_DELIVERY order_id={} attempted_status={} - order was not NEW, "
                    + "guard 1 affected 0 rows; skipping cash, position and publish", orderId, newStatus);
            return new SettlementResult(new SettlementOutcome(false, "DUPLICATE", null), null);
        }

        OrderSnapshot order = loadOrder(conn, orderId);

        if (decision.fill()) {
            applyFill(conn, order, decision);
        }

        return new SettlementResult(new SettlementOutcome(true, newStatus, rejectionReason), order.accountId());
    }

    private OrderSnapshot loadOrder(Connection conn, long orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT account_id, instrument_id, side, quantity FROM orders WHERE order_id = ?")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SettlementFailedException("Order " + orderId + " vanished after guard 1 succeeded");
                }
                return new OrderSnapshot(
                        orderId,
                        rs.getLong("account_id"),
                        rs.getLong("instrument_id"),
                        OrderSide.valueOf(rs.getString("side")),
                        rs.getBigDecimal("quantity"));
            }
        }
    }

    private void applyFill(Connection conn, OrderSnapshot order, FillDecision decision) throws SQLException {
        settleCashWithRetry(conn, order, decision);
        upsertPosition(conn, order, decision);
        insertTrade(conn, order, decision);
    }

    /**
     * Guard 2: optimistic lock on {@code accounts.version}. Zero rows
     * affected means a concurrent writer moved the version between our read
     * and our write, so we re-read and retry - a single retry succeeding is
     * normal and expected, not logged as a problem. Only running out of
     * attempts is an error.
     */
    private void settleCashWithRetry(Connection conn, OrderSnapshot order, FillDecision decision) throws SQLException {
        BigDecimal notional = order.quantity().multiply(decision.executionPrice());

        try (PreparedStatement selectPs = conn.prepareStatement(
                     "SELECT cash_balance, version FROM accounts WHERE account_id = ?");
             PreparedStatement updatePs = conn.prepareStatement(
                     "UPDATE accounts SET cash_balance = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP "
                             + "WHERE account_id = ? AND version = ?")) {

            for (int attempt = 1; attempt <= MAX_OPTIMISTIC_LOCK_ATTEMPTS; attempt++) {
                BigDecimal cashBalance;
                long version;
                selectPs.setLong(1, order.accountId());
                try (ResultSet rs = selectPs.executeQuery()) {
                    if (!rs.next()) {
                        throw new SettlementFailedException("Account " + order.accountId() + " not found during settlement");
                    }
                    cashBalance = rs.getBigDecimal("cash_balance");
                    version = rs.getLong("version");
                }

                BigDecimal newBalance = order.side() == OrderSide.BUY
                        ? cashBalance.subtract(notional)
                        : cashBalance.add(notional);

                updatePs.setBigDecimal(1, newBalance);
                updatePs.setLong(2, order.accountId());
                updatePs.setLong(3, version);
                int updated = updatePs.executeUpdate();

                if (updated > 0) {
                    return;
                }
            }
        }

        throw new SettlementFailedException("Optimistic lock on account " + order.accountId()
                + " was not won after " + MAX_OPTIMISTIC_LOCK_ATTEMPTS + " attempts");
    }

    private void upsertPosition(Connection conn, OrderSnapshot order, FillDecision decision) throws SQLException {
        BigDecimal existingQuantity = BigDecimal.ZERO;
        BigDecimal existingAverageCost = BigDecimal.ZERO;
        boolean exists;

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT quantity, average_cost FROM client_holdings WHERE account_id = ? AND instrument_id = ?")) {
            ps.setLong(1, order.accountId());
            ps.setLong(2, order.instrumentId());
            try (ResultSet rs = ps.executeQuery()) {
                exists = rs.next();
                if (exists) {
                    existingQuantity = rs.getBigDecimal("quantity");
                    existingAverageCost = rs.getBigDecimal("average_cost");
                }
            }
        }

        // holdingId is a placeholder to satisfy the domain object's constructor - it
        // is never persisted; the real key is (account_id, instrument_id).
        Position position = new Position(1L, order.accountId(), order.instrumentId(),
                existingQuantity, existingAverageCost);

        if (order.side() == OrderSide.BUY) {
            position.buy(order.quantity(), decision.executionPrice());
        } else {
            position.sell(order.quantity());
        }

        if (exists) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE client_holdings SET quantity = ?, average_cost = ?, updated_at = CURRENT_TIMESTAMP "
                            + "WHERE account_id = ? AND instrument_id = ?")) {
                ps.setBigDecimal(1, position.getQuantity());
                ps.setBigDecimal(2, position.getAverageCost());
                ps.setLong(3, order.accountId());
                ps.setLong(4, order.instrumentId());
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO client_holdings (account_id, instrument_id, quantity, average_cost) "
                            + "VALUES (?, ?, ?, ?)")) {
                ps.setLong(1, order.accountId());
                ps.setLong(2, order.instrumentId());
                ps.setBigDecimal(3, position.getQuantity());
                ps.setBigDecimal(4, position.getAverageCost());
                ps.executeUpdate();
            }
        }
    }

    private void insertTrade(Connection conn, OrderSnapshot order, FillDecision decision) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO trades (order_id, executed_price, executed_quantity, executed_at, fee) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, 0)")) {
            ps.setLong(1, order.orderId());
            ps.setBigDecimal(2, decision.executionPrice());
            ps.setBigDecimal(3, order.quantity());
            ps.executeUpdate();
        }
    }

    private void publish(long orderId, Long accountId, FillDecision decision, SettlementOutcome outcome) {
        String eventType = decision.fill() ? "ORDER_FILLED" : "ORDER_REJECTED";
        TradeSettledPayload payload = new TradeSettledPayload(
                orderId, accountId, outcome.status(), decision.executionPrice(), outcome.reason());
        EventEnvelope<TradeSettledPayload> envelope = EventEnvelope.of(eventType, "trade-executor", payload);
        publisher.publish(Topics.TRADE_EVENTS, String.valueOf(accountId), envelope);
    }

    private static void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException e) {
            log.error("Rollback failed after a settlement error", e);
        }
    }

    private record OrderSnapshot(long orderId, long accountId, long instrumentId, OrderSide side, BigDecimal quantity) {
    }

    private record SettlementResult(SettlementOutcome outcome, Long accountId) {
    }
}
