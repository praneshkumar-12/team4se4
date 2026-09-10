package com.leap.tradeapi.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.leap.domain.Account;
import org.leap.domain.enums.AccountStatus;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.leap.tradeapi.mapper.row.AccountDetailsRow;
import com.leap.tradeapi.mapper.row.PositionRow;

// The Sprint 3 schema is not on the embedded database, so the additive Liquibase
// changelog cannot run here. The @Sql scripts build the H2 schema for the slice.
@MybatisTest
@TestPropertySource(properties = { "spring.liquibase.enabled=false", "spring.sql.init.mode=never" })
@Sql(scripts = { "/schema.sql", "/seed.sql" })
class AccountMapperTest {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private PositionMapper positionMapper;

    @Test
    void reads_account_details_shaped_for_the_contract() {
        AccountDetailsRow row = accountMapper.findDetails(1L);

        assertThat(row.id()).isEqualTo(1L);
        assertThat(row.accountReference()).isEqualTo("ACC-000001");
        assertThat(row.holderName()).isEqualTo("Priya Menon");
        assertThat(row.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(row.version()).isZero();
        assertThat(row.cashBalance()).isEqualByComparingTo("25000.00");
    }

    @Test
    void reads_the_account_as_a_domain_object_with_its_current_version() {
        Account account = accountMapper.findDomainById(1L);

        assertThat(account.getAccountReference()).isEqualTo("ACC-000001");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getVersion()).isZero();
        assertThat(account.getCashBalance()).isEqualByComparingTo("25000.00");
    }

    @Test
    void existsById_distinguishes_a_known_account_from_an_unknown_one() {
        assertThat(accountMapper.existsById(1L)).isTrue();
        assertThat(accountMapper.existsById(999L)).isFalse();
    }

    @Test
    void optimistic_locked_update_affects_one_row_then_zero_on_a_stale_version() {
        int first = accountMapper.updateBalanceWithVersion(1L, new BigDecimal("22450.00"), 0L);
        int second = accountMapper.updateBalanceWithVersion(1L, new BigDecimal("10000.00"), 0L);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();

        Account after = accountMapper.findDomainById(1L);
        assertThat(after.getVersion()).isEqualTo(1L);
        assertThat(after.getCashBalance()).isEqualByComparingTo("22450.00");
    }

    @Test
    void reads_positions_for_the_account_excluding_zero_quantity() {
        List<PositionRow> positions = positionMapper.findByAccount(1L);

        assertThat(positions).hasSize(1);
        assertThat(positions.get(0).symbol()).isEqualTo("ACME");
        assertThat(positions.get(0).quantity()).isEqualByComparingTo("40");
    }
}
