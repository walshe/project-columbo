# Story 009 — Add RSI Indicator

## Phase 1 — Data Model & Migration

* [ ] Extend ENUMs:

    * `indicator_type` → add `'RSI'`
    * `trend_state` → add `'ABOVE_60'`, `'BELOW_40'`, `'NEUTRAL'`
    * `event` → add `'CROSSED_ABOVE_60'`, `'CROSSED_BELOW_40'`
* [ ] Create `indicator_rsi` table:

    * `id BIGSERIAL PRIMARY KEY`
    * `asset_id BIGINT NOT NULL REFERENCES asset(id)`
    * `timeframe timeframe NOT NULL`
    * `close_time TIMESTAMPTZ NOT NULL`
    * `rsi_value NUMERIC(10,4) NOT NULL`
    * `created_at TIMESTAMPTZ DEFAULT now()`
    * Unique constraint `(asset_id, timeframe, close_time)`
* [ ] Add indexes on `(asset_id, timeframe, close_time DESC)`
* [ ] Verify Flyway migration runs cleanly

---

## Phase 2 — Domain & Repository

* [ ] Create `IndicatorRsi` JPA entity mirroring schema
* [ ] Map `timeframe` using `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`
* [ ] Map relationships:

    * `asset` as `@ManyToOne(fetch = LAZY)`
* [ ] Create `IndicatorRsiRepository`

    * `findLatestByAssetAndTimeframe(asset, timeframe)`
    * `findByAssetAndTimeframeAndCloseTime(asset, timeframe, closeTime)`
* [ ] Add repository integration test using PostgreSQL Testcontainers

---

## Phase 3 — RSI Computation Service

* [ ] Create `RsiComputationService`

    * `computeForAsset(asset, timeframe)` method
    * Fetch last 15 OHLC close prices
    * Compute 14-period RSI using Wilder’s formula:

      ```
      RS = avg_gain / avg_loss
      RSI = 100 - (100 / (1 + RS))
      ```
    * Persist new RSI row if not already stored
* [ ] Return computed RSI value to caller
* [ ] Add unit tests with known price series and expected RSI outputs

---

## Phase 4 — SignalState Integration

* [ ] Extend `SignalStateService` with:

    * `computeForRsi(asset, timeframe, closeTime, rsiValue)`
* [ ] Classification rules:

    * `rsi >= 60 → ABOVE_60`
    * `rsi <= 40 → BELOW_40`
    * otherwise `NEUTRAL`
* [ ] Event detection:

    * BELOW_40 → ABOVE_60 → `CROSSED_ABOVE_60`
    * ABOVE_60 → BELOW_40 → `CROSSED_BELOW_40`
    * Else → `NONE`
* [ ] Persist to shared `signal_state` table
* [ ] Ensure idempotency and correct event continuity

---

## Phase 5 — Scheduler & Integration

* [ ] Update `IndicatorComputationScheduler` to include RSI:

    * After SuperTrend computation, call `RsiComputationService.computeForAsset`
* [ ] Ensure new `signal_state` rows appear for RSI
* [ ] Validate integration end-to-end:

    * OHLC → RSI → SignalState
* [ ] Run `MarketPulse.computeDaily()` to confirm RSI is included in aggregation automatically

---

## Phase 6 — Testing & Validation

* [ ] Unit test RSI math with known data points
* [ ] Repository integration tests for persistence
* [ ] End-to-end test with seeded OHLC candles
* [ ] Verify correct event transitions (`CROSSED_ABOVE_60`, `CROSSED_BELOW_40`)
* [ ] Confirm idempotent re-runs
* [ ] Validate MarketPulse includes RSI without code changes

---

## Deliverables

✅ Flyway migration for `indicator_rsi`
✅ `IndicatorRsi` JPA entity and repository
✅ `RsiComputationService` with correct formula
✅ RSI-based `signal_state` and event detection
✅ Integration with scheduler and MarketPulse
✅ Comprehensive unit + integration tests
