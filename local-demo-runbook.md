# Running the Sprint 7 stack locally and demonstrating no double debit

This is the full, step-by-step record of standing up the whole
`sprint-07`-branch stack on a local machine (Docker Desktop on Windows,
Git Bash) and running the SEC4-616 duplicate-delivery demo end-to-end,
including every gotcha hit along the way. `design/demo-duplicate-delivery.md`
is the short abstract script for the demo itself; this document is the
"start from nothing and actually get there" version, with real commands.

## 0. Prerequisites

- Docker Desktop running.
- JDK 21 available (the executor's `pom.xml` enforces it). If your default
  `java` is older, point `JAVA_HOME` at a JDK 21 install for every command
  below, e.g.:
  ```bash
  export JAVA_HOME="/c/Users/<you>/.jdks/ms-21.0.12.1"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```
- `.env` created from `.env.example` at the repo root, with a real
  `FAUXNANCE_API_KEY` if you want to see an actual **fill** (a placeholder
  key still lets the whole demo run — every order just resolves as
  `REJECTED`/`NO_PRICE_AVAILABLE` instead, which still proves the
  no-double-processing guarantee, just without cash moving).

## 1. Bring up Postgres and Kafka

```bash
cd /g/team4se4
docker-compose up -d postgres kafka
```

Wait for both to report healthy:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
```

**Known gotcha**: the `apache/kafka:3.7.1` image does not put `/opt/kafka/bin`
on `PATH`, so a bare `kafka-broker-api-versions.sh`/`kafka-topics.sh` call
(as originally written in `docker-compose.yml`'s healthcheck and in
`scripts/kafka-init.sh`) fails with "executable file not found in $PATH".
Both were fixed to call the full path
(`/opt/kafka/bin/kafka-broker-api-versions.sh`, `/opt/kafka/bin/kafka-topics.sh`).
If you're on a machine where these still say `unhealthy`, check that fix
made it into your checkout.

## 2. Create the Kafka topics

`KAFKA_AUTO_CREATE_TOPICS_ENABLE=false` is deliberate (see `design/kafka.md`),
so nothing creates the topics for you:

```bash
MSYS_NO_PATHCONV=1 bash scripts/kafka-init.sh
```

(`MSYS_NO_PATHCONV=1` is a Git-Bash-on-Windows-only thing — it stops Git
Bash from mangling the `/opt/kafka/bin/...` argument into a Windows path
before it reaches `docker compose exec`. Not needed on macOS/Linux.)

This creates `orders`, `orders.DLT`, `trade-events`, `trade-events.DLT`,
`market-data`, `market-data.DLT` with the partition counts/retention from
`contracts/kafka-topics.md`. Safe to re-run (`--if-not-exists`).

## 3. Bring up the Trade REST API

```bash
docker-compose up -d --build trade-api
```

**Known gotcha**: if Postgres has an old volume from before this branch's
Liquibase changes, you'll see a `ValidationFailedException: changesets
check sum` error in `trade-api`'s logs — the recorded checksum for
`003-demo-seed` no longer matches the file. For a local disposable demo
database, the fix is to reset it:

```bash
docker-compose down
docker volume rm team4se4_postgres-data
docker-compose up -d postgres kafka   # recreate fresh, then redo step 2
docker-compose up -d --build trade-api
```

Wait for it healthy the same way as step 1. Once up, the demo seed
(`003-demo-seed.sql`, loaded because `.env`'s `LIQUIBASE_CONTEXTS=demo`)
gives you:
- Account 1 (`ACC-10001`), ACTIVE, ₹100,000.00 cash.
- Account 2 (`ACC-10002`), SUSPENDED.
- Account 3 (`ACC-10003`), CLOSED.
- Instruments `TCS` and `INFY` (both NSE-listed — **not** in Fauxnance's
  mock data set, see step 6's note) and `OLDCO` (inactive).

**Known gotcha (host port conflict)**: if something else on your machine is
already listening on port 5432 (a native PostgreSQL install is the common
case — check with `netstat -ano | grep :5432` and `Get-Process -Id <pid>`
in PowerShell), the Docker container's published `5432:5432` mapping can
silently lose the race for that port, and anything connecting to
`localhost:5432` from the host (like the executor in step 5, which is *not*
in `docker-compose` and runs as a local process) ends up talking to the
wrong database entirely — usually surfacing as a password-authentication
error, or "table does not exist" if the other Postgres has no schema at
all. `trade-api` itself is unaffected (it talks to Postgres over Docker's
internal network, not through the published host port).

If you hit this and can't stop the conflicting service, remap the
container's host port instead, without touching the tracked
`docker-compose.yml` — add a `docker-compose.override.yml` (Compose merges
it automatically, and it's local/untracked — delete it when you're done, or
add it to `.gitignore` if you'll need it repeatedly):

```yaml
services:
  postgres:
    ports:
      - "5433:5432"
```

```bash
docker-compose up -d postgres   # recreates just this container with the extra port
```

Then use `localhost:5433` (not `5432`) for every host-side Postgres
connection below.

## 4. Run the Trade Executor

It's a local process, not a `docker-compose` service — it reads every
config value from the environment, no properties files (per SEC4-614).
From the repo root:

```bash
cd executor
mvn -q -f ../sprint-05-domain-engine/pom.xml install -DskipTests   # once, or after any domain-module change
mvn -q compile
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/executor-cp.txt

export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export DB_URL=jdbc:postgresql://localhost:5433/trade_db   # or :5432 if you didn't need the port workaround
export DB_USER=postgres
export DB_PASSWORD=postgres_dev_password                  # matches .env's POSTGRES_PASSWORD
export FAUXNANCE_BASE_URL=https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1
export FAUXNANCE_API_KEY=<your real key from .env, or leave the placeholder>
export POLL_INTERVAL_SECONDS=60

CP="target/classes;$(cat /tmp/executor-cp.txt)"
nohup java -Duser.timezone=UTC -cp "$CP" org.leap.executor.Main > /tmp/executor.log 2>&1 &
disown
```

(`-Duser.timezone=UTC` works around a PostgreSQL JDBC driver quirk on some
locales — without it you may see `FATAL: invalid value for parameter
"TimeZone"` on connect.)

Confirm exactly one instance is running before doing anything else — a
stray old instance left over from a previous attempt will sit in the same
`trade-executor` consumer group and race the new one, which is confusing
to debug:

```bash
"$JAVA_HOME/bin/jps" -l
```

If you see more than one `org.leap.executor.Main`, kill the stale ones
(`taskkill //F //PID <pid>` on Windows, `kill <pid>` elsewhere) and leave
exactly one running.

## 5. Mint a demo JWT

There's no auth service yet this sprint (that's Sprint 8), so `trade-api`
just verifies a bearer token against the shared `JWT_SECRET`. Mint one by
hand — HS256, must carry an `accountId` claim and `iss` matching
`JWT_ISSUER` (`auth-service` by default):

