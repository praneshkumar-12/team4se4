# Trading Platform — Database Schema (v3)

Entity-relationship diagram for the trading platform, generated from the migration files.

## ER Diagram

```mermaid
erDiagram
    CLIENTS ||--|| ACCOUNTS : has
    ACCOUNTS ||--o{ ORDERS : creates
    ACCOUNTS ||--o{ TRANSACTION_HISTORY : has
    ACCOUNTS ||--o{ CLIENT_HOLDINGS : holds
    ORDERS }o--|| INSTRUMENTS : has
    ORDERS ||--|| TRADES : executes
    CLIENT_HOLDINGS }o--|| INSTRUMENTS : has

    CLIENTS {
        attr client_id PK "NOT NULL"
        attr first_name "NOT NULL"
        attr last_name "NOT NULL"
        attr email UK "NOT NULL"
        attr phone
        attr dob
        attr risk_profile "NOT NULL"
    }

    ACCOUNTS {
        attr account_id PK "NOT NULL"
        attr account_reference UK "NOT NULL"
        attr client_id FK,UK "NOT NULL"
        attr currency "NOT NULL"
        attr account_status "NOT NULL"
        attr cash_balance "NOT NULL"
        attr version "NOT NULL"
    }

    INSTRUMENTS {
        attr instrument_id PK "NOT NULL"
        attr isin UK "NOT NULL"
        attr ticker "NOT NULL"
        attr name "NOT NULL"
        attr type "NOT NULL"
        attr exchange "NOT NULL"
        attr is_active "NOT NULL"
        attr currency "NOT NULL"
    }

    ORDERS {
        attr order_id PK "NOT NULL"
        attr idempotency_key UK "NOT NULL"
        attr account_id FK "NOT NULL"
        attr instrument_id FK "NOT NULL"
        attr quantity "NOT NULL"
        attr limit_price
        attr status "NOT NULL"
        attr side "NOT NULL"
        attr order_type "NOT NULL"
    }

    TRANSACTION_HISTORY {
        attr txn_id PK "NOT NULL"
        attr account_id FK "NOT NULL"
        attr amount "NOT NULL"
        attr txn_type "NOT NULL"
        attr currency "NOT NULL"
    }

    TRADES {
        attr trade_id PK "NOT NULL"
        attr order_id FK,UK "NOT NULL"
        attr executed_price "NOT NULL"
        attr executed_quantity "NOT NULL"
        attr executed_at "NOT NULL"
        attr fee "NOT NULL"
    }

    CLIENT_HOLDINGS {
        attr holding_id PK "NOT NULL"
        attr account_id FK "NOT NULL"
        attr instrument_id FK "NOT NULL"
        attr quantity "NOT NULL"
        attr average_cost "NOT NULL"
    }
```

## Relationships

| Relationship | Notes |
|---|---|
| Client **has** account | `accounts.client_id` is both `FK` and `UNIQUE` — each client has exactly one account. |
| Account **creates** orders | `orders.account_id` FK. A trigger blocks inserts unless the account is `ACTIVE`. |
| Account **has** cash transactions | `transaction_history.account_id` FK |
| Account **holds** client holdings | `client_holdings.account_id` FK |
| Order **has** instrument | `orders.instrument_id` FK |
| Order **executes** trade | `trades.order_id` is both `FK` and `UNIQUE` — each order maps to at most one trade. |
| Client holdings **has** instrument | `client_holdings.instrument_id` FK |

## Constraint legend

- **PK** — primary key
- **FK** — foreign key
- **UK** — unique key
- **NOT NULL** — column cannot be null

