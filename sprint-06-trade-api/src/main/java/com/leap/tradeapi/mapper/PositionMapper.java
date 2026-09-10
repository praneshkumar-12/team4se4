package com.leap.tradeapi.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.leap.domain.Position;

import com.leap.tradeapi.mapper.row.PositionRow;

/** Statements against {@code client_holdings}. */
@Mapper
public interface PositionMapper {

    Position findDomain(
            @Param("accountId") long accountId,
            @Param("instrumentId") long instrumentId);

    int insertPosition(
            @Param("accountId") long accountId,
            @Param("instrumentId") long instrumentId,
            @Param("quantity") BigDecimal quantity,
            @Param("averageCost") BigDecimal averageCost);

    int updatePosition(
            @Param("accountId") long accountId,
            @Param("instrumentId") long instrumentId,
            @Param("quantity") BigDecimal quantity,
            @Param("averageCost") BigDecimal averageCost);

    /** Net holdings for the account, zero-quantity positions excluded. */
    List<PositionRow> findByAccount(@Param("accountId") long accountId);
}
