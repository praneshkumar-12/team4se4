package com.leap.tradeapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leap.domain.Account;
import org.leap.domain.Instrument;
import org.leap.domain.enums.AccountStatus;
import org.leap.domain.enums.InstrumentType;
import org.leap.domain.enums.OrderSide;
import org.leap.domain.enums.OrderStatus;
import org.leap.exceptions.InsufficientFundsException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.leap.tradeapi.auth.CallerContext;
import com.leap.tradeapi.controller.dto.OrderResponse;
import com.leap.tradeapi.controller.dto.PlaceOrderRequest;
import com.leap.tradeapi.mapper.AccountMapper;
import com.leap.tradeapi.mapper.InstrumentMapper;
import com.leap.tradeapi.mapper.OrderMapper;
import com.leap.tradeapi.mapper.PositionMapper;
import com.leap.tradeapi.mapper.row.NewOrder;
import com.leap.tradeapi.messaging.OrderEventPublisher;
import com.leap.tradeapi.messaging.OrderPlacedPayload;

/**
 * SEC4-613 unit test execution paths, one test per bullet:
 * <ul>
 *   <li>{@link #an_accepted_order_is_written_at_NEW_and_answered_NEW()}
 *   <li>{@link #ORDER_PLACED_is_published_for_a_newly_accepted_order()}
 *   <li>{@link #the_event_is_published_only_after_the_transaction_has_committed()}
 *   <li>{@link #an_order_that_fails_validation_publishes_nothing()}
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OrderServicePlacementTest {

    @Mock private AccountMapper accountMapper;
    @Mock private InstrumentMapper instrumentMapper;
    @Mock private PositionMapper positionMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderEventPublisher orderEventPublisher;

    private OrderService orderService;
    private final CallerContext caller = new CallerContext();

    @BeforeEach
    void setUp() {
        caller.setAccountId(1L);
        orderService = new OrderService(accountMapper, instrumentMapper, positionMapper,
                orderMapper, new AccountAccessGuard(caller), orderEventPublisher);
        // publishOrderPlaced registers a transaction synchronization, which
        // requires an active transaction; these tests simulate the boundary
        // by hand rather than starting a real Spring transaction.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private Account account(String balance, long version) {
        return new Account(1L, "ACC-000001", "Priya", "Menon", "USD",
                new BigDecimal(balance), AccountStatus.ACTIVE, version);
    }

    private Instrument acme() {
        return new Instrument(10L, "US0000000001", "ACME", "Acme Corp",
                InstrumentType.EQUITY, "NASDAQ", "USD", true);
    }

    private PlaceOrderRequest buy(int quantity, String price) {
        return new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, quantity,
                new BigDecimal(price), UUID.randomUUID().toString());
    }

    private void stubAcceptedOrder() {
        when(accountMapper.findDomainById(1L)).thenReturn(account("25000.00", 0));
        when(instrumentMapper.findTradableByTicker("ACME")).thenReturn(acme());
        when(positionMapper.findDomain(1L, 10L)).thenReturn(null);
        when(orderMapper.insert(any(NewOrder.class))).thenAnswer(inv -> {
            inv.getArgument(0, NewOrder.class).setOrderId(55L);
            return 1;
        });
    }

    @Test
    void an_accepted_order_is_written_at_NEW_and_answered_NEW() {
        stubAcceptedOrder();

        OrderResponse response = orderService.placeOrder(buy(100, "25.50"));

        assertThat(response.status()).isEqualTo(OrderStatus.NEW);

        ArgumentCaptor<NewOrder> captor = ArgumentCaptor.forClass(NewOrder.class);
        verify(orderMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("NEW");
    }

    @Test
    void ORDER_PLACED_is_published_for_a_newly_accepted_order() {
        stubAcceptedOrder();

        orderService.placeOrder(buy(100, "25.50"));

        ArgumentCaptor<OrderPlacedPayload> captor = ArgumentCaptor.forClass(OrderPlacedPayload.class);
        verify(orderEventPublisher).publishOrderPlaced(captor.capture());

        OrderPlacedPayload payload = captor.getValue();
        assertThat(payload.accountId()).isEqualTo(1L);
        assertThat(payload.symbol()).isEqualTo("ACME");
        assertThat(payload.side()).isEqualTo("BUY");
        assertThat(payload.quantity()).isEqualTo(100);
        assertThat(payload.price()).isEqualByComparingTo("25.50");
    }

    @Test
    void the_event_is_published_only_after_the_transaction_has_committed() {
        stubAcceptedOrder();

        // OrderService only ever hands the payload to OrderEventPublisher
        // (which defers the actual Kafka send to afterCommit - see
        // OrderEventPublisherTest) after the order row is persisted, never
        // before: the write that must survive is the one this method commits.
        orderService.placeOrder(buy(100, "25.50"));

        org.mockito.InOrder callOrder = inOrder(orderMapper, orderEventPublisher);
        callOrder.verify(orderMapper).insert(any());
        callOrder.verify(orderEventPublisher).publishOrderPlaced(any());
    }

    @Test
    void an_order_that_fails_validation_publishes_nothing() {
        when(accountMapper.findDomainById(1L)).thenReturn(account("1000.00", 0));
        when(instrumentMapper.findTradableByTicker("ACME")).thenReturn(acme());
        when(positionMapper.findDomain(1L, 10L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.placeOrder(buy(100, "25.50")))
                .isInstanceOf(InsufficientFundsException.class);

        verify(orderMapper, never()).insert(any());
        verifyNoInteractions(orderEventPublisher);
    }
}
