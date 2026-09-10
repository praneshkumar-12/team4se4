package com.leap.tradeapi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sprint 6 Trade REST API.
 *
 * <p>Transport and persistence only. Every decision about whether a trade is
 * allowed is made by the Sprint 5 domain package ({@code org.leap.domain}),
 * which is resolved from the local Maven repository by the coordinates in
 * {@code manifest.env}.
 */
@SpringBootApplication
@MapperScan("com.leap.tradeapi.mapper")
public class TradeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeApiApplication.class, args);
    }
}