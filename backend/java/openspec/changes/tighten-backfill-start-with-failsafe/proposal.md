## Why

`app.ingestion.backfill-start` is `2025-01-01`, which on the current date is ~547 days of daily history. Two problems follow from it: (1) it exceeds Binance's default klines page cap (500), so a fresh single-pass backfill is silently truncated and misses the most recent candles until a second pipeline run heals it — which, because SuperTrend is path-dependent, produces wrong flip points on a fresh database; and (2) it pulls far more history than the only enabled weekly indicator actually needs. Nothing validates that the configured start is even sufficient, so a too-recent value would silently yield unreliable weekly signals.

## What Changes

- Move `app.ingestion.backfill-start` from `2025-01-01` to `2025-07-01` (~52 weekly candles). This comfortably clears W1 SuperTrend's ~20-week ATR warm-up and drops the fresh-backfill window under Binance's 500-candle default, eliminating the truncation.
- Add a fail-fast startup validator that computes the minimum daily history required for the **currently enabled** weekly indicator (W1 SuperTrend, ~20 weekly candles) and throws at boot if `now − backfill-start` falls short. **BREAKING** for misconfiguration only: an under-provisioned `backfill-start` now prevents startup instead of producing bad signals silently.
- Document, in code, that the minimum tracks the *enabled* weekly indicators — and that re-enabling Elder Impulse / Thermometer (W1 EMA-13 / MACD, ~100 weekly candles ≈ 2 years) requires raising the constant and moving `backfill-start` back accordingly.

## Capabilities

### New Capabilities

- `backfill-coverage`: Validation that the configured ingestion backfill start provides enough daily history for the enabled weekly indicators to warm up, enforced at application startup.

### Modified Capabilities

_(none — no existing versioned spec governs backfill configuration)_

## Impact

- `application.yaml` — `app.ingestion.backfill-start` value + explanatory comment
- New `BackfillStartValidator` component — runs at startup, throws `IllegalStateException` when coverage is insufficient
- `IngestionProperties` — read by the validator (no shape change required)
- **Out of scope**: the candle coverage/freshness endpoint (separate change), pagination of the Binance fetch, and any change to indicator math
