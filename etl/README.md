# Analytics ETL

Incremental load from the Postgres operational schema into a DuckDB star
schema (`dim_date`, `dim_instrument`, `dim_account`, `fact_trades`) matching
`contracts/analytics-schema.sql` at the repo root. One DuckDB file, no
server.

## Setup

```bash
cd etl
python -m venv .venv
.venv/Scripts/activate        # .venv/bin/activate on macOS/Linux
pip install -e ".[dev]"
```

Reads Postgres connection info from the environment — the same
`POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` already in the repo
root's `.env.example`, plus `POSTGRES_HOST` / `POSTGRES_PORT` (default
`localhost:5432`, matching the port `docker-compose.yml` exposes). The
DuckDB file location is `DUCKDB_PATH` (default `data/warehouse.duckdb`,
relative to `etl/`).

## Commands

Run from `etl/` with the venv active:

```bash
python -m etl.cli dims   # dim_date (full range) + dim_instrument + dim_account
python -m etl.cli fact   # fact_trades only, incremental off the watermark
python -m etl.cli full   # all four, in dependency order (dims, then fact)
```

`dims` must be run at least once before `fact` — `fact_trades` rows resolve
their account/instrument/date keys by lookup and are dead-lettered, not
faked with a placeholder row, if a dimension hasn't been loaded yet.

## Idempotency

Re-running `full` (or `fact`) twice against unchanged Postgres data adds no
new rows: the watermark (`etl_watermark` table, on `orders.created_at`)
stops re-reading old orders, and the merge on `fact_trades.source_order_id`
stops a rare re-read from duplicating a row.

## Dead letters

A row that fails validation (unresolved dimension key, non-positive
quantity/price, invalid `side`/`status`) is written to the `dead_letter`
table with its reason and the batch id it came from, and the load continues
past it — nothing is silently dropped.

## Tests

```bash
pytest
```

## SonarQube / DevSecOps (SEC4-620)

`sonar-project.properties` in this directory (`sonar.projectKey=analytics-pipeline`,
sources `src`, tests `tests`) is the scan config for this project. To run it
locally against the `sonarqube` service in the repo root's `docker-compose.yml`:

```bash
docker compose up -d sonarqube
# open http://localhost:9000, sign in admin/admin, change the password,
# generate a token, then:
export SONAR_TOKEN=...          # never commit this
docker run --rm --network=host -e SONAR_HOST_URL=http://localhost:9000 \
  -e SONAR_TOKEN="$SONAR_TOKEN" -v "$(pwd):/usr/src" \
  sonarsource/sonar-scanner-cli
```

Local dependency scan (run from `etl/` with the venv active):

```bash
pip install pip-audit
python -m pip_audit
```

Local secret scan (from the repo root; `gitleaks` is the tool named in the
ticket, `detect-secrets` was used here as an equivalent since no container
runtime was available to pull the `gitleaks` image):

```bash
pip install detect-secrets
python -m detect_secrets scan --exclude-files '(\.venv/|target/|node_modules/|\.duckdb$|\.git/)' .
```

**Status as of this branch**: dependency scan run and fixed (`setuptools`
floor raised to `>=83.0.0` in `pyproject.toml`, clearing 4 known CVEs — see
git history). Secret scan run against the whole repo; the only 3 hits are
pre-existing `Secret Keyword` matches in `sprint-06-trade-api` test fixtures
and a `${DB_PASSWORD:postgres}` env-default, all outside this sprint's scope
and, on inspection, apparent false positives (test-only placeholder values,
not real credentials) — flagged for team review per SEC4-620's rule against
unilaterally dismissing a finding, not fixed here since that module belongs
to a different sprint's branch. `executor/pom.xml` now has the
`sonar-maven-plugin` wired in (`mvn sonar:sonar -Dsonar.token="$SONAR_TOKEN"`,
against `sonar.host.url=http://localhost:9000`, `sonar.projectKey=trade-executor`
— both overridable on the command line), but the live SonarQube gate itself
(dashboard pass/fail, findings fixed) has still not been run: that needs
Docker Desktop running locally and a manually-generated `SONAR_TOKEN`, which
weren't available when this config was wired up. Run it and address whatever
it reports before calling SEC4-620 done — see the ticket notes and
`00-MASTER-PLAN-sprint7.md`.
