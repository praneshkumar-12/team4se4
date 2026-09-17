package org.leap.pricing;

import java.math.BigDecimal;

/** A live market quote for one symbol, as returned by Fauxnance. */
public record Quote(String symbol, BigDecimal bid, BigDecimal ask, BigDecimal price) {}
