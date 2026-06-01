# Plan 05-02 Summary

**Status:** Complete
**Date:** 2026-05-29

## Files Created
- `backend/java/src/main/java/walshe/projectcolumbo/persistence/entity/EmaIndicator.java` — JPA entity for `indicator_ema` with period + emaValue fields
- `backend/java/src/main/java/walshe/projectcolumbo/persistence/repository/EmaRepository.java` — period-scoped queries for indicator_ema
- `backend/java/src/main/java/walshe/projectcolumbo/persistence/service/EmaComputationService.java` — incremental EMA computation service for any period/timeframe
- `backend/java/src/main/java/walshe/projectcolumbo/persistence/entity/MacdIndicator.java` — JPA entity for `indicator_macd` with macdLine, signalLine, histogram fields
- `backend/java/src/main/java/walshe/projectcolumbo/persistence/repository/MacdRepository.java` — timeframe-scoped queries for indicator_macd
- `backend/java/src/main/java/walshe/projectcolumbo/persistence/service/MacdComputationService.java` — incremental MACD 12-26-9 computation service

## Files Modified
- `backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineService.java` — added EmaComputationService + MacdComputationService injection and calls in PHASE 2 (EMA 13 D1, MACD D1) after RSI
- `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/W1IndicatorService.java` — added EmaComputationService injection and W1_EMA phase between W1_RSI and W1_SIGNAL; MACD correctly excluded from W1
- `backend/java/src/test/java/walshe/projectcolumbo/ingestion/MarketPipelineServiceTest.java` — updated constructor call and inOrder assertions to include EmaComputationService and MacdComputationService mocks
- `backend/java/src/test/java/walshe/projectcolumbo/ingestion/MarketPipelineIntegrationTest.java` — added emaRepository.deleteAll() and macdRepository.deleteAll() in setUp to prevent FK violations on assetRepository.deleteAll()

## Verification
- compile: exit 0
- test suite: 163 tests, 0 failures, 0 errors

## Notes
- Two test fixes were required beyond the 8 plan tasks:
  1. `MarketPipelineServiceTest` — the constructor gained 2 new parameters (EmaComputationService, MacdComputationService), so the unit test's direct constructor call and inOrder verification needed updating.
  2. `MarketPipelineIntegrationTest` — setUp's deleteAll order violated the FK constraint `indicator_ema_asset_id_fkey`; fixed by deleting ema and macd indicator rows before deleting assets.
- W1IndicatorService javadoc comment updated implicitly by the step ordering; MACD is not added to W1 per plan constraint.
