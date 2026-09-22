# Person 4 — Authentication Security & Security Review

**Tickets:** SEC4-628 (One Answer for Every Failed Login), SEC4-631 (OWASP
Security Review, Committed)
**Branch:** `sprint-08-person-4`, based on `sprint-08` after Person 1's
SEC4-623/624 merged — this ticket edits Person 1's `AuthService.login`
directly
**Commits:** 8 — `git log --oneline sprint-08 ^sprint-07 --grep='Person 4\]'`
(4 for SEC4-628, 4 for SEC4-631)

## SEC4-628: one answer for every failed login

Requirement: an unknown username and a wrong password get the same
status, the same body, and *comparable timing* — the last of which is the
one Person 1's original `login()` failed by accident: it returned
immediately when the username lookup missed, skipping the argon2id
verification a wrong-password path paid for. Plus: a login throttle with a
documented cooldown and attempt count, and the throttle must not itself
become a new oracle.

| File | Role |
|---|---|
| `src/auth/login-throttle.service.ts` (+ spec) | Fixed-window, **5 attempts / 5-minute window, keyed by caller IP** (not username — an attacker sprays different unknown usernames from one address to duck a per-username limit). In-memory, per-instance; documented as a residual risk, not a gap, in the security review |
| `src/auth/auth.service.ts` (edit) | `login()` now: checks the throttle first; looks up the user; verifies the supplied password against the user's **real hash if found, or the fixed dummy hash (`getDummyPasswordHash()`, Person 1's SEC4-624) if not** — so both paths call `passwordHasher.verify()` exactly once, at the same cost; records success/failure against the throttle; logs a structured event for every branch (added during the SEC4-631 pass below, once the review surfaced that nothing was logged) |
| `src/auth/auth.controller.ts` (edit) | `login()` now takes `@Ip()` and passes it through as the throttle key |
| `src/auth/auth.service.spec.ts` (edit) | New tests: unknown user verifies against the dummy hash (not an early return); known user verifies against their real hash; a caller over the throttle limit is refused without the repository even being queried |
| `README.md` (edit) | Records the cooldown (5 min) and attempt count (5) per the ticket's explicit task |

## SEC4-631: the OWASP security review

Requirement: copy `security-review/TEMPLATE.md`, fill in a finding *and* a
disposition for every category, name the copy in the sprint README. A
finding of "none" needs the specific check behind it; a disposition of
"accepted" needs the residual risk stated.

| File | Role |
|---|---|
| `security-review/TEMPLATE.md` | Verbatim copy from the requirements repo |
| `security-review/team4-auth-service-review.md` | Team 4's filled copy — see below |

**Two real findings surfaced while writing this, and were fixed as part of
the same ticket rather than left as accepted risk:**

1. **A05, container ran as root.** `node:20-alpine`'s default. Fixed:
   `Dockerfile` now runs `USER node` before `CMD` — verified with a real
   `docker build` and a `process.getuid()` check inside the built image
   (returned `1000`, not `0`).
2. **A09, nothing was logged.** Before this pass, a login failure, a login
   success, a throttle trip, a refresh, and a detected refresh-token
   replay all happened silently — the exact gap the sprint README's A09
   guidance warns about ("a service that logs nothing usable cannot answer
   the questions asked after an incident"). Fixed: `AuthService` and
   `RefreshTokenService` (Person 3's file, edited here) now log one
   structured event per case, naming only a username/userId/accountId/
   callerId — never a credential, redacted or not, through Nest's
   `Logger`, which `main.ts` backs with Person 1's `RedactingLogger`.

Every other row (A01, A02, A03, A04, A06, A07) has a finding and a
disposition backed by a specific file or test — see the review itself
rather than a summary here; it's written to be read on its own.

**A04's JWT_SECRET-rotation cell is left open** in this ticket's version
of the file — that's Person 5's SEC4-629 decision, filled in on top of
this document in a later commit on `sprint-08-person-5`.

## Files touched

```
A  sprint-08-auth-service/src/auth/login-throttle.service.ts
A  sprint-08-auth-service/src/auth/login-throttle.service.spec.ts
A  sprint-08-auth-service/security-review/TEMPLATE.md
A  sprint-08-auth-service/security-review/team4-auth-service-review.md
M  sprint-08-auth-service/src/auth/auth.service.ts
M  sprint-08-auth-service/src/auth/auth.service.spec.ts
M  sprint-08-auth-service/src/auth/auth.controller.ts
M  sprint-08-auth-service/src/auth/auth.module.ts        (register LoginThrottleService)
M  sprint-08-auth-service/src/tokens/refresh-token.service.ts   (A09 logging)
M  sprint-08-auth-service/Dockerfile                      (A05 non-root user)
M  sprint-08-auth-service/README.md
```

## Verifying this work independently

```bash
cd sprint-08-auth-service
npx jest login-throttle auth.service
docker build -t auth-service-check .
docker run --rm auth-service-check node -e "console.log(process.getuid())"   # expect 1000, not 0
```
