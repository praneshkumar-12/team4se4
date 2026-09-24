-- ============================================================
-- Create users table (SEC4-624: the credential store)
-- ============================================================
--
-- One users table. Every row is a credential (username + password_hash)
-- plus a role and a link to the trading account it may act on:
--
--   users.account_id -> accounts.account_id -> accounts.client_id -> clients.client_id
--
-- accounts.client_id is UNIQUE (see sprint-06-trade-api's Sprint 3
-- schema), so this chain is 1:1:1 - a user is tied to exactly one
-- account, which is tied to exactly one client. That is the
-- "links to client/customer" relationship: it runs through the existing
-- accounts table rather than duplicating a client_id here, because
-- accountId - not clientId - is the identifier the JWT claim set and the
-- Trade REST API's authorisation check actually use
-- (contracts/auth-api.yaml: "the Trade REST API compares [accountId]
-- against the account in the request"). A denormalised client_id on this
-- table would be a second, driftable copy of a fact the accounts row
-- already carries.
--
-- account_id is a foreign key to the Sprint 3 accounts table this service
-- does not own and never creates a row in - registration only links a
-- user to an accountId that must already exist. ON DELETE RESTRICT: an
-- account with a registered user cannot be silently orphaned by a delete
-- in the Trade REST API's schema.
--
-- roles: CUSTOMER (a normal trading user) or ADMIN (an operator), never
-- empty - matching contracts/auth-api.yaml's Role enum and the "always
-- present, never empty" rule on the JWT roles claim. The CHECK constraints
-- enforce the same two values at the database layer, not only in the
-- NestJS DTO validation, matching this schema's existing convention
-- (chk_accounts_status, chk_clients_risk_profile, ...).

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    username VARCHAR(64) NOT NULL,
    password_hash TEXT NOT NULL,
    account_id BIGINT NOT NULL,
    roles TEXT[] NOT NULL DEFAULT ARRAY['CUSTOMER'],

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_username
        UNIQUE (username),

    CONSTRAINT fk_users_account
        FOREIGN KEY (account_id)
        REFERENCES accounts (account_id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_users_roles_not_empty
        CHECK (
            cardinality(roles) > 0
        ),

    CONSTRAINT chk_users_roles_valid
        CHECK (
            roles <@ ARRAY['CUSTOMER', 'ADMIN']::TEXT[]
        )
);

CREATE INDEX idx_users_account_id ON users (account_id);
