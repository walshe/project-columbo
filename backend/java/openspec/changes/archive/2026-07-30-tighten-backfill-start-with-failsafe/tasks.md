## 1. Config

- [x] 1.1 Change `app.ingestion.backfill-start` in `application.yaml` from `2025-01-01T00:00:00Z` to `2025-07-01T00:00:00Z`, and update the surrounding comment to explain the value clears W1 SuperTrend's ~20-week warm-up and stays under Binance's 500-candle single-fetch cap

## 2. Startup validator

- [x] 2.1 Add a `BackfillStartValidator` component that reads `IngestionProperties` and, in `@PostConstruct`, throws `IllegalStateException` when `backfill-start` is null or when `now − backfill-start` is less than the required weekly-candle lookback; message states configured start, computed minimum, and remedy
- [x] 2.2 Define the coverage floor as a named constant (e.g. `MIN_WEEKLY_CANDLES_REQUIRED = 20`) with a comment that it tracks only the enabled W1 indicators and must be raised (~100 weekly ≈ 2yr) if Elder Impulse / Thermometer are reinstated

## 3. Tests

- [x] 3.1 Unit test the validator: passes for a sufficiently-early start, throws for a too-recent start, and throws for a null start (use a fixed/injected clock or `now` reference so the test is deterministic)
- [x] 3.2 Run the affected test suite and confirm green
