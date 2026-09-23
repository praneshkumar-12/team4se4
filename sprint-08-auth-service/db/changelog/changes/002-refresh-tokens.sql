-- ============================================================
-- Create refresh_tokens table (SEC4-627: refresh issuance and rotation)
-- ============================================================
--
-- Stores a hash of the refresh token, never the token itself, so that
-- read access to this table is not session takeover. user_id is a real
-- foreign key to users(id), guaranteed to exist because Liquibase applies
-- this changelog's changesets in file order and 001-users.sql runs
-- first. ON DELETE CASCADE: a refresh token is session state tied to a
-- user, not business data - it should not outlive the user row.

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    revoked_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_refresh_tokens_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
