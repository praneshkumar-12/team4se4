package com.leap.tradeapi.auth;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Verifies a bearer token against the shared HS256 secret.
 *
 * <p>Order matters: the signature, the expiry and the algorithm the token asks
 * for are all checked by {@code parseSignedClaims} before any claim is read.
 * {@code verifyWith(key)} pins the algorithm to the one the key implies (HS256),
 * so a token asking for {@code none} or {@code RS256} is rejected, not trusted.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer}") String issuer) {

        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    /**
     * @return the verified claims
     * @throws io.jsonwebtoken.JwtException if the token is missing a valid
     *         signature, is expired, asks for the wrong algorithm, or was not
     *         issued by the configured issuer
     */
    public VerifiedToken verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Number accountId = claims.get("accountId", Number.class);
        if (accountId == null) {
            throw new io.jsonwebtoken.JwtException("Token carries no accountId claim");
        }

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);

        return new VerifiedToken(claims.getSubject(), accountId.longValue(), roles);
    }

    /** The subset of verified claims the platform authorises against. */
    public record VerifiedToken(String subject, long accountId, List<String> roles) {
    }
}