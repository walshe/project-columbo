Story 009 — Add RSI Indicator — Tasks

#### 🧩 Phase 1 — Data Model & Migration
- [ ] Create new Flyway migration file: `V009__add_indicator_rsi.sql`
- [ ] Extend ENUMs:
  - [ ] Add 'RSI' to `indicator_type`
  - [ ] Add 'ABOVE_60', 'BELOW_40', 'NEUTRAL' to `trend_state`
  - [ ] Add 'CROSSED_ABOVE_60', 'CROSSED_BELOW_40' to `event`
- [ ] Create table `indicator_rsi` with:
  - [ ] `id` BIGSERIAL PRIMARY KEY
  - [ ] `asset_id` BIGINT REFERENCES asset(id)
  - [ ] `timeframe` timeframe NOT NULL
  - [ ] `close_time` TIMESTAMPTZ NOT NULL
  - [ ] `rsi_value` NUMERIC(10,4) NOT NULL
  - [ ] `created_at` TIMESTAMPTZ DEFAULT now()
- [ ] Unique constraint `(asset_id, timeframe, close_time)`
- [ ] Add index on `(asset_id, timeframe, close_time DESC)`
- [ ] Validate migration with local PostgreSQL / Testcontainers

#### 🧠 Phase 2 — Domain & Repository
- [ ] Create `IndicatorRsi` entity in `walshe.projectcolumbo.indicator.rsi`
- [ ] Annotate with `@Entity` and `@Table(name = "indicator_rsi")`
- [ ] Map timeframe using `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`
- [ ] Add relation to `Asset`
- [ ] Map `rsiValue` as `BigDecimal`
- [ ] Implement `IndicatorRsiRepository`:
  - [ ] `Optional<IndicatorRsi> findLatestByAssetAndTimeframe(Asset asset, Timeframe tf)`
  - [ ] `Optional<IndicatorRsi> findByAssetAndTimeframeAndCloseTime(Asset asset, Timeframe tf, OffsetDateTime closeTime)`
- [ ] Add repository integration test:
  - [ ] Persist and query RSI rows
  - [ ] Validate uniqueness and ordering

#### ⚙️ Phase 3 — RSI Computation Service
- [ ] Implement `RsiComputationService`
- [ ] Fetch last 15 OHLC closes from `ohlc` table
- [ ] Compute RSI via 14-period Wilder’s formula:
  - [ ] `RS = avgGain / avgLoss;`
  - [ ] `RSI = 100 - (100 / (1 + RS));`
- [ ] Handle division by zero (no loss period)
- [ ] Persist computed RSI to `indicator_rsi`
- [ ] Skip insert if RSI unchanged for same `close_time`
- [ ] Add unit test:
  - [ ] Seed synthetic OHLC closes
  - [ ] Assert correct RSI output for known examples

#### 🔄 Phase 4 — SignalState Integration
- [ ] Extend `SignalStateService`:
  - [ ] Add method `computeForRsi(Asset asset, Timeframe tf, OffsetDateTime closeTime, BigDecimal rsiValue)`
- [ ] Implement classification:
  - [ ] `rsi >= 60` → `ABOVE_60`
  - [ ] `rsi <= 40` → `BELOW_40`
  - [ ] `otherwise` → `NEUTRAL`
- [ ] Implement event detection:
  - [ ] `BELOW_40` → `ABOVE_60` → `CROSSED_ABOVE_60`
  - [ ] `ABOVE_60` → `BELOW_40` → `CROSSED_BELOW_40`
  - [ ] `else` → `NONE`
- [ ] Upsert into `signal_state` table
- [ ] Ensure idempotency (identical inputs = no DB diff)
- [ ] Add test cases:
  - [ ] Verify correct event transitions
  - [ ] Neutral transitions generate no event

#### 🕓 Phase 5 — Scheduler & Integration
- [ ] Update `IndicatorComputationScheduler`:
  - [ ] After SuperTrend computation → trigger RSI computation
- [ ] Reuse same asset list and timeframe logic
- [ ] Skip already-finalized RSI rows
- [ ] Verify integrated flow:
  - [ ] `OHLC → RSI → SignalState`
- [ ] Confirm new rows in both `indicator_rsi` and `signal_state`
- [ ] Confirm MarketPulse picks up RSI automatically
- [ ] Validate incremental re-runs (no duplication)

#### 🧪 Phase 6 — Testing & Validation
- [ ] Unit tests:
  - [ ] RSI formula correctness (compare to known values)
  - [ ] Classification and event transitions
- [ ] Repository tests:
  - [ ] Unique constraint + retrieval ordering
- [ ] Integration tests (Testcontainers):
  - [ ] End-to-end flow with seeded OHLC data
  - [ ] Idempotent re-run verification
  - [ ] Multi-asset scenario (2+ assets)
- [ ] Validate output in MarketPulse aggregation
- [ ] Ensure `indicator_type=RSI` rows contribute correctly
- [ ] Confirm performance acceptable (<200ms per asset)

#### ✅ Deliverables
- [ ] New Flyway migration for RSI schema
- [ ] `IndicatorRsi` JPA entity + repository
- [ ] `RsiComputationService`
- [ ] Extended `SignalStateService` for RSI events
- [ ] Updated scheduler to trigger RSI computation
- [ ] Complete unit + integration test coverage
- [ ] Verified RSI visible via MarketPulse aggregation
