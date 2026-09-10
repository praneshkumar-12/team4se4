package com.leap.tradeapi.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.leap.domain.Instrument;

/** Statements against {@code instruments}. */
@Mapper
public interface InstrumentMapper {

    /**
     * The tradable instrument for a symbol, or null when it is unknown or
     * delisted. The Sprint 5 domain treats both as {@code INS-404}.
     */
    Instrument findTradableByTicker(@Param("ticker") String ticker);
}
