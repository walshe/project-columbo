---
phase: 07-market-thermometer
plan: "03"
type: summary
status: complete
date: 2026-05-30
---

# 07-03 Summary: Market Thermometer Tests

## Result
BUILD SUCCESS — 224 tests passing (0 failures, 0 errors)

## Test Files Created

1. **ThermometerCalculatorTest** (9 tests) — unit test, no Spring
   - Path: `backend/java/src/test/java/walshe/projectcolumbo/persistence/service/ThermometerCalculatorTest.java`
   - Covers: null/empty guard, inside bar → temp=0, highDiff wins, lowDiff wins, null EMA for <22 values, non-null EMA at index 21, 25-candle EMA boundary

2. **ThermometerServiceIntegrationTest** (6 tests) — Spring + Testcontainers
   - Path: `backend/java/src/test/java/walshe/projectcolumbo/persistence/service/ThermometerServiceIntegrationTest.java`
   - Covers: 24 rows from 25 candles, null EMA for first 21 rows, non-null EMA for last 3, skip <2 candles, idempotency, positive temperature

3. **ThermometerStateServiceIntegrationTest** (9 tests) — Spring + Testcontainers
   - Path: `backend/java/src/test/java/walshe/projectcolumbo/persistence/service/ThermometerStateServiceIntegrationTest.java`
   - Covers: QUIET derivation, HOT derivation, SPIKE derivation, SPIKE priority over HOT, null EMA skip, no-row skip, idempotency, CROSSED_ABOVE_EMA event, CROSSED_BELOW_EMA event

4. **ThermometerScanIntegrationTest** (4 tests) — Spring + Testcontainers + MockMvc
   - Path: `backend/java/src/test/java/walshe/projectcolumbo/api/v1/scan/ThermometerScanIntegrationTest.java`
   - Covers: QUIET scan returns ThermometerMatch with numeric temperature+ema, empty when HOT, SPIKE scan, full daily trading scan (W1 GREEN AND D1 GREEN AND QUIET → BTC matches, ETH excluded)

## Files Updated

5. **ScanValidatorTest** — 2 new tests appended
   - Added: `shouldAcceptValidThermometerStateCondition` (THERMOMETER_QUIET)
   - Added: `shouldAcceptValidThermometerEventCondition` (THERMOMETER_CROSSED_ABOVE_EMA)

## Deviations / Fixes Applied

1. **ThermometerIndicator entity fix**: `created_at` column was annotated as nullable in the JPA entity but the DB migration defines it as `NOT NULL DEFAULT NOW()`. Added `nullable = false` to the `@Column` annotation to satisfy Hibernate schema validation.

2. **@AfterEach teardowns added**: 
   - `ThermometerServiceIntegrationTest` — added `@AfterEach tearDown()` to prevent FK leaks into subsequent test classes
   - `ThermometerScanIntegrationTest` — added `@AfterEach tearDown()` for same reason

3. **MarketPipelineIntegrationTest updated**: The pipeline now invokes `ThermometerService`, producing `indicator_thermometer` rows. The existing `@BeforeEach setUp()` didn't delete thermometer rows before deleting assets, causing FK violations in tests that ran afterward. Added `ThermometerRepository` injection and `thermometerRepository.deleteAll()` to the cleanup sequence.

## Requirements Covered
- THERM-01: ThermometerServiceIntegrationTest verifies rows persisted
- THERM-02: ThermometerCalculatorTest verifies temperature formula
- THERM-03: ThermometerCalculatorTest verifies 22-period EMA null/non-null boundary
- THERM-04: ThermometerServiceIntegrationTest idempotency test
- THERM-06: ThermometerStateServiceIntegrationTest QUIET/HOT/SPIKE derivation + priority
- THERM-07: ScanValidatorTest THERMOMETER_QUIET state accepted
- THERM-08: ThermometerStateServiceIntegrationTest signal_state rows written
- THERM-09: ThermometerScanIntegrationTest temperature + temperatureEma are numbers in JSON

## Final Counts
- Tests before this plan: ~211 (baseline from plan reference ≥194, actual was higher)
- Tests after this plan: 224
- New tests added: 9 (ThermometerCalculator) + 6 (ThermometerService) + 9 (ThermometerStateService) + 4 (ThermometerScan) + 2 (ScanValidator) = 30 new tests
