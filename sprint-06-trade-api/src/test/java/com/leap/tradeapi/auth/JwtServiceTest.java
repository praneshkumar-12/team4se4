package com.leap.tradeapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.leap.tradeapi.support.TestTokens;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(TestTokens.SECRET, TestTokens.ISSUER);

    @Test
    void verifies_a_well_formed_token_and_returns_the_account_claim() {
        JwtService.VerifiedToken token = jwtService.verify(TestTokens.validFor(7L));

        assertThat(token.accountId()).isEqualTo(7L);
        assertThat(token.roles()).containsExactly("CUSTOMER");
    }

    @Test
    void rejects_an_expired_token() {
        assertThatThrownBy(() -> jwtService.verify(TestTokens.expiredFor(1L)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejects_a_forged_signature() {
        assertThatThrownBy(() -> jwtService.verify(TestTokens.forgedSignatureFor(1L)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejects_a_token_from_the_wrong_issuer() {
        assertThatThrownBy(() -> jwtService.verify(TestTokens.wrongIssuerFor(1L)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejects_an_unsigned_token_before_reading_any_claim() {
        String unsigned = Jwts.builder()
                .issuer(TestTokens.ISSUER)
                .claim("accountId", 1)
                .claim("roles", List.of("CUSTOMER"))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .compact();

        assertThatThrownBy(() -> jwtService.verify(unsigned))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejects_a_token_signed_with_a_different_algorithm_family() {
        // HS512 token: a valid signature, but not the algorithm the configured key implies.
        String hs512 = Jwts.builder()
                .issuer(TestTokens.ISSUER)
                .claim("accountId", 1)
                .claim("roles", List.of("CUSTOMER"))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(
                        "another-secret-that-is-definitely-long-enough-for-hs512-usage-64b".getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> jwtService.verify(hs512))
                .isInstanceOf(JwtException.class);
    }
}