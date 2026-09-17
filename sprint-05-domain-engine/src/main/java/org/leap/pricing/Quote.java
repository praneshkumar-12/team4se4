package org.leap.pricing;

import java.math.BigDecimal;

public record Quote(String symbol, BigDecimal bid, BigDecimal ask, BigDecimal price) {}