# Person 2 — JWT & Access Tokens

**Ticket:** SEC4-625 (Access Token Issuance and the Exact Claim Set)
**Branch:** `sprint-08-person-2`, based on `sprint-08` right after Person
5's scaffold merged — deliberately first, since this ticket's interface is
what everyone else builds against
**Commits:** 3 — `git log --oneline sprint-08 ^sprint-07 --grep='Person 2\]'`

## What the ticket required and what was built

Sign an access token HS256 with `JWT_SECRET`, carrying **exactly** `sub`,
`accountId`, `roles`, `iat`, `exp`, plus the contract's `iss`. Fifteen-minute
expiry. No claim outside that set. Don't require a particular issuer value
in any consumer.

Built as `src/tokens/token.service.ts`:

```ts
export interface TokenSubject {
  id: string;        // becomes `sub`
  accountId: number;
  roles: string[];
}

export class TokenService {
  static readonly ACCESS_TOKEN_TTL_SECONDS = 15 * 60;
  createAccessToken(user: TokenSubject): string { ... }
}
```

This is the interface Person 1's `AuthService` (login/refresh) and Person
3's `RefreshTokenService` depend on — published in its own commit before
either of those existed, so they could be written against it directly
rather than against a mock.

Key decisions:
- **Algorithm and expiry are literal options** on the `jwt.sign()` call
  (`algorithm: "HS256"`, `expiresIn: 900`), not something a caller can
  influence.
- **`JWT_SECRET` has no default** (`ConfigService.getOrThrow`) — the
  service fails to start rather than sign with a value nobody chose.
- **`iss` reads from `JWT_ISSUER`** with the contract's own default
  (`auth-service`) rather than a value this service requires — per the
  ticket's "do not require a particular issuer value in any consumer."

## Files touched

```
A  sprint-08-auth-service/src/tokens/token.service.ts
A  sprint-08-auth-service/src/tokens/token.service.spec.ts
A  sprint-08-auth-service/src/tokens/tokens.module.ts
M  sprint-08-auth-service/src/app.module.ts   (register TokensModule)
```

## Unit tests (`token.service.spec.ts`) — the three named paths

1. **A token is issued with the contract claims and expiry** — decodes the
   token and checks `sub`/`accountId`/`roles`/`iss`, and that
   `exp - iat === 900`.
2. **The token signature verifies with the service key** — `jwt.verify`
   succeeds with the configured secret and fails with a different one.
3. **No claim outside the contract set is present** — asserts
   `Object.keys(decoded)` is exactly
   `["accountId", "exp", "iat", "iss", "roles", "sub"]`.

A fourth test pins that `JWT_ISSUER` is configurable, confirming this
service's own code never hard-requires a particular issuer.

## Verifying this work independently

```bash
cd sprint-08-auth-service
npx jest token.service
```
