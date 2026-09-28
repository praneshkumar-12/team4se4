import { Injectable, computed } from '@angular/core';
import { TokenStore } from './token-store';

/**
 * The JWT claims contract is normative — see `contracts/auth-api.yaml`'s
 * `info.description`. A token that doesn't carry exactly these claims
 * breaks the Trade REST API's own authorisation check, not just this
 * decode.
 */
interface JwtClaims {
  sub: string;
  accountId: number;
  roles: string[];
  iat: number;
  exp: number;
  iss: string;
}

/**
 * Decodes the current account off the token — no signature verification.
 * The browser can't verify a token anyway, and it isn't this app's job to:
 * the Trade REST API verifies on every `/api/v1/**` call. This is purely
 * "what does the token *say*," for read-only display (SEC4-638's
 * read-only account field) — never for an authorisation decision.
 */
@Injectable({ providedIn: 'root' })
export class AccountContext {
  private readonly claims = computed<JwtClaims | null>(() => {
    const token = this.tokenStore.token();
    if (!token) return null;
    return decodeJwtPayload(token);
  });

  readonly accountId = computed<number | null>(() => this.claims()?.accountId ?? null);
  readonly roles = computed<readonly string[]>(() => this.claims()?.roles ?? []);

  constructor(private readonly tokenStore: TokenStore) {}
}

function decodeJwtPayload(token: string): JwtClaims | null {
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json) as JwtClaims;
  } catch {
    return null;
  }
}
