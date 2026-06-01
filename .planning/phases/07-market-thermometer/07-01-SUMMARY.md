# 07-01 Summary: Schema + Calculator

**Status:** Complete
**Date:** 2026-05-30

## Artifacts Created
- V17__add_thermometer_indicator.sql
- IndicatorType.java (MARKET_THERMOMETER added)
- TrendState.java (THERMOMETER_QUIET/HOT/SPIKE added)
- SignalEvent.java (THERMOMETER_CROSSED_ABOVE/BELOW/TRIPLE_SPIKE added)
- ThermometerCalculator.java
- ThermometerIndicator.java
- ThermometerRepository.java

## Verification
```
(no output — clean compile, exit 0)
```

All plan verification checks passed:
- `grep 'MARKET_THERMOMETER' IndicatorType.java` ✓
- `grep 'THERMOMETER_QUIET' TrendState.java` ✓
- `grep 'THERMOMETER_CROSSED_ABOVE_EMA' SignalEvent.java` ✓
- All three new Java files present ✓
- V17 SQL file contains 4 matching lines for indicator_thermometer/MARKET_THERMOMETER/THERMOMETER_QUIET ✓
- `./mvnw compile -q` exits 0 ✓

## Notes
- MarketPulseService `isBullishState`/`isBearishState` switch expressions were non-exhaustive after adding MARKET_THERMOMETER to IndicatorType — added stubs `case MARKET_THERMOMETER -> false;` as directed. Plan 07-02 will replace with real logic.
- ThermometerIndicator has no timeframe field (D1-only by design, per plan).
- temperatureEma column is nullable in both SQL schema and JPA entity.
- ThermometerCalculator reuses EmaCalculator.calculateFromValues() for the 22-day temperature EMA (DRY).
