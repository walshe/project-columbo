Story 009 — Add RSI Indicator — Tasks

#### 🧩 Phase 1 — Data Model & Migration
- [x] Create new Flyway migration file: `V009__add_indicator_rsi.sql`
- [x] Extend ENUMs:
  - [x] Add 'RSI' to `indicator_type`
  - [x] Add 'ABOVE_60', 'BELOW_40', 'NEUTRAL' to `trend_state`
  - [x] Add 'CROSSED_ABOVE_60', 'CROSSED_BELOW_40' to `event`
- [x] Create table `indicator_rsi` with:
  - [x] `id` BIGSERIAL PRIMARY KEY
  - [x] `asset_id` BIGINT REFERENCES asset(id)
  - [x] `timeframe` timeframe NOT NULL
  - [x] `close_time` TIMESTAMPTZ NOT NULL
  - [x] `rsi_value` NUMERIC(10,4) NOT NULL
  - [x] `created_at` TIMESTAMPTZ DEFAULT now()
- [x] Unique constraint `(asset_id, timeframe, close_time)`
- [x] Add index on `(asset_id, timeframe, close_time DESC)`
- [x] Validate migration with local PostgreSQL / Testcontainers

#### 🧠 Phase 2 — Domain & Repository
- [x] Create `RsiIndicator` entity in `walshe.projectcolumbo.persistence`
- [x] Annotate with `@Entity` and `@Table(name = "indicator_rsi")`
- [x] Map timeframe using `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`
- [x] Add relation to `Asset`
- [x] Map `rsiValue` as `BigDecimal`
- [x] Implement `RsiRepository`:
  - [x] `Optional<RsiIndicator> findLatestByAssetAndTimeframe(Asset asset, Timeframe tf)`
  - [x] `Optional<RsiIndicator> findByAssetAndTimeframeAndCloseTime(Asset asset, Timeframe tf, OffsetDateTime closeTime)`
- [x] Add repository integration test:
  - [x] Persist and query RSI rows
  - [x] Validate uniqueness and ordering

#### ⚙️ Phase 3 — RSI Computation Service
- [ ] Implement `RsiCalculator`
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
- [x] Implement event detection:
  - [x] Detect any trend change as an event (e.g., NEUTRAL -> ABOVE_60)
  - [x] `BELOW_40` → `ABOVE_60` → `CROSSED_ABOVE_60`
  - [x] `ABOVE_60` → `BELOW_40` → `CROSSED_BELOW_40`
  - [x] `else` → `NONE`
- [x] Upsert into `signal_state` table
- [x] Ensure idempotency (identical inputs = no DB diff)
- [x] Add test cases:
  - [x] Verify correct event transitions
  - [x] Neutral to trend states generate events
  - [x] No trend change generates no event

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
- [ ] `Rsiindicator` JPA entity + repository
- [ ] `RsiCCalculator`
- [ ] Extended `SignalStateService` for RSI events
- [ ] Updated scheduler to trigger RSI computation
- [ ] Complete unit + integration test coverage
- [ ] Verified RSI visible via MarketPulse aggregation
