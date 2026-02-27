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
- [x] Implement `RsiCalculator`
- [x] Fetch last 15 OHLC closes from `ohlc` table
- [x] Compute RSI via 14-period Wilder’s formula:
  - [x] `RS = avgGain / avgLoss;`
  - [x] `RSI = 100 - (100 / (1 + RS));`
- [x] Handle division by zero (no loss period)
- [x] Persist computed RSI to `indicator_rsi`
- [x] Skip insert if RSI unchanged for same `close_time`
- [x] Add unit test:
  - [x] Seed synthetic OHLC closes
  - [x] Assert correct RSI output for known examples

#### 🔄 Phase 4 — SignalState Integration
- [x] Extend `SignalStateService`:
  - [x] Add method `computeForRsi(Asset asset, Timeframe tf, OffsetDateTime closeTime, BigDecimal rsiValue)`
- [x] Implement classification:
  - [x] `rsi >= 60` → `ABOVE_60`
  - [x] `rsi <= 40` → `BELOW_40`
  - [x] `otherwise` → `NEUTRAL`
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
- [x] Update `IndicatorComputationScheduler`:
  - [x] After SuperTrend computation → trigger RSI computation
- [x] Reuse same asset list and timeframe logic
- [x] Skip already-finalized RSI rows
- [x] Verify integrated flow:
  - [x] `OHLC → RSI → SignalState`
- [x] Confirm new rows in both `indicator_rsi` and `signal_state`
- [x] Confirm MarketPulse picks up RSI automatically
- [x] Validate incremental re-runs (no duplication)

#### 🧪 Phase 6 — Testing & Validation
- [x] Unit tests:
  - [x] RSI formula correctness (compare to known values)
  - [x] Classification and event transitions
- [x] Repository tests:
  - [x] Unique constraint + retrieval ordering
- [x] Integration tests (Testcontainers):
  - [x] End-to-end flow with seeded OHLC data
  - [x] Idempotent re-run verification
  - [x] Multi-asset scenario (2+ assets)
- [x] Validate output in MarketPulse aggregation
- [x] Ensure `indicator_type=RSI` rows contribute correctly
- [x] Confirm performance acceptable (<200ms per asset)

#### ✅ Deliverables
- [x] New Flyway migration for RSI schema
- [x] `Rsiindicator` JPA entity + repository
- [x] `RsiCCalculator`
- [x] Extended `SignalStateService` for RSI events
- [x] Updated scheduler to trigger RSI computation
- [x] Complete unit + integration test coverage
- [x] Verified RSI visible via MarketPulse aggregation
