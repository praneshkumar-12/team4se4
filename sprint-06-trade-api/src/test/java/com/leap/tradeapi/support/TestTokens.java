package com.leap.tradeapi.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Team-owned test fixture that mints tokens following {@code contracts/auth-api.yaml}.
 * It is not a service and is never deployed: Sprint 8 supplies the real issuer,
 * and because this fixture and the production verifier share only the secret and
 * the claim names, that swap is a configuration change.
 */
public final class TestTokens {

    public static final String SECRET = "test-secret-value-at-least-32-characters-long-for-hs256";
    public static final String ISSUER = "auth-service";

    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private static final SecretKey OTHER_KEY =
            Keys.hmacShaKeyFor("a-different-secret-of-the-same-length-forforgery".getBytes(StandardCharsets.UTF_8));

    private TestTokens() {
    }

    /** A valid CUSTOMER access token for the given account, expiring in 15 minutes. */
    public static String validFor(long accountId) {
        return build(accountId, Instant.now().plusSeconds(900), KEY, ISSUER);
    }

    public static String expiredFor(long accountId) {
        return build(accountId, Instant.now().minusSeconds(60), KEY, ISSUER);
    }

    /** Correct claims, but signed with a key the service does not hold. */
    public static String forgedSignatureFor(long accountId) {
        return build(accountId, Instant.now().plusSeconds(900), OTHER_KEY, ISSUER);
    }

    public static String wrongIssuerFor(long accountId) {
        return build(accountId, Instant.now().plusSeconds(900), KEY, "evil-issuer");
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String build(long accountId, Instant expiry, SecretKey key, String issuer) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(issuer)
                .claim("accountId", accountId)
                .claim("roles", List.of("CUSTOMER"))
                .issuedAt(Date.from(now.minusSeconds(1)))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }
}