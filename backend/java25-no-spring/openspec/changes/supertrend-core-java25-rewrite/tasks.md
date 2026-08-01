## 1. Project foundations

- [x] 1.1 Scaffold `backend/java25-no-spring/` as a standalone Java 25 module (Maven, no Spring Boot parent POM — see `pom.xml`; JDK 25/Maven pinned locally via `.sdkmanrc`)
- [x] 1.2 Decide and document final package layout (`indicator`, `ingestion`, `signal`, `pulse`, `pipeline`, `api`, `persistence`, `freshness` under `walshe.projectcolumbo.supertrend` — matches design.md's sketch, created as empty packages ready for group 2+)
- [x] 1.3 Set up `System.Logger`-based logging convention (no SLF4J/Logback) — see `package-info.java` + `logging.properties`, wired in `Main`
- [x] 1.4 Set up Postgres connection handling (`DataSourceFactory` — HikariCP, configured via `SUPERTREND_DB_*` env vars with local-dev defaults)
- [x] 1.5 Set up Flyway (retained per design.md) with an empty baseline migration (`SchemaMigrator` + `V1__baseline.sql`) — compiles clean; full DB smoke-test deferred to 16.1 since no local Postgres is running yet

## 2. supertrend-indicator-core

- [x] 2.1 Implement `Candle` domain type (OHLC + close time, timeframe) as a plain record
- [x] 2.2 Implement True Range / Wilder ATR calculation (`BigDecimal`, scale/rounding per design.md)
- [x] 2.3 Implement basic and final (sticky) band calculation
- [x] 2.4 Implement SuperTrend value + direction + flip detection over an ordered candle series
- [x] 2.5 Implement incremental recomputation with warm-up window (`atrLength * 10`) and full-recalculation mode
- [x] 2.6 Unit tests: deterministic repeat calculation, band stickiness edge cases, flip detection, incremental-vs-full-recalc equivalence on overlapping ranges (8/8 passing — `SuperTrendCalculatorTest`)
- [ ] 2.7 Characterization test: run against a known historical candle series and diff output against `backend/java`'s stored `indicator_supertrend` values for the same asset/range (validates the 2.0 multiplier decision reproduces real output) — DEFERRED into 16.2 (both need the same one-time `backend/java` stack + real data setup; doing it once at the end covers indicator + all endpoints together)

## 3. Persistence & schema

- [x] 3.1 Author fresh Flyway migrations for: `asset`, `candle`, `indicator_supertrend`, `signal_state` (no `indicator_type` column), `market_breadth_snapshot`, `ingestion_run`, and the `v_asset_liquidity` view — V2-V8, verified against a real throwaway Postgres container (all 8 migrations apply cleanly, idempotent re-run confirmed)
- [x] 3.2 Define Postgres native enums: `timeframe`, `provider`, `supertrend_direction`, `trend_state`, `signal_event`, `ingestion_run_status` — defined with full value sets up front, no incremental ALTER TYPE history replayed
- [x] 3.3 Implement DAOs with hand-written JDBC + row mappers: `AssetDao`, `CandleDao`, `SuperTrendIndicatorDao`, `SignalStateDao`, `MarketBreadthSnapshotDao`, `IngestionRunDao`
- [x] 3.4 Implement idempotent upsert semantics for candles and indicator rows (insert-if-absent, update-with-warn-if-different, unique on `(asset, timeframe, close_time)`) — added Testcontainers (test-scope) so `PersistenceIntegrationTest` spins up its own throwaway Postgres and verifies all 6 DAOs for real (16/16 tests passing)
- [x] 3.5 Seed initial asset list — reused `backend/java`'s 60-asset list (final USDT-suffixed form), verified 60 rows present after migration

## 4. market-data-ingestion

- [x] 4.1 Implement `MarketDataProvider` interface (single method: fetch daily candles for a symbol + time range) — returns `indicator.Candle` directly (timeframe=D1), no separate DTO needed since this system only ever fetches D1 from a provider
- [x] 4.2 Implement Binance provider using `java.net.http.HttpClient` + a JSON library for klines parsing (Jackson core) — volume read from kline index 7 (quote-asset/USDT volume, not index 5 base-asset volume), matching `backend/java`'s behavior exactly
- [x] 4.3 Implement invalid-symbol handling → asset deactivation (`InvalidSymbolException`, detected via Binance's `-1121` error code)
- [x] 4.4 Implement incremental time-window computation per asset (last stored close time, or configured backfill-start if none) — `SUPERTREND_BACKFILL_START` env var
- [x] 4.5 Implement per-asset error isolation (continue on provider error, record error count + truncated error sample) — `IngestionStats` (immutable record) accumulates across assets
- [x] 4.6 Implement D1→W1 rollup (Monday-start week grouping, require exactly 7 finalized source candles) — `CandleRollupService`, reuses `CandleDao.upsert`'s idempotent warn-on-revision semantics for W1 rows too
- [x] 4.7 Implement backfill-window startup validation (≥147 days / 20 weekly candles, fail fast if insufficient) — `BackfillStartValidator`, matches old semantics exactly; not yet wired into `Main`'s startup sequence (Main isn't the full composition root yet — happens when group 5's pipeline entry point is built)
- [x] 4.8 Tests: idempotent re-ingestion (no-op vs. revision-with-warning), rollup completeness rules, backfill validation failure case — 37/37 passing across `BinanceMarketDataProviderTest`, `BackfillStartValidatorTest`, `CandleRollupServiceTest` (Testcontainers), `CandleIngestionServiceTest` (Testcontainers + fake provider)

## 5. pipeline-orchestration

- [x] 5.1 Implement `ingestion_run` lifecycle tracking (RUNNING → SUCCESS/PARTIAL/FAILED with counts) — `PipelineOrchestrator`, status derived from `IngestionStats.errorCount()` vs active asset count (0 errors → SUCCESS, errorCount ≥ assetCount → FAILED, else PARTIAL); unhandled exceptions also mark the run FAILED rather than leaving it stuck RUNNING
- [x] 5.2 Implement concurrent-run rejection (reject new run if one is `RUNNING` for the same provider+timeframe) — `IngestionAlreadyRunningException`, checked via `IngestionRunDao.isRunning` before starting
- [x] 5.3 Implement per-asset parallel fan-out within a phase using virtual threads / `StructuredTaskScope` — `ParallelAssetExecutor` (`Executors.newVirtualThreadPerTaskExecutor()`, not `StructuredTaskScope` — that API's preview status in JDK 25 was unclear, so went with the unambiguously-stable option); used by the new `IndicatorComputationService`
- [x] 5.4 (PARTIAL) Implement the sequential phase chain — built the 4 phases that currently exist: ingest → D1 indicators → W1 rollup → W1 indicators, each fully committed before the next reads it. **D1/W1 signal detection and market pulse phases (groups 6/7) are not built yet and are NOT in this chain** — `PipelineOrchestrator`'s class doc explicitly says where they slot in once those groups exist. Do not check this off as fully done until groups 6/7 are wired in.
- [x] 5.5 Implement the daily scheduled trigger (`ScheduledExecutorService` or explicit sleep-until-next-boundary loop) invoking the identical orchestration path as the manual trigger — `DailyScheduler`, self-reschedules after each firing so a slow run never overlaps the next
- [x] 5.6 (PARTIAL) Tests: concurrent-run rejection and mixed-success → `PARTIAL` status are fully tested (plus SUCCESS/FAILED, not originally called out but straightforward to add). The "signal detection never sees uncommitted indicator writes" case can't be tested until group 6 exists — instead verified phase-ordering for what exists now (end-to-end: ingest → D1 indicators → W1 rollup → W1 indicators all produce correct output from one `runDaily` call). `PipelineOrchestratorTest` (4 tests) + `IndicatorComputationServiceTest` (3 tests), Testcontainers.

## 6. signal-state-detection

- [x] 6.1 Implement SuperTrend direction → trend state mapping (`UP`→`BULLISH`, `DOWN`→`BEARISH`, insufficient-history→`UNKNOWN`)
- [x] 6.2 Implement flip-event emission (bullish-reversal/bearish-reversal exactly on direction change, `NONE` otherwise)
- [x] 6.3 Persist signal state keyed by `(asset, timeframe, close_time)`, no indicator-type dimension
- [x] 6.4 Tests: reversal event timing, `UNKNOWN` state for warm-up-insufficient assets, idempotent re-detection

(PipelineOrchestrator wiring for this phase is deferred alongside group 7's, per the note on tasks 5.4/5.6 — both signal-state-detection and market-breadth-pulse slot in together once both exist.)

## 7. market-breadth-pulse

- [x] 7.1 Implement per-run breadth snapshot computation (bullish/bearish/missing counts, bullish ratio) keyed by `(timeframe, snapshot_close_time)`
- [x] 7.2 Implement latest-snapshot retrieval per timeframe
- [x] 7.3 Implement historical snapshot retrieval with optional date range
- [x] 7.4 Tests: count consistency (bullish+bearish+missing = total), empty-history case returns no result rather than an error

(7.2/7.3 were already implemented by `MarketBreadthSnapshotDao.findLatest`/`findRange` in group 3 foundations; this group added the computation service. PipelineOrchestrator wiring for both this phase and signal-state-detection is deferred together — see the note on tasks 5.4/5.6.)

## 8. data-freshness (cross-cutting)

- [x] 8.1 Implement expected-latest-candle boundary computation (D1: UTC-midnight-today minus 1 day; W1: minus 7 days)
- [x] 8.2 Implement up-to-date comparison against actual latest stored close time
- [x] 8.3 Implement 6-hour grace window and `requireFresh` 503 rejection with `Retry-After` header
- [x] 8.4 Implement shared freshness-metadata lookup (last successful ingestion timestamp, latest candle date) for reuse across all read APIs
- [x] 8.5 Tests: grace-window boundary cases, freshness fields null before first ingestion

(8.3's 503-with-Retry-After is implemented as `StaleDataException` carrying a fixed `retryAfterSeconds()` — actual HTTP mapping happens once group 9's error-response layer exists. `FreshnessService` isn't wired into any read path yet, same deferred-wiring pattern as groups 5/6/7.)

## 9. HTTP layer & JSON foundations

- [ ] 9.1 Stand up `com.sun.net.httpserver.HttpServer` with a small path+method router
- [ ] 9.2 Implement query-param parsing helpers (required/optional params, enum params, boolean flags)
- [ ] 9.3 Wire up JSON serialization (Jackson core or similar) for the DTO shapes needed by the APIs below (primitives, `BigDecimal`, `OffsetDateTime`, enums, lists, nested records)
- [ ] 9.4 Implement shared error-response mapping (400 for bad input, 404 for not-found-but-valid-request, 409 for conflicting run, 503 for stale data) replacing the old `@RestControllerAdvice` pattern
- [ ] 9.5 Implement `text/markdown` and `text/plain` (watchlist) response writers alongside JSON

## 10. signals-api

- [ ] 10.1 Implement `GET /api/v1/signals` (timeframe required, state/sort/requireFresh optional)
- [ ] 10.2 Implement all `SignalSort` orderings (asset asc, last-flip asc/desc, trend-state asc, liquidity desc, pct-change asc/desc)
- [ ] 10.3 Implement `GET /api/v1/assets/by-state` (state required, no sort/freshness gating)
- [ ] 10.4 Implement percentage-change-since-flip and 7-day average volume enrichment (via `v_asset_liquidity`)
- [ ] 10.5 Tests: sort orderings, required-param validation, stale-rejection behavior

## 11. trend-alignment-api

- [ ] 11.1 Implement cross-timeframe (W1+D1) bull/bear confluence list computation, ordered by D1 flip date descending
- [ ] 11.2 Implement bullish/bearish retest detection within `maxRetestAgeDays`
- [ ] 11.3 Implement JSON, Markdown, and Watchlist output formats
- [ ] 11.4 Wire freshness metadata and `requireFresh` gating
- [ ] 11.5 Characterization test against `backend/java`'s `/summary/trend-alignment` output for the same seeded data

## 12. summary-api

- [ ] 12.1 Implement SuperTrend-only bullish/bearish asset lists (direct signal-state query, no scan-engine dependency)
- [ ] 12.2 Wire in market pulse for the requested timeframe
- [ ] 12.3 Implement JSON, Markdown, and Watchlist output formats
- [ ] 12.4 Confirm no RSI-related fields exist anywhere in the response shape (deliberate breaking change vs. old impl)

## 13. scan-api

- [ ] 13.1 Implement `ScanCondition` (timeframe, trend-state filter, max-days-since-flip) with implicit SuperTrend-only targeting (no indicator-type field)
- [ ] 13.2 Implement AND/OR condition combination and result-limit truncation
- [ ] 13.3 Tests: single-condition match, AND-combined cross-timeframe match, days-since-flip filter, limit truncation

## 14. candle-coverage-api

- [ ] 14.1 Implement `GET /api/v1/candles/coverage` (per-timeframe earliest/latest/expectedLatest/upToDate/assetCount)

## 15. ingestion-trigger-api

- [ ] 15.1 Implement `POST /api/v1/internal/ingestion/run` (optional provider/timeframe, defaults to Binance/D1)
- [ ] 15.2 Wire 202-Accepted + run-ID response, and 409 on concurrent-run rejection
- [ ] 15.3 Confirm manual-trigger run records are indistinguishable in shape from scheduled-trigger run records

## 16. End-to-end validation

- [ ] 16.1 Seed an independent dev database and run the full pipeline once end-to-end (ingest → indicators → signals → pulse → rollup → W1)
- [ ] 16.2 Characterization-test every in-scope endpoint's JSON output against `backend/java`'s equivalent endpoint for the same underlying data (also covers the indicator-level comparison deferred from 2.7 — diff raw `indicator_supertrend` values, not just endpoint JSON)
- [ ] 16.3 Confirm no Spring, JPA/Hibernate, Lombok, SLF4J, or Micrometer dependency exists anywhere in the module's dependency tree
- [ ] 16.4 Document final package layout and any deviations from the design.md sketch back into design.md
