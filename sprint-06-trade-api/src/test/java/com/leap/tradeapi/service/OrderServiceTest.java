package com.leap.tradeapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leap.domain.Account;
import org.leap.domain.Instrument;
import org.leap.domain.Position;
import org.leap.domain.enums.AccountStatus;
import org.leap.domain.enums.InstrumentType;
import org.leap.domain.enums.OrderSide;
import org.leap.domain.enums.OrderStatus;
import org.leap.exceptions.InsufficientFundsException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.leap.tradeapi.auth.CallerContext;
import com.leap.tradeapi.controller.dto.OrderResponse;
import com.leap.tradeapi.controller.dto.PlaceOrderRequest;
import com.leap.tradeapi.error.ConcurrentUpdateException;
import com.leap.tradeapi.error.OrderNotCancellableException;
import com.leap.tradeapi.mapper.AccountMapper;
import com.leap.tradeapi.mapper.InstrumentMapper;
import com.leap.tradeapi.mapper.OrderMapper;
import com.leap.tradeapi.mapper.PositionMapper;
import com.leap.tradeapi.mapper.TradeMapper;
import com.leap.tradeapi.mapper.row.NewOrder;
import com.leap.tradeapi.mapper.row.OrderRow;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private AccountMapper accountMapper;
    @Mock private InstrumentMapper instrumentMapper;
    @Mock private PositionMapper positionMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private TradeMapper tradeMapper;

    private OrderService orderService;
    private final CallerContext caller = new CallerContext();

    @BeforeEach
    void setUp() {
        caller.setAccountId(1L);
        orderService = new OrderService(accountMapper, instrumentMapper, positionMapper,
                orderMapper, tradeMapper, new AccountAccessGuard(caller));
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

    @Test
    void place_order_commits_cash_and_position_together() {
        when(accountMapper.findDomainById(1L)).thenReturn(account("25000.00", 0));
        when(instrumentMapper.findTradableByTicker("ACME")).thenReturn(acme());
        when(positionMapper.findDomain(1L, 10L)).thenReturn(null);
        when(orderMapper.insert(any(NewOrder.class))).thenAnswer(inv -> {
            inv.getArgument(0, NewOrder.class).setOrderId(55L);
            return 1;
        });
        when(accountMapper.updateBalanceWithVersion(eq(1L), any(BigDecimal.class), eq(0L))).thenReturn(1);

        OrderResponse response = orderService.placeOrder(buy(100, "25.50"));

        assertThat(response.status()).isEqualTo(OrderStatus.FILLED);
        // cash moved: 25000 - (100 * 25.50) = 22450
        verify(accountMapper).updateBalanceWithVersion(1L, new BigDecimal("22450.00"), 0L);
        // position opened and the trade written against the generated order key
        verify(positionMapper).insertPosition(eq(1L), eq(10L), any(BigDecimal.class), any(BigDecimal.class));
        verify(tradeMapper).insert(eq(55L), eq(new BigDecimal("25.50")), any(BigDecimal.class), any());
    }

    @Test
    void failed_order_rolls_back_with_no_partial_write() {
        when(accountMapper.findDomainById(1L)).thenReturn(account("1000.00", 0));
        when(instrumentMapper.findTradableByTicker("ACME")).thenReturn(acme());
        when(positionMapper.findDomain(1L, 10L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.placeOrder(buy(100, "25.50")))
                .isInstanceOf(InsufficientFundsException.class);

        verify(orderMapper, never()).insert(any());
        verify(accountMapper, never()).updateBalanceWithVersion(anyLong(), any(), anyLong());
        verify(positionMapper, never()).insertPosition(anyLong(), anyLong(), any(), any());
        verify(tradeMapper, never()).insert(anyLong(), any(), any(), any());
    }

    @Test
    void a_concurrent_update_is_detected_and_the_second_writer_is_refused() {
        when(accountMapper.findDomainById(1L)).thenReturn(account("25000.00", 3));
        when(instrumentMapper.findTradableByTicker("ACME")).thenReturn(acme());
        when(positionMapper.findDomain(1L, 10L)).thenReturn(null);
        when(orderMapper.insert(any(NewOrder.class))).thenAnswer(inv -> {
            inv.getArgument(0, NewOrder.class).setOrderId(55L);
            return 1;
        });
        // the version moved under us: zero rows affected
        when(accountMapper.updateBalanceWithVersion(eq(1L), any(BigDecimal.class), eq(3L))).thenReturn(0);

        assertThatThrownBy(() -> orderService.placeOrder(buy(100, "25.50")))
                .isInstanceOf(ConcurrentUpdateException.class);

        verify(tradeMapper, never()).insert(anyLong(), any(), any(), any());
    }

    @Test
    void cancelling_a_filled_order_returns_ORD_409() {
        OrderRow filled = new OrderRow(55L, UUID.randomUUID().toString(), 1L, "ACME",
                OrderSide.BUY, OrderStatus.FILLED, new BigDecimal("100"), new BigDecimal("25.50"));
        when(orderMapper.findByPublicId(any())).thenReturn(filled);
        when(orderMapper.cancelIfNew(any())).thenReturn(0);

        assertThatThrownBy(() -> orderService.cancelOrder(UUID.randomUUID()))
                .isInstanceOf(OrderNotCancellableException.class);
    }
}