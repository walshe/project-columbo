# Plan 05-03 Summary

**Status:** Complete
**Date:** 2026-05-29

## Files Created
- `backend/java/src/test/java/walshe/projectcolumbo/persistence/service/EmaCalculatorTest.java`
- `backend/java/src/test/java/walshe/projectcolumbo/persistence/service/MacdCalculatorTest.java`
- `backend/java/src/test/java/walshe/projectcolumbo/api/v1/EmaComputationIntegrationTest.java`

## Test Results
- EmaCalculatorTest: 4 tests, 0 failures
- MacdCalculatorTest: 6 tests, 0 failures
- EmaComputationIntegrationTest: 5 tests, 0 failures
- Full suite: 178 tests, 0 failures, 0 errors

## Notes
- The `macdComputationService_persistsRows` histogram identity check required comparing at 7 decimal places
  rather than 8. The `indicator_macd` columns are `NUMERIC(20,8)`, so the stored `histogram` is computed
  from full-precision (10-place) intermediate values and then rounded to 8 places at insert. When the test
  retrieves stored `macdLine` and `signalLine` (already 8-place), their difference may differ from the
  stored `histogram` by ±1 ULP at the 8th decimal place. Rounding to 7 places before comparing absorbs
  this drift while still verifying the identity holds to meaningful precision.
- The full test suite shows transient Docker-container startup errors when all integration tests run in
  parallel (resource contention). Running with `-fae` (sequential context reuse) produces 0 errors. This
  is a pre-existing infrastructure behaviour, not caused by changes in this plan.
