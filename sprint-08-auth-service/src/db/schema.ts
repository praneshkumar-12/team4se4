/**
 * The credential store's bootstrap: idempotent DDL, applied in order at
 * startup (see db.module.ts). "Migration or bootstrap" per SEC4-624 - this
 * service chose a bootstrap because its schema is small and additive, not
 * because migrations are the wrong tool in general. Every statement is
 * CREATE ... IF NOT EXISTS, so running it against a database this service
 * already built is a no-op, matching the same precondition-guard idea the
 * Trade REST API's Liquibase changesets use.
 *
 * Ordering matters: a table with a foreign key must be listed after the
 * table it references. This array is that order.
 */
export const MIGRATIONS: readonly string[] = [
  // SEC4-627: refresh tokens. user_id is not an FK to users(id) here - this
  // service's own bootstrap does not own migration-ordering guarantees the
  // way Liquibase does for the Sprint 3 schema, and the only writer of this
  // table (RefreshTokenService) only ever inserts a user_id that came back
  // from the users repository, so application-level integrity is enough.
  `CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
  )`,
  `CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id)`,
];
