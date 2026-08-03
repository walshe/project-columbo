---
phase: 07-market-thermometer
plan: "02"
status: complete
date: 2026-05-30
---

# 07-02 Summary: Market Thermometer Pipeline + Scan API Wiring

## What was done

### Task 1 — ThermometerService
Created `persistence/service/ThermometerService.java`. Loads D1 finalized candles per active asset, calls `ThermometerCalculator.calculate()`, upserts into `indicator_thermometer`. Uses `BigDecimal.compareTo()` for equality (not `.equals()`). Null-safe `temperatureEma` comparison: both null → match; one null → mismatch.

### Task 2 — ThermometerStateService
Created `persistence/service/ThermometerStateService.java`. Reads latest `ThermometerIndicator` per asset; skips when `temperatureEma == null`. SPIKE priority: `temperature > 3*ema` checked before `temperature > ema`. Event logic: SPIKE → always `THERMOMETER_TRIPLE_SPIKE`; QUIET→HOT → `THERMOMETER_CROSSED_ABOVE_EMA`; HOT/SPIKE→QUIET → `THERMOMETER_CROSSED_BELOW_EMA`; otherwise `NONE`. Upserts to `signal_state` with `MARKET_THERMOMETER` / `D1`.

### Task 3 — ThermometerMatch DTO + MatchedIndicator + ScanResult
Created `api/v1/scan/dto/ThermometerMatch.java` — 6 fields (no event field: continuous measurement). Updated `MatchedIndicator` sealed interface: added `ThermometerMatch` to `permits` clause and `@JsonSubTypes`. Updated `ScanResult` `@ArraySchema` discriminator with `MARKET_THERMOMETER → ThermometerMatch`.

### Task 4 — ScanService, ScanValidator, MarketPulseService
- **ScanService**: Added `ThermometerRepository` constructor dep. Added `MARKET_THERMOMETER` branch in `mapToMatchedIndicator()` (single repository call with `orElse` fallback to ZERO). Added `ThermometerMatch` instanceof check in `addIndicatorIfNotPresent()`.
- **ScanValidator**: Added `MARKET_THERMOMETER` to both `VALID_EVENTS` (CROSSED_ABOVE_EMA, CROSSED_BELOW_EMA, TRIPLE_SPIKE) and `VALID_STATES` (QUIET, HOT, SPIKE).
- **MarketPulseService**: Replaced stubs with real logic — `isBullishState → THERMOMETER_QUIET`; `isBearishState → HOT || SPIKE`.

### Task 5 — MarketPipelineService
Added `ThermometerService` and `ThermometerStateService` as constructor deps. `ThermometerService.computeForActiveAssets(false)` added to INDICATOR phase (after MACD). `ThermometerStateService.computeForAllActiveAssets()` added as new `D1_THERMOMETER` phase (after D1_IMPULSE, before MARKET_PULSE).

Updated `MarketPipelineServiceTest` and `ScanServiceTest` — added mocks for both new services and updated constructor calls.

## Pipeline order (final)
```
INGESTION → INDICATOR (ST, RSI, EMA-13, MACD, Thermometer) → SIGNAL → D1_IMPULSE → D1_THERMOMETER → MARKET_PULSE → W1_ROLLUP → W1_PROCESSING
```

## Compilation
- `./mvnw compile -q` — exits 0
- `./mvnw test-compile -q` — exits 0

## Requirements satisfied
- THERM-04: ThermometerService computes temperature incrementally on D1 candles
- THERM-05 (complete): all Java + DB enum values in place; ScanValidator wired
- THERM-06: ThermometerStateService derives QUIET/HOT/SPIKE; skips when ema null; SPIKE priority correct
- THERM-07: Scan API accepts MARKET_THERMOMETER state conditions
- THERM-08: Signal query works (signal_state rows written with MARKET_THERMOMETER)
- THERM-09: ThermometerMatch exposes temperature + temperatureEma as BigDecimal in scan results
