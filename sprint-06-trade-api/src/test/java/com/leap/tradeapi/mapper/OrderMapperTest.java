package com.leap.tradeapi.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.leap.domain.enums.OrderStatus;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.leap.tradeapi.mapper.row.NewOrder;
import com.leap.tradeapi.mapper.row.OrderHistoryRow;
import com.leap.tradeapi.mapper.row.OrderRow;

// The Sprint 3 schema is not on the embedded database, so the additive Liquibase
// changelog cannot run here. The @Sql scripts build the H2 schema for the slice.
@MybatisTest
@TestPropertySource(properties = { "spring.liquibase.enabled=false", "spring.sql.init.mode=never" })
@Sql(scripts = { "/schema.sql", "/seed.sql" })
class OrderMapperTest {

    @Autowired
    private OrderMapper orderMapper;

    private NewOrder newOrder(String idempotencyKey, String status) {
        return new NewOrder(UUID.randomUUID().toString(), idempotencyKey, 1L, 1L,
                "BUY", "LIMIT", new BigDecimal("100"), new BigDecimal("25.50"), status);
    }

    @Test
    void an_inserted_order_row_is_retrievable_and_reports_its_generated_key() {
        NewOrder order = newOrder("idem-key-0001", "FILLED");

        int affected = orderMapper.insert(order);

        assertThat(affected).isEqualTo(1);
        assertThat(order.getOrderId()).isNotNull();

        OrderRow found = orderMapper.findByPublicId(order.getPublicId());
        assertThat(found.accountId()).isEqualTo(1L);
        assertThat(found.symbol()).isEqualTo("ACME");
        assertThat(found.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(found.quantity()).isEqualByComparingTo("100");
    }

    @Test
    void a_reused_idempotency_key_surfaces_the_constraint_violation_rather_than_swallowing_it() {
        orderMapper.insert(newOrder("idem-key-dup1", "FILLED"));

        assertThatThrownBy(() -> orderMapper.insert(newOrder("idem-key-dup1", "FILLED")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void the_guarded_cancel_transition_reports_the_affected_row_count() {
        NewOrder working = newOrder("idem-key-new1", "NEW");
        orderMapper.insert(working);
        NewOrder done = newOrder("idem-key-fill1", "FILLED");
        orderMapper.insert(done);

        assertThat(orderMapper.cancelIfNew(working.getPublicId())).isEqualTo(1);
        assertThat(orderMapper.cancelIfNew(done.getPublicId())).isZero();
        assertThat(orderMapper.cancelIfNew(working.getPublicId())).isZero();
    }

    @Test
    void order_history_comes_back_newest_first_and_can_be_filtered_by_status() {
        orderMapper.insert(newOrder("idem-key-h1", "FILLED"));
        orderMapper.insert(newOrder("idem-key-h2", "CANCELLED"));

        List<OrderHistoryRow> all = orderMapper.findHistory(1L, null, null, null);
        assertThat(all).hasSize(2);
        assertThat(all.get(0).idempotencyKey()).isEqualTo("idem-key-h2");

        List<OrderHistoryRow> filled = orderMapper.findHistory(1L, "FILLED", null, null);
        assertThat(filled).extracting(OrderHistoryRow::idempotencyKey).containsExactly("idem-key-h1");
    }

    @Test
    void order_history_binds_the_timestamp_window_as_parameters() {
        orderMapper.insert(newOrder("idem-key-t1", "FILLED"));

        List<OrderHistoryRow> future = orderMapper.findHistory(
                1L, null, Instant.now().plusSeconds(3600), null);
        assertThat(future).isEmpty();

        List<OrderHistoryRow> past = orderMapper.findHistory(
                1L, null, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));
        assertThat(past).hasSize(1);
    }
}
