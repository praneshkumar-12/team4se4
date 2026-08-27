-- ============================================================
-- Description: Create application triggers and indexes
-- ============================================================


-- ============================================================
-- 1. Automatically update updated_at
-- ============================================================

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;

    RETURN NEW;
END;
$$;


CREATE TRIGGER trg_clients_updated_at
BEFORE UPDATE ON clients
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();


CREATE TRIGGER trg_accounts_updated_at
BEFORE UPDATE ON accounts
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();


CREATE TRIGGER trg_instruments_updated_at
BEFORE UPDATE ON instruments
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();


CREATE TRIGGER trg_orders_updated_at
BEFORE UPDATE ON orders
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();


CREATE TRIGGER trg_transaction_history_updated_at
BEFORE UPDATE ON transaction_history
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();


CREATE TRIGGER trg_trades_updated_at
BEFORE UPDATE ON trades
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();


CREATE TRIGGER trg_client_holdings_updated_at
BEFORE UPDATE ON client_holdings
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();


-- ============================================================
-- 2. Validate account status transitions
--
-- Allowed:
--   ACTIVE    -> ACTIVE
--   ACTIVE    -> SUSPENDED
--   ACTIVE    -> CLOSED
--   SUSPENDED -> SUSPENDED
--   SUSPENDED -> ACTIVE
--   SUSPENDED -> CLOSED
--   CLOSED    -> CLOSED
--
-- Not allowed:
--   CLOSED    -> ACTIVE
--   CLOSED    -> SUSPENDED
-- ============================================================

CREATE OR REPLACE FUNCTION validate_account_status_transition()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    -- No status change
    IF OLD.account_status = NEW.account_status THEN
        RETURN NEW;
    END IF;


    -- CLOSED is a terminal state.
    -- Once an account is closed, it cannot be reopened
    -- or moved to another status.
    IF OLD.account_status = 'CLOSED' THEN
        RAISE EXCEPTION
            'Closed account % cannot change status from CLOSED to %',
            OLD.account_id,
            NEW.account_status;
    END IF;


    -- ACTIVE can only transition to SUSPENDED or CLOSED.
    IF OLD.account_status = 'ACTIVE'
       AND NEW.account_status NOT IN ('SUSPENDED', 'CLOSED') THEN

        RAISE EXCEPTION
            'Invalid account status transition for account %: % -> %',
            OLD.account_id,
            OLD.account_status,
            NEW.account_status;
    END IF;


    -- SUSPENDED can transition back to ACTIVE
    -- or permanently to CLOSED.
    IF OLD.account_status = 'SUSPENDED'
       AND NEW.account_status NOT IN ('ACTIVE', 'CLOSED') THEN

        RAISE EXCEPTION
            'Invalid account status transition for account %: % -> %',
            OLD.account_id,
            OLD.account_status,
            NEW.account_status;
    END IF;


    RETURN NEW;
END;
$$;


CREATE TRIGGER trg_validate_account_status_transition
BEFORE UPDATE OF account_status ON accounts
FOR EACH ROW
EXECUTE FUNCTION validate_account_status_transition();


-- ============================================================
-- 3. Prevent terminal order status changes
-- ============================================================

CREATE OR REPLACE FUNCTION prevent_terminal_order_update()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status IN (
        'FILLED',
        'CANCELLED',
        'REJECTED'
    )
    AND NEW.status <> OLD.status THEN

        RAISE EXCEPTION
            'Terminal order % cannot change from status %',
            OLD.order_id,
            OLD.status;
    END IF;


    RETURN NEW;
END;
$$;


CREATE TRIGGER trg_prevent_terminal_order_update
BEFORE UPDATE OF status ON orders
FOR EACH ROW
EXECUTE FUNCTION prevent_terminal_order_update();


-- ============================================================
-- 4. Check account status before inserting an order
-- ============================================================

CREATE OR REPLACE FUNCTION check_order_account_active()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    current_status VARCHAR(20);
BEGIN
    SELECT account_status
    INTO current_status
    FROM accounts
    WHERE account_id = NEW.account_id;


    IF current_status IS NULL THEN
        RAISE EXCEPTION
            'Account % does not exist',
            NEW.account_id;
    END IF;


    IF current_status <> 'ACTIVE' THEN
        RAISE EXCEPTION
            'Orders cannot be placed against account % because it is %',
            NEW.account_id,
            current_status;
    END IF;


    RETURN NEW;
END;
$$;


CREATE TRIGGER trg_check_order_account_active
BEFORE INSERT ON orders
FOR EACH ROW
EXECUTE FUNCTION check_order_account_active();


-- ============================================================
-- 5. Indexes
-- ============================================================

-- Query 1:
-- All open orders for one account, newest first

CREATE INDEX idx_orders_open_account_created
ON orders (account_id, created_at DESC)
WHERE status = 'NEW';


-- Query 2:
-- Last 50 orders for one account in any state, newest first

CREATE INDEX idx_orders_account_created
ON orders (account_id, created_at DESC);


-- Query 3:
-- Everything one account currently holds

CREATE INDEX idx_client_holdings_account
ON client_holdings (account_id);


-- Query 4:
-- Orders created since a given timestamp

CREATE INDEX idx_orders_created_at
ON orders (created_at);


-- Query 6:
-- Filled orders by execution/order history

CREATE INDEX idx_trades_executed_at
ON trades (executed_at);