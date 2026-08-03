# Plan 05-01 Summary

**Status:** Complete
**Date:** 2026-05-29

## Files Created
- `backend/java/src/main/resources/db/migration/V15__add_ema_macd_indicators.sql`
- `backend/java/src/main/java/walshe/projectcolumbo/persistence/service/EmaCalculator.java`
- `backend/java/src/main/java/walshe/projectcolumbo/persistence/service/MacdCalculator.java`

## Verification
- `grep -c "CREATE TABLE indicator_ema" V15__add_ema_macd_indicators.sql` → 1 ✓
- `grep -c "CREATE TABLE indicator_macd" V15__add_ema_macd_indicators.sql` → 1 ✓
- `grep "period INTEGER NOT NULL"` → match ✓
- `grep "record EmaResult"` → match ✓
- `grep "calculateFromValues"` → match ✓
- `grep "record MacdResult"` → match ✓
- `grep "FAST_PERIOD = 12"` → match ✓
- `cd backend/java && ./mvnw compile -q` → exit 0 (no output) ✓

## Notes
- V15 migration is additive only (CREATE TABLE) — no enum changes, consistent with plan spec that Elder Impulse state enum is added in Phase 6.
- `indicator_ema` uses NUMERIC(20,8) for ema_value to handle crypto price ranges spanning many orders of magnitude; unique constraint includes `period` so the same asset/timeframe can store both 13-period and 26-period EMA rows.
- `indicator_macd` stores pre-computed macd_line, signal_line, and histogram for fast retrieval.
- EmaCalculator provides a `calculateFromValues` overload so MacdCalculator can feed the raw MACD line values (not candles) into EMA(9) for the signal line — this is the critical link between the two calculators.
- All EMA arithmetic uses scale=10, RoundingMode.HALF_UP throughout, mirroring the RsiCalculator pattern.