```bash
python -c "
import hmac, hashlib, base64, json, time

def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode()

secret = 'dev-only-change-me-this-secret-is-not-for-production-use'.encode()  # .env's JWT_SECRET
header = {'alg': 'HS256', 'typ': 'JWT'}
payload = {
    'iss': 'auth-service',
    'sub': 'demo-user',
    'accountId': 1,
    'roles': ['TRADER'],
    'iat': int(time.time()),
    'exp': int(time.time()) + 3600,
}
h = b64url(json.dumps(header, separators=(',', ':')).encode())
p = b64url(json.dumps(payload, separators=(',', ':')).encode())
sig = b64url(hmac.new(secret, f'{h}.{p}'.encode(), hashlib.sha256).digest())
print(f'{h}.{p}.{sig}')
" > /tmp/token.txt
TOKEN=$(cat /tmp/token.txt)
```

## 6. Place an order

The demo seed's own instruments (`TCS`, `INFY`) are real NSE tickers that
Fauxnance's mock data doesn't recognize (it's US-equity data — confirmed by
querying `GET /quotes/AAPL` successfully but `GET /quotes/TCS` returning
`SYMBOL_NOT_FOUND`). Two options:

**Option A — see a REJECTED/NO_PRICE_AVAILABLE resolution** (works with the seed data as-is, no DB change needed):

```bash
curl -s -X POST http://localhost:8085/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"accountId":1,"symbol":"TCS","side":"BUY","quantity":10,"price":3500.00,"idempotencyKey":"demo-order-001"}'
```

