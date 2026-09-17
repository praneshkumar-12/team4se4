package org.leap.executor.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.leap.domain.enums.OrderSide;
import org.leap.events.EventEnvelope;
import org.leap.executor.kafka.TradeEventPublisher;
import org.leap.pricing.FillDecision;

/**
 * One test per "Unit Test Execution Paths" bullet in SEC4-615. The JDBC
 * chain (DataSource -> Connection -> PreparedStatement -> ResultSet) is
 * mocked directly since the executor talks to the database with plain JDBC
 * and has no ORM/embedded-database test dependency.
 */
class SettlementServiceTest {

    private static final long ORDER_ID = 42L;
    private static final long ACCOUNT_ID = 7L;
    private static final long INSTRUMENT_ID = 3L;

    private DataSource dataSource;
    private Connection connection;
    private TradeEventPublisher publisher;
    private JdbcSettlementService service;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        publisher = mock(TradeEventPublisher.class);
        when(dataSource.getConnection()).thenReturn(connection);
        service = new JdbcSettlementService(dataSource, publisher);
    }

    @Test
    void settledOrderWritesStatusCashAndPositionTogether() throws SQLException {
        stubGuard1(1);
        stubOrderLookup(OrderSide.BUY, new BigDecimal("5"));
        stubAccountRead(new BigDecimal("1000"), 3L);
        stubAccountUpdate(1);
        stubPositionRead(false, null, null);
        stubPositionWrite();
        stubTradeInsert(1);

        SettlementOutcome outcome = service.settle(ORDER_ID, FillDecision.fillAt(new BigDecimal("10")));

        assertTrue(outcome.applied());
        assertEquals("FILLED", outcome.status());
        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(publisher, times(1)).publish(anyString(), anyString(), any(EventEnvelope.class));
    }

    @Test
    void failureInAnyOfTheThreeLeavesNoneOfThemWritten() throws SQLException {
        stubGuard1(1);
        stubOrderLookup(OrderSide.BUY, new BigDecimal("5"));
        stubAccountRead(new BigDecimal("1000"), 3L);
        stubAccountUpdate(1);
        stubPositionRead(false, null, null);
        stubPositionWrite();

        // The third write (the trade insert) fails.
        PreparedStatement tradeInsert = mock(PreparedStatement.class);
        when(connection.prepareStatement(contains("INSERT INTO trades"))).thenReturn(tradeInsert);
        when(tradeInsert.executeUpdate()).thenThrow(new SQLException("disk full"));

        FillDecision decision = FillDecision.fillAt(new BigDecimal("10"));
        assertThrows(SettlementFailedException.class, () -> service.settle(ORDER_ID, decision));

        verify(connection, never()).commit();
        verify(connection).rollback();
        verify(publisher, never()).publish(anyString(), anyString(), any(EventEnvelope.class));
    }

    @Test
    void secondDeliveryAffectsZeroRowsAndPublishesNothing() throws SQLException {
        stubGuard1(0);

        SettlementOutcome outcome = service.settle(ORDER_ID, FillDecision.fillAt(new BigDecimal("10")));

        assertFalse(outcome.applied());
        assertEquals("DUPLICATE", outcome.status());
        verify(connection, never()).commit();
        verify(connection).rollback();
        verify(publisher, never()).publish(anyString(), anyString(), any(EventEnvelope.class));
        verify(connection, never()).prepareStatement(contains("FROM accounts"));
        verify(connection, never()).prepareStatement(contains("client_holdings"));
    }

    @Test
    void exhaustedOptimisticLockBudgetIsReportedAsAnError() throws SQLException {
        stubGuard1(1);
        stubOrderLookup(OrderSide.BUY, new BigDecimal("5"));
        stubAccountRead(new BigDecimal("1000"), 3L);

        PreparedStatement accountUpdate = mock(PreparedStatement.class);
        when(connection.prepareStatement(contains("UPDATE accounts SET cash_balance"))).thenReturn(accountUpdate);
        // Every attempt loses the optimistic-lock race.
        when(accountUpdate.executeUpdate()).thenReturn(0);

        FillDecision decision = FillDecision.fillAt(new BigDecimal("10"));
        assertThrows(SettlementFailedException.class, () -> service.settle(ORDER_ID, decision));

        verify(accountUpdate, times(5)).executeUpdate();
        verify(connection, never()).commit();
        verify(connection).rollback();
        verify(publisher, never()).publish(anyString(), anyString(), any(EventEnvelope.class));
    }

    // ---- stubbing helpers ----

    private void stubGuard1(int rowsAffected) throws SQLException {
        PreparedStatement guard1 = mock(PreparedStatement.class);
        when(connection.prepareStatement(contains("UPDATE orders SET status"))).thenReturn(guard1);
        when(guard1.executeUpdate()).thenReturn(rowsAffected);
    }

    private void stubOrderLookup(OrderSide side, BigDecimal quantity) throws SQLException {
        PreparedStatement lookup = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(contains("FROM orders WHERE order_id"))).thenReturn(lookup);
        when(lookup.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getLong("account_id")).thenReturn(ACCOUNT_ID);
        when(rs.getLong("instrument_id")).thenReturn(INSTRUMENT_ID);
        when(rs.getString("side")).thenReturn(side.name());
        when(rs.getBigDecimal("quantity")).thenReturn(quantity);
    }

    private void stubAccountRead(BigDecimal cashBalance, long version) throws SQLException {
        PreparedStatement select = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(contains("FROM accounts WHERE account_id"))).thenReturn(select);
        when(select.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getBigDecimal("cash_balance")).thenReturn(cashBalance);
        when(rs.getLong("version")).thenReturn(version);
    }

    private void stubAccountUpdate(int rowsAffected) throws SQLException {
        PreparedStatement update = mock(PreparedStatement.class);
        when(connection.prepareStatement(contains("UPDATE accounts SET cash_balance"))).thenReturn(update);
        when(update.executeUpdate()).thenReturn(rowsAffected);
    }

    private void stubPositionRead(boolean exists, BigDecimal quantity, BigDecimal averageCost) throws SQLException {
        PreparedStatement select = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(contains("FROM client_holdings WHERE account_id"))).thenReturn(select);
        when(select.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(exists);
        if (exists) {
            when(rs.getBigDecimal("quantity")).thenReturn(quantity);
            when(rs.getBigDecimal("average_cost")).thenReturn(averageCost);
        }
    }

    private void stubPositionWrite() throws SQLException {
        PreparedStatement insert = mock(PreparedStatement.class);
        when(connection.prepareStatement(contains("INSERT INTO client_holdings"))).thenReturn(insert);
        when(insert.executeUpdate()).thenReturn(1);

        PreparedStatement update = mock(PreparedStatement.class);
        when(connection.prepareStatement(contains("UPDATE client_holdings SET quantity"))).thenReturn(update);
        when(update.executeUpdate()).thenReturn(1);
    }

    private void stubTradeInsert(int rowsAffected) throws SQLException {
        PreparedStatement insert = mock(PreparedStatement.class);
        when(connection.prepareStatement(contains("INSERT INTO trades"))).thenReturn(insert);
        when(insert.executeUpdate()).thenReturn(rowsAffected);
    }
}
