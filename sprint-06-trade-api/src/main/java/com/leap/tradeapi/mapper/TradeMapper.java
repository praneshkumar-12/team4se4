package com.leap.tradeapi.mapper;

import java.math.BigDecimal;
import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Statements against {@code trades}. One order becomes one trade when filled. */
@Mapper
public interface TradeMapper {

    int insert(
            @Param("orderId") long orderId,
            @Param("executedPrice") BigDecimal executedPrice,
            @Param("executedQuantity") BigDecimal executedQuantity,
            @Param("executedAt") Instant executedAt);
}
