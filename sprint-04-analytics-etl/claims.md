# Sprint 4 Business Claims

> Replace the three placeholders below with claims calculated from the actual
> Fauxnance pull. Do not invent the numbers. Each claim must be true of a
> defined period and must name the chart that supports it.

## Claim 1 — Weekly turnover

**Claim:** `[Instrument/company] had the highest average daily turnover during
[period], at approximately [amount], which was [X%] above [comparison].`

**Evidence:** `artefacts/report.html#weekly-turnover`

**What would have to be true for this claim to be wrong?**  
`[State the data condition that would invalidate the claim.]`

## Claim 2 — Largest weekly movement

**Claim:** `[Instrument/company] recorded its largest weekly [gain/loss] of
[X%] in the week beginning [date].`

**Evidence:** `artefacts/report.html#weekly-returns`

**What would have to be true for this claim to be wrong?**  
`[State the data condition that would invalidate the claim.]`

## Claim 3 — Volatility

**Claim:** `[Instrument/company] had the highest average daily high-low range
during [period], at [X%], compared with [comparison].`

**Evidence:** `artefacts/report.html#weekly-range`

**What would have to be true for this claim to be wrong?**  
`[State the data condition that would invalidate the claim.]`

## Scope

Symbols: `INFY.NS` (Infosys), `RELIANCE.NS` (Reliance Industries), `AAPL`
(Apple).

## Review trace

For one selected claim, record the exact query/result rows used to calculate
the number and retain them with the review evidence.

## Entry point

The project exposes the `analytics-etl` console script. It can be run with:

```bash
analytics-etl --start YYYY-MM-DD --end YYYY-MM-DD
```