**Option B — see an actual FILL** (needs one extra instrument row, since
none of the seed tickers are in Fauxnance's mock set):

```bash
MSYS_NO_PATHCONV=1 docker exec team4se4-postgres-1 psql -U postgres -d trade_db -c "
INSERT INTO instruments (isin, ticker, name, type, exchange, is_active, currency)
VALUES ('US0378331005', 'AAPL', 'Apple Inc.', 'EQUITY', 'NASDAQ', TRUE, 'USD')
ON CONFLICT DO NOTHING;"

curl -s http://localhost:8085/api/v1/accounts/1/balance -H "Authorization: Bearer $TOKEN"

curl -s -X POST http://localhost:8085/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"accountId":1,"symbol":"AAPL","side":"BUY","quantity":5,"price":340.00,"idempotencyKey":"demo-order-003"}'
```

(Price 340.00 is comfortably above AAPL's real-time ask from Fauxnance at
demo time — `FillRule` fills a BUY when the limit is at or above the ask,
so pick a price you're confident clears it; check `GET /quotes/AAPL`
first if you want to be sure.)

Either way, the response comes back `"status":"NEW"` immediately — the fill
decision happens asynchronously, in the executor, a few hundred
milliseconds later (it's polling Kafka every 500ms).

## 7. Check the outcome

```bash
MSYS_NO_PATHCONV=1 docker exec team4se4-postgres-1 psql -U postgres -d trade_db -c "
SELECT o.order_id, o.status, o.rejection_reason, t.executed_price, t.executed_quantity
FROM orders o LEFT JOIN trades t ON t.order_id = o.order_id
WHERE o.idempotency_key = 'demo-order-003';"

curl -s http://localhost:8085/api/v1/accounts/1/balance -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8085/api/v1/accounts/1/positions -H "Authorization: Bearer $TOKEN"
```

A real run (Option B, AAPL) looked like this:

```
 order_id | status | rejection_reason | executed_price | executed_quantity
----------+--------+-------------------+-----------------+--------------------
        7 | FILLED |                   |      332.45000000 |        5.00000000

{"accountId":1,"cashBalance":98337.75000000,...}
[{"accountId":1,"symbol":"AAPL","quantity":5,"averageCost":332.45000000}, ...]
```

`100000.00 - (5 × 332.45) = 98337.75` — matches exactly.

## 8. The duplicate-delivery demo (SEC4-616)

This is `design/demo-duplicate-delivery.md`'s script, run for real. Capture
the *exact* `ORDER_PLACED` message for the order you just placed and
processed (already terminal — `FILLED` or `REJECTED`):

```bash
MSYS_NO_PATHCONV=1 docker exec team4se4-kafka-1 \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic orders --from-beginning --property print.key=true --timeout-ms 6000 \
  | grep "<the order's public UUID, from the curl response's orderId>"
```

Save the matching line (`<key>\t<json value>`) to a file, e.g.
`/tmp/replay-message.txt`, preserving the tab between key and value.

**Balance and position before the replay:**

```bash
curl -s http://localhost:8085/api/v1/accounts/1/balance -H "Authorization: Bearer $TOKEN"
```

**Replay it, preserving the key:**

```bash
MSYS_NO_PATHCONV=1 docker exec -i team4se4-kafka-1 \
  /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic orders --property "parse.key=true" --property "key.separator=:" \
  < /tmp/replay-message.txt
```

**Balance and position after the replay — must be byte-for-byte identical:**

```bash
curl -s http://localhost:8085/api/v1/accounts/1/balance -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8085/api/v1/accounts/1/positions -H "Authorization: Bearer $TOKEN"
```

**The dedup log line** — grep the executor's own log file (or `docker logs`
if you containerized it) for the guard-1 line `ExecutionService` emits when
it sees a non-`NEW` status:

```bash
grep DUPLICATE_DELIVERY /tmp/executor.log
```

Expect exactly one new line, e.g.:

```
WARNING: DUPLICATE_DELIVERY orderId=7 status=FILLED — already processed, skipping
```

**Confirm exactly one `trade-events` message for this order — never two:**

```bash
MSYS_NO_PATHCONV=1 docker exec team4se4-kafka-1 \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic trade-events --from-beginning --property print.key=true --timeout-ms 6000 \
  | grep '"orderId":7'
```

### A real run (order 7, AAPL, FILLED) looked like this

| | Before replay | After replay |
|---|---|---|
| `cashBalance` | 98337.75000000 | 98337.75000000 |
| AAPL position quantity | 5 | 5 |
| `trade-events` messages for order 7 | 1 | 1 |

New log line on replay: `WARNING: DUPLICATE_DELIVERY orderId=7 status=FILLED — already processed, skipping`

## 9. Tearing down

```bash
# stop the executor
"$JAVA_HOME/bin/jps" -l                 # find its PID
taskkill //F //PID <pid>                # or: kill <pid>

# stop the containers
docker-compose down

# if you added docker-compose.override.yml for the port workaround and don't need it again:
rm docker-compose.override.yml
```
