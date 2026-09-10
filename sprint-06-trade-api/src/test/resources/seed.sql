-- Deterministic fixture for slice and integration tests.
--
--   account 1  ACTIVE    holder "Priya Menon"   cash 25000.00  version 0
--   account 2  SUSPENDED holder "Arun Kumar"    cash  1000.00  version 0
--   instrument ACME (active), DELISTED (inactive)
--   account 1 holds 40 ACME at average cost 20.00

INSERT INTO clients (first_name, last_name, email, risk_profile)
VALUES ('Priya', 'Menon', 'priya.menon@example.com', 'LOW'),
       ('Arun',  'Kumar', 'arun.kumar@example.com',  'MEDIUM');

INSERT INTO accounts (account_reference, client_id, currency, account_status, cash_balance, version)
VALUES ('ACC-000001', 1, 'USD', 'ACTIVE',    25000.00, 0),
       ('ACC-000002', 2, 'USD', 'SUSPENDED',  1000.00, 0);

INSERT INTO instruments (isin, ticker, name, type, exchange, is_active, currency)
VALUES ('US0000000001', 'ACME',     'Acme Corp',        'EQUITY', 'NASDAQ', TRUE,  'USD'),
       ('US0000000002', 'DELISTED', 'Delisted Holdings','EQUITY', 'NASDAQ', FALSE, 'USD');

INSERT INTO client_holdings (account_id, instrument_id, quantity, average_cost)
VALUES (1, 1, 40, 20.00);
