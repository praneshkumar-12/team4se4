package org.leap.executor.exec;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.leap.executor.db.AccountRepository;
import org.leap.executor.db.ConnectionFactory;
import org.leap.executor.db.InstrumentRepository;
import org.leap.executor.db.OrderRepository;
import org.leap.executor.fauxnance.FauxnanceClient;
import org.leap.executor.fauxnance.FauxnanceException;
import org.leap.pricing.FillDecision;
import org.leap.pricing.Quote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the full consume-price-decide flow with real JDBC repositories
 * against H2 standing in for Postgres, and a mocked {@link FauxnanceClient}
 * standing in for the live Fauxnance HTTP call (an interface specifically so
 * this is easy, per the ticket, rather than a real HTTP mock server) — for
 * the three outcomes called out: a fill, a reject, and a
 * pricing-unavailable resolution.
 *
 * <p>No Kafka here: {@code OrderEventConsumer} is plumbing around this flow
 * and is not exercised by this test.
 */
class ExecutorIntegrationTest {

    // A fresh, uniquely-named DB per test: each test method gets its own
    // isolated schema instead of sharing one in-memory instance across the class.
    private String jdbcUrl;

    private Connection setupConnection;
    private FauxnanceClient fauxnanceClient;
    private FakeSettlementService settlementService;
    private ExecutionService service;

    @BeforeEach
    void setUp() throws SQLException {
        jdbcUrl = "jdbc:h2:mem:executor-it-" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
        setupConnection = DriverManager.getConnection(jdbcUrl, "sa", "");
        try (Statement st = setupConnection.createStatement()) {
            st.execute("CREATE TABLE clients (client_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                    + "first_name VARCHAR(100) NOT NULL, last_name VARCHAR(100) NOT NULL)");
            st.execute("CREATE TABLE accounts (account_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                    + "account_reference VARCHAR(50) NOT NULL, client_id BIGINT NOT NULL, currency CHAR(3) NOT NULL, "
                    + "account_status VARCHAR(20) NOT NULL, cash_balance NUMERIC(20,8) NOT NULL, version BIGINT NOT NULL)");
            st.execute("CREATE TABLE instruments (instrument_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                    + "isin VARCHAR(12) NOT NULL, ticker VARCHAR(20) NOT NULL, name VARCHAR(255) NOT NULL, "
                    + "type VARCHAR(30) NOT NULL, exchange VARCHAR(50) NOT NULL, is_active BOOLEAN NOT NULL, currency CHAR(3) NOT NULL)");
            st.execute("CREATE TABLE orders (order_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                    + "idempotency_key VARCHAR(255) NOT NULL, quantity NUMERIC(20,8) NOT NULL, account_id BIGINT NOT NULL, "
                    + "instrument_id BIGINT NOT NULL, limit_price NUMERIC(20,8), status VARCHAR(20) NOT NULL, "
                    + "side VARCHAR(10) NOT NULL, order_type VARCHAR(20) NOT NULL)");

            st.execute("INSERT INTO clients (first_name, last_name) VALUES ('Priya', 'Menon')");
            st.execute("INSERT INTO accounts (account_reference, client_id, currency, account_status, cash_balance, version) "
                    + "VALUES ('ACC-000001', 1, 'USD', 'ACTIVE', 100000.00, 0)");
            st.execute("INSERT INTO instruments (isin, ticker, name, type, exchange, is_active, currency) "
                    + "VALUES ('US0000000001', 'ACME', 'Acme Corp', 'EQUITY', 'NASDAQ', TRUE, 'USD')");
        }

        ConnectionFactory connectionFactory = () -> DriverManager.getConnection(jdbcUrl, "sa", "");
        OrderRepository orderRepository = new OrderRepository(connectionFactory);
        InstrumentRepository instrumentRepository = new InstrumentRepository(connectionFactory);
        AccountRepository accountRepository = new AccountRepository(connectionFactory);
        fauxnanceClient = mock(FauxnanceClient.class);
        settlementService = new FakeSettlementService();

        service = new ExecutionService(orderRepository, instrumentRepository, accountRepository, fauxnanceClient, settlementService);
    }

    @AfterEach
    void tearDown() throws SQLException {
        setupConnection.close();
    }

    private long insertNewOrder(String idempotencyKey, BigDecimal quantity, BigDecimal limitPrice, String side) throws SQLException {
        try (Statement st = setupConnection.createStatement()) {
            st.execute("INSERT INTO orders (idempotency_key, quantity, account_id, instrument_id, limit_price, status, side, order_type) "
                            + "VALUES ('" + idempotencyKey + "', " + quantity + ", 1, 1, " + limitPrice + ", 'NEW', '" + side + "', 'LIMIT')",
                    Statement.RETURN_GENERATED_KEYS);
            try (ResultSet rs = st.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void a_marketable_buy_is_loaded_priced_and_filled() throws SQLException {
        long orderId = insertNewOrder("it-fill-1", new BigDecimal("10"), new BigDecimal("25.50"), "BUY");
        when(fauxnanceClient.getQuote("ACME"))
                .thenReturn(new Quote("ACME", new BigDecimal("25.00"), new BigDecimal("25.40"), new BigDecimal("25.20")));

        service.execute(orderId);

        FillDecision decision = settlementService.lastDecision();
        assertTrue(decision.fill());
        assertEquals(0, new BigDecimal("25.40000000").compareTo(decision.executionPrice()));
    }

    @Test
    void an_unmarketable_buy_is_rejected_not_marketable() throws SQLException {
        long orderId = insertNewOrder("it-reject-1", new BigDecimal("10"), new BigDecimal("20.00"), "BUY");
        when(fauxnanceClient.getQuote("ACME"))
                .thenReturn(new Quote("ACME", new BigDecimal("25.00"), new BigDecimal("25.40"), new BigDecimal("25.20")));

        service.execute(orderId);

        FillDecision decision = settlementService.lastDecision();
        assertFalse(decision.fill());
        assertEquals("NOT_MARKETABLE", decision.rejectionReason());
    }

    @Test
    void a_fauxnance_outage_resolves_the_order_as_no_price_available() throws SQLException {
        long orderId = insertNewOrder("it-nopricing-1", new BigDecimal("10"), new BigDecimal("25.50"), "BUY");
        when(fauxnanceClient.getQuote("ACME")).thenThrow(new FauxnanceException("simulated outage"));

        service.execute(orderId);

        FillDecision decision = settlementService.lastDecision();
        assertFalse(decision.fill());
        assertEquals("NO_PRICE_AVAILABLE", decision.rejectionReason());
    }
}
