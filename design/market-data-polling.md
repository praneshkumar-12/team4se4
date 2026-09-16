# Market-data polling: interval and quota arithmetic

## Constraints

- Fauxnance quota: **2000 requests/day**, resets 00:00 UTC.
- Batch quotes endpoint: up to **25 symbols per request**.
- The poller runs on a schedule inside the Trade Executor process (no
  separate deployable), does one Fauxnance HTTP call per chunk of ≤25
  symbols per tick, then publishes **one Kafka message per symbol** to
  `market-data` (never one message per batch — batching is an HTTP/quota
  optimization only, and putting multiple symbols behind one key would
  break the per-symbol ordering the topic promises).

## Requests/day at candidate intervals (single chunk, ≤25 symbols)

| Interval | Ticks/day | Requests/day (1 chunk) | Verdict |
|---|---|---|---|
| 15s | 5760 | 5760 | exhausted in ~8h20m — too aggressive |
| 30s | 2880 | 2880 | exhausted in ~16h40m — too aggressive |
| 60s | 1440 | 1440 | sustainable, ~560 requests/day margin |
| 120s | 720 | 720 | sustainable, ~1280 requests/day margin |

## Chosen floor: 120 seconds

`MarketDataPoller.MIN_POLL_INTERVAL_SECONDS = 120` is enforced in code
(`enforceFloor()` clamps any configured `POLL_INTERVAL_SECONDS` up to at
least 120 — a below-floor value is never silently honored).

**Why 120s over 60s:** the watched-symbol set (instruments with a non-zero
holding on any account, plus instruments on any resting `NEW` order) is
expected to grow over the life of the platform, and each additional group
of 25 symbols adds one more request per tick. 120s buys headroom for that
growth without ever having to revisit the interval:

| Watched symbols | Chunks/tick | Requests/day at 60s | Requests/day at 120s |
|---|---|---|---|
| ≤25 | 1 | 1440 (margin 560) | 720 (margin 1280) |
| 26–50 | 2 | 2880 — **over quota** | 1440 (margin 560) |
| 51–75 | 3 | 4320 — over quota | 2160 — over quota |

At 60s, the moment the watched set crosses 25 symbols the daily request
count blows through the 2000/day quota. At 120s there's room for up to two
full chunks (50 symbols) before that happens, which comfortably covers this
platform's realistic instrument universe. The cost is data that's up to
120s stale instead of 60s stale — acceptable here because the poller feeds
periodic marketability checks (SEC4-614's fill decision), not a live
order book, so a 2x staleness increase doesn't change correctness.

## Declared symbol set used in tests

`MarketDataPollerTest.configuredIntervalStaysInsideDailyQuotaForDeclaredSymbolSet`
asserts the arithmetic for a declared set of **up to 50 watched symbols**
(2 chunks/tick worst case) at the 120s floor:

```
ticksPerDay   = 86400 / 120           = 720
chunksPerTick = ceil(50 / 25)         = 2
requestsPerDay = 720 * 2              = 1440   (<= 2000 quota, margin 560)
```
