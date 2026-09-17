package org.leap.executor.exec;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.leap.domain.Account;
import org.leap.domain.Instrument;
import org.leap.domain.Order;
import org.leap.domain.enums.AccountStatus;
import org.leap.domain.enums.InstrumentType;
import org.leap.domain.enums.OrderSide;
import org.leap.domain.enums.OrderStatus;
import org.leap.domain.enums.OrderType;
import org.leap.executor.db.AccountRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExecutionServiceTest {

    private static final long ORDER_ID = 1L;
    private static final String ORDER_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";
    private static final long ACCOUNT_ID = 1L;
    private static final long INSTRUMENT_ID = 1L;

    private OrderRepository orderRepository;
    private InstrumentRepository instrumentRepository;
    private AccountRepository accountRepository;
    private FauxnanceClient fauxnanceClient;
    private FakeSettlementService settlementService;
    private ExecutionService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        instrumentRepository = mock(InstrumentRepository.class);
        accountRepository = mock(AccountRepository.class);
        fauxnanceClient = mock(FauxnanceClient.class);
        settlementService = new FakeSettlementService();
        service = new ExecutionService(orderRepository, instrumentRepository, accountRepository, fauxnanceClient, settlementService);
        when(orderRepository.resolveNumericId(ORDER_PUBLIC_ID)).thenReturn(ORDER_ID);
    }

    private static Order buyOrder(BigDecimal quantity, String limitPrice) {
        return new Order(ORDER_ID, "idem-1", ACCOUNT_ID, INSTRUMENT_ID, OrderSide.BUY, OrderType.LIMIT, quantity, new BigDecimal(limitPrice));
    }

    private static Instrument tradableInstrument() {
        return new Instrument(INSTRUMENT_ID, "US0000000001", "ACME", "Acme Corp", InstrumentType.EQUITY, "NASDAQ", "USD", true);
    }

    private static Account account(String cash, AccountStatus status) {
        return new Account(ACCOUNT_ID, "ACC-000001", "Priya", "Menon", "USD", new BigDecimal(cash), status, 0L);
    }

    // Duplicate-delivery defense (acceptance criterion, not a listed unit test bullet, but the
    // flow's first step and exactly what a teammate's SEC4-616 demo exercises).

    @Test
    void an_order_already_settled_is_skipped_without_settling_again() {
        when(orderRepository.findStatus(ORDER_ID)).thenReturn(OrderStatus.FILLED);

        service.execute(ORDER_PUBLIC_ID);

        assertTrue(settlementService.decisions.isEmpty());
        verifyNoInteractions(instrumentRepository, accountRepository, fauxnanceClient);
    }

    // Instrument tradability re-check (flow step 2).

    @Test
    void an_order_against_a_non_tradable_instrument_is_rejected() {
        when(orderRepository.findStatus(ORDER_ID)).thenReturn(OrderStatus.NEW);
        when(orderRepository.findById(ORDER_ID)).thenReturn(buyOrder(BigDecimal.TEN, "25.50"));
        when(instrumentRepository.findById(INSTRUMENT_ID)).thenReturn(null);

        service.execute(ORDER_PUBLIC_ID);

        assertEquals("INSTRUMENT_NOT_TRADABLE", settlementService.lastDecision().rejectionReason());
        verifyNoInteractions(fauxnanceClient);
    }

    // ---- Unit Test Execution Paths bullets ----

    @Test
    void an_account_suspended_after_acceptance_does_not_trade() {
        when(orderRepository.findStatus(ORDER_ID)).thenReturn(OrderStatus.NEW);
        when(orderRepository.findById(ORDER_ID)).thenReturn(buyOrder(BigDecimal.TEN, "25.50"));
        when(instrumentRepository.findById(INSTRUMENT_ID)).thenReturn(tradableInstrument());
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(account("10000.00", AccountStatus.SUSPENDED));

        service.execute(ORDER_PUBLIC_ID);

        FillDecision decision = settlementService.lastDecision();
        assertFalse(decision.fill());
        assertEquals("ACCOUNT_SUSPENDED", decision.rejectionReason());
        verifyNoInteractions(fauxnanceClient);
    }

    @Test
    void an_execution_time_recheck_rejects_an_order_the_account_can_no_longer_afford() {
        when(orderRepository.findStatus(ORDER_ID)).thenReturn(OrderStatus.NEW);
        when(orderRepository.findById(ORDER_ID)).thenReturn(buyOrder(new BigDecimal("1000"), "25.50"));
        when(instrumentRepository.findById(INSTRUMENT_ID)).thenReturn(tradableInstrument());
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(account("100.00", AccountStatus.ACTIVE));

        service.execute(ORDER_PUBLIC_ID);

        FillDecision decision = settlementService.lastDecision();
        assertFalse(decision.fill());
        assertEquals("INSUFFICIENT_FUNDS", decision.rejectionReason());
        verifyNoInteractions(fauxnanceClient);
    }

    @Test
    void an_order_with_no_available_price_is_resolved_rather_than_left_at_NEW() {
        when(orderRepository.findStatus(ORDER_ID)).thenReturn(OrderStatus.NEW);
        when(orderRepository.findById(ORDER_ID)).thenReturn(buyOrder(BigDecimal.TEN, "25.50"));
        when(instrumentRepository.findById(INSTRUMENT_ID)).thenReturn(tradableInstrument());
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(account("100000.00", AccountStatus.ACTIVE));
        when(fauxnanceClient.getQuote("ACME")).thenThrow(new FauxnanceException("outage"));

        service.execute(ORDER_PUBLIC_ID);

        FillDecision decision = settlementService.lastDecision();
        assertFalse(decision.fill());
        assertEquals("NO_PRICE_AVAILABLE", decision.rejectionReason());
    }

    // Happy path, completing the flow this service exists for.

    @Test
    void an_affordable_marketable_buy_is_filled_via_FillRule() {
        when(orderRepository.findStatus(ORDER_ID)).thenReturn(OrderStatus.NEW);
        when(orderRepository.findById(ORDER_ID)).thenReturn(buyOrder(BigDecimal.TEN, "25.50"));
        when(instrumentRepository.findById(INSTRUMENT_ID)).thenReturn(tradableInstrument());
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(account("100000.00", AccountStatus.ACTIVE));
        when(fauxnanceClient.getQuote("ACME"))
                .thenReturn(new Quote("ACME", new BigDecimal("25.00"), new BigDecimal("25.40"), new BigDecimal("25.20")));

        service.execute(ORDER_PUBLIC_ID);

        FillDecision decision = settlementService.lastDecision();
        assertTrue(decision.fill());
        assertEquals(0, new BigDecimal("25.40000000").compareTo(decision.executionPrice()));
    }
}
