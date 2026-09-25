-- ============================================================
-- Enforce one user per account (uq_users_account_id)
-- ============================================================
--
-- Registration is now keyed off a client's email (looked up through
-- accounts.client_id -> clients.email, both already UNIQUE), rather than a
-- caller-supplied accountId. That makes "one user per trading account" an
-- invariant the schema should hold, not just a comment in 001-users.sql -
-- two different emails must never be able to register against the same
-- account.
--
-- Drops the old idx_users_account_id first: a UNIQUE constraint creates
-- its own backing index, so keeping both would just be two indexes over
-- the same column.

DROP INDEX IF EXISTS idx_users_account_id;

ALTER TABLE users
    ADD CONSTRAINT uq_users_account_id UNIQUE (account_id);
