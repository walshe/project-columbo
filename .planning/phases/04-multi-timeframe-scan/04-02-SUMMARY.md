---
plan: "04-02"
phase: 04-multi-timeframe-scan
status: complete
completed: 2026-05-24
---

# Summary: Plan 04-02 — Output DTOs + Service + Validator

## What was done

Task 1: Added `Timeframe timeframe` as the second component to `SupertrendMatch` and `RsiMatch` records (after `indicatorType`), and added `Timeframe timeframe()` method to the `MatchedIndicator` sealed interface. All three files received the corresponding `Timeframe` import.

Task 2: Updated `ScanValidator` to add a private `resolveTimeframe(ScanCondition, Timeframe)` helper that returns the condition-level timeframe if set, falls back to request-level timeframe, or throws `BadRequestException` when neither is present. The existing `validate()` condition loop now calls `resolveTimeframe` before `validateCondition` for each condition.

Task 3: Updated `ScanService` with four changes:
- Added `TIMEFRAME_PRIORITY` static map (`D1=1, W1=2`) and `highestTimeframe(List<MatchedIndicator>)` private helper.
- Replaced single `latestCloseTime` call with a `Map<Timeframe, OffsetDateTime> closeTimeByTimeframe` built once from all unique effective timeframes before the condition loop.
- In the condition loop, resolves `effectiveTf` per condition and uses it for all `signalStateRepository` calls.
- In `mapToMatchedIndicator()`, passes `s.getTimeframe()` as the second arg to both `SupertrendMatch` and `RsiMatch` constructors.
- Replaced `request.timeframe()` with `highestTimeframe(indicators)` in the `TradingViewUtil.generateUrl` call.

## Files modified

- `backend/java/src/main/java/walshe/projectcolumbo/api/v1/scan/dto/MatchedIndicator.java`
- `backend/java/src/main/java/walshe/projectcolumbo/api/v1/scan/dto/SupertrendMatch.java`
- `backend/java/src/main/java/walshe/projectcolumbo/api/v1/scan/dto/RsiMatch.java`
- `backend/java/src/main/java/walshe/projectcolumbo/api/v1/scan/ScanValidator.java`
- `backend/java/src/main/java/walshe/projectcolumbo/api/v1/scan/ScanService.java`

## Verification

`./mvnw compile -q` exits 0 — all main sources compile cleanly.

Acceptance criteria results:
- `grep -c 'Timeframe timeframe()'` MatchedIndicator.java → 1
- `grep -c 'Timeframe timeframe'` SupertrendMatch.java → 1 (second component, after indicatorType, before state)
- `grep -c 'Timeframe timeframe'` RsiMatch.java → 1 (second component, after indicatorType, before event)
- `grep -c 'resolveTimeframe'` ScanValidator.java → 2 (definition + call site)
- `grep -c 'BadRequestException'` ScanValidator.java → 10 (includes new throw in resolveTimeframe)
- `grep -c 'closeTimeByTimeframe'` ScanService.java → 3
- `grep -c 'effectiveTf'` ScanService.java → 4
- `grep -c 'TIMEFRAME_PRIORITY'` ScanService.java → 2
- `grep -c 'highestTimeframe'` ScanService.java → 2
- `grep -c 's\.getTimeframe()'` ScanService.java → 3
- generateUrl call uses `highestTimeframe(indicators)` not `request.timeframe()`

## Requirements satisfied

SCAN-03, SCAN-05, SCAN-06, SCAN-07, SCAN-08, SCAN-09, SCAN-10
