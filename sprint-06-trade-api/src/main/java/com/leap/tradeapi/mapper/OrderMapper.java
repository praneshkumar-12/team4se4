package com.leap.tradeapi.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.leap.tradeapi.mapper.row.NewOrder;
import com.leap.tradeapi.mapper.row.OrderHistoryRow;
import com.leap.tradeapi.mapper.row.OrderRow;

/** Statements against {@code orders}. */
@Mapper
public interface OrderMapper {

    /** Inserts the order and writes the generated key back onto {@code order}. */
    int insert(NewOrder order);

    OrderRow findByPublicId(@Param("publicId") String publicId);

    /**
     * The guarded cancel transition, in one statement: the row moves to
     * {@code CANCELLED} only while it is still {@code NEW}. Zero rows affected
     * means it was not cancellable.
     */
    int cancelIfNew(@Param("publicId") String publicId);

    /**
     * Order history, newest first. {@code status}, {@code from} and {@code to}
     * are optional bind parameters; the ORDER BY is a fixed constant, not a
     * parameter and not interpolated.
     */
    List<OrderHistoryRow> findHistory(
            @Param("accountId") long accountId,
            @Param("status") String status,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
