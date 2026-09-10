package com.leap.tradeapi.auth;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * The verified identity of the current caller, populated by {@link JwtAuthFilter}
 * once per request and read by the service layer where the account key is known.
 *
 * <p>It holds token claims, not a servlet type, so the service depends on the
 * token contract and not on the transport. When Sprint 8 swaps the auth stub for
 * the real service, nothing here changes.
 */
@Component
@RequestScope
public class CallerContext {

    private Long accountId;
    private List<String> roles = List.of();

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /** True when the token's {@code accountId} claim addresses this account. */
    public boolean reaches(long targetAccountId) {
        return accountId != null && accountId == targetAccountId;
    }
}