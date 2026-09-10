package com.leap.tradeapi.mapper;

import java.math.BigDecimal;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.leap.domain.Account;

import com.leap.tradeapi.mapper.row.AccountDetailsRow;
import com.leap.tradeapi.mapper.row.BalanceRow;

/**
 * Statements against {@code accounts}. Every value that arrives from outside is
 * bound with {@code #{}}, which the driver sends as a JDBC bind parameter.
 */
@Mapper
public interface AccountMapper {

    /** The account as a Sprint 5 domain object, with the version it holds now. */
    Account findDomainById(@Param("accountId") long accountId);

    AccountDetailsRow findDetails(@Param("accountId") long accountId);

    BalanceRow findBalance(@Param("accountId") long accountId);

    boolean existsById(@Param("accountId") long accountId);

    /**
     * Optimistic-locked write: succeeds only while the row still holds
     * {@code expectedVersion}, and increments it. Returns the affected row
     * count so the caller can tell a lost update (0) from a success (1).
     */
    int updateBalanceWithVersion(
            @Param("accountId") long accountId,
            @Param("cashBalance") BigDecimal cashBalance,
            @Param("expectedVersion") long expectedVersion);
}
