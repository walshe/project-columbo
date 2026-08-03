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

- [x] 9.1 Stand up the HTTP layer with a path+method router — revised mid-group from a hand-rolled `com.sun.net.httpserver.HttpServer` router to Javalin (`ApiServer`), to support OpenAPI/Swagger UI; see the amended HTTP layer decision and Non-Goals in design.md
- [x] 9.2 Implement query-param parsing (required/optional params, enum params, boolean flags) — via Javalin's built-in `Context.queryParamAsClass`/`Validator`, with a case-insensitive converter registered for `Timeframe`, rather than a hand-written parser
- [x] 9.3 Wire up JSON serialization (Jackson) for the DTO shapes needed by the APIs below (primitives, `BigDecimal`, `OffsetDateTime`, enums, lists, nested records) — shared `ObjectMapper` (`JsonSupport`) fed into Javalin via `JavalinJackson`
- [x] 9.4 Implement shared error-response mapping (400 for bad input, 404 for not-found-but-valid-request, 409 for conflicting run, 503 for stale data) replacing the old `@RestControllerAdvice` pattern — Javalin's built-in `HttpResponseException` subclasses handle 400/404/409/etc. automatically; `app.exception(...)` handlers added for this codebase's own `IngestionAlreadyRunningException` (409) and `StaleDataException` (503 + `Retry-After`)
- [x] 9.5 Implement `text/markdown` and `text/plain` (watchlist) response writers alongside JSON — via `ctx.contentType(...).result(...)`; Javalin's `Context` already covers this natively, so no separate writer utility class is needed (verified with dedicated markdown/plain-text routes in `ApiServerIntegrationTest`)

(No real endpoints registered yet - `ApiServer.create()` isn't started from Main. Verified end-to-end with `javalin-testtools` against throwaway test routes in `ApiServerIntegrationTest`, including the OpenAPI spec and Swagger UI being served; real routes land with groups 10+.)

## 10. signals-api

- [x] 10.1 Implement `GET /api/v1/signals` (timeframe required, state/sort/requireFresh optional) — `SignalsHandler`, registered independently of `ApiServer.create()` (called by tests now, by `Main` once the composition root is wired at the end of the API groups)
- [x] 10.2 Implement all `SignalSort` orderings (asset asc, last-flip asc/desc, trend-state asc, liquidity desc, pct-change asc/desc) — `SignalSort` (7 values, no `LIQUIDITY_ASC` - matches old app), sorting logic in `SignalQueryService.summarize` (pure, unit-tested without a DB)
- [x] 10.3 Implement `GET /api/v1/assets/by-state` (state required, no sort/freshness gating) — reuses `SignalQueryService.listSignals(timeframe, state, null)`, no `requireFresh` check
- [x] 10.4 Implement percentage-change-since-flip and 7-day average volume enrichment (via `v_asset_liquidity`) — new `AssetLiquidityDao`, new `CandleDao.findLatestCloseByAssetForTimeframe`/`findCloseAtTimes` (batched, one query for all assets rather than N+1); formula matches the old app's (`(latest-flip)/flip*100`, 2dp, `HALF_UP`, null on zero/missing)
- [x] 10.5 Tests: sort orderings, required-param validation, stale-rejection behavior — `SignalQueryServiceTest` (pure, 11 tests covering every sort ordering + pct-change edge cases), `SignalQueryServiceIntegrationTest` (Testcontainers), `SignalsHandlerIntegrationTest` (Testcontainers + `javalin-testtools`, covers 400 on missing required params, 503+`Retry-After` on `requireFresh` with stale data, and that `/assets/by-state` doesn't gate on freshness)

(No `IndicatorType`/RSI-namespaced `TrendState` values, `daysSinceFlip`, or `tradingviewUrl` fields — out of scope per this rewrite's SuperTrend-only, single-indicator design; not itemized in this group's task list. `ApiServer.create()` itself is unchanged; `SignalsHandler.register(app)` is called by the composition root once it exists.)

## 11. trend-alignment-api

- [x] 11.1 Implement cross-timeframe (W1+D1) bull/bear confluence list computation, ordered by D1 flip date descending — `TrendAlignmentService.computeAlignment`/`align` (pure static core): confluence = exact-trend-state match on both timeframes (symbol intersection), an asset `UNKNOWN` on either timeframe is silently excluded (never in either W1 symbol set); ordering comes from fetching D1 via `SignalQueryService`'s existing `SignalSort.LAST_FLIP_DESC` and preserving encounter order through the intersection, no extra sort needed
- [x] 11.2 Implement bullish/bearish retest detection within `maxRetestAgeDays` — W1-aligned ∩ D1-counter-trend, filtered to `daysSinceFlip != null && daysSinceFlip <= maxRetestAgeDays` (inclusive boundary, matches old app); `daysSinceFlip` is computed on the fly from `SignalSummary.lastFlipTime()` rather than stored, since group 10 deliberately didn't add that field to `SignalSummary`
- [x] 11.3 Implement JSON, Markdown, and Watchlist output formats — `TrendAlignmentResponse` (JSON), `TrendAlignmentFormatter.toMarkdown`/`toWatchlist`; selected via `?format=JSON|MARKDOWN|WATCHLIST` (new `SummaryFormat` enum, case-insensitive like the other query-param enums), default `JSON`. No `tradingviewUrl` links (out of scope per group 10's precedent) - Markdown/Watchlist entries use plain symbols
- [x] 11.4 Wire freshness metadata and `requireFresh` gating — `TrendAlignmentHandler`, same single-`evaluate()`-per-request pattern as `SignalsHandler` (group 10 fix); always checks **D1 only**, never W1, matching the old app's "D1 is the driving timeframe" rule (W1 is rolled up from D1)
- [ ] 11.5 Characterization test against `backend/java`'s `/summary/trend-alignment` output for the same seeded data — DEFERRED into 16.2 alongside the other characterization tests (needs the same one-time `backend/java` stack + real seeded data setup)

## 12. summary-api

- [x] 12.1 Implement SuperTrend-only bullish/bearish asset lists (direct signal-state query, no scan-engine dependency) — `SummaryHandler` calls `SignalQueryService.listSignals(timeframe, BULLISH/BEARISH, LAST_FLIP_DESC)` directly; no scan engine exists in this rewrite at all (the old app's `/summary` used one only to combine SuperTrend + RSI conditions, which doesn't apply here)
- [x] 12.2 Wire in market pulse for the requested timeframe — `MarketBreadthSnapshotDao.findLatest(timeframe)` (already existed from group 3/7), `null` in the response if no snapshot exists yet for that timeframe, matching the old app
- [x] 12.3 Implement JSON, Markdown, and Watchlist output formats — reuses the `SummaryFormat` enum/converter from group 11; extracted a small shared `SignalTextFormatting` helper (day-count, pct-change, watchlist-section rendering) used by both `SummaryFormatter` and `TrendAlignmentFormatter` rather than duplicating it a second time
- [x] 12.4 Confirm no RSI-related fields exist anywhere in the response shape — `SummaryResponse` has exactly `pulse`, `bullishSignals`, `bearishSignals`, `lastIngestionAt`, `candlesThrough`, `stale`; verified in `SummaryHandlerIntegrationTest.defaultFormatIsJsonWithSignalsPulseAndFreshnessFields` (asserts the JSON body contains no `rsi`/`Rsi`/`RSI` substring) and `watchlistFormatOmitsRsiSectionsAndUnflippedAssets`

(Unlike `/summary/trend-alignment`, `requireFresh`/`stale` here check the *requested* timeframe directly, not hardcoded D1 - `/summary` is genuinely single-timeframe, matching the old app.)

## 13. scan-api

- [x] 13.1 Implement `ScanCondition` (timeframe, trend-state filter, max-days-since-flip) with implicit SuperTrend-only targeting (no indicator-type field) — `ScanCondition`/`ScanRequest`/`ScanOperator` (signal package). tasks.md's checklist for this group didn't call out an HTTP endpoint explicitly (unlike groups 10-12's "Implement GET /api/v1/..." lines), but the change's own `specs/scan-api/spec.md` (already present, ADDED requirements) unambiguously requires `POST /api/v1/scan` as "a standalone public endpoint for composable SuperTrend condition queries" - implemented via `ScanHandler`. JSON-only (no Markdown/Watchlist - the old app never had that for `/scan` either), no `requireFresh` gating (conditions can span multiple/different timeframes, so there's no single timeframe to check, matching the old app)
- [x] 13.2 Implement AND/OR condition combination and result-limit truncation — `ScanService.combine` (pure static core): each condition matched independently against its own timeframe via `SignalQueryService`, then asset-symbol sets intersected (AND) or unioned (OR); results sorted by symbol, then `limit`-truncated last (after combination and sorting, matching the old app's ordering)
- [x] 13.3 Tests: single-condition match, AND-combined cross-timeframe match, days-since-flip filter, limit truncation — `ScanServiceTest` (pure, 8 tests), `ScanServiceIntegrationTest` (Testcontainers), `ScanHandlerIntegrationTest` (Testcontainers + `javalin-testtools`, including malformed-body and negative-limit 400s)

(`spec.md`'s "Standalone capability, not an internal dependency" requirement is already satisfied - confirmed in group 12: `summary-api`/`trend-alignment-api` never call `ScanService`.)

## 14. candle-coverage-api

- [x] 14.1 Implement `GET /api/v1/candles/coverage` (per-timeframe earliest/latest/expectedLatest/upToDate/assetCount) — `CandleCoverageHandler`, keyed by `Timeframe` (serializes as `"D1"`/`"W1"` map keys). `expectedLatest`/`upToDate`/`latest` are delegated to `FreshnessService.evaluate(timeframe)` (same source of truth every other read endpoint's `stale` flag uses) rather than a second, separately-computed latest-close query; new `CandleDao.findEarliestCloseTimeAcrossAllAssets`/`countDistinctAssetsForTimeframe` cover the two remaining fields

## 15. ingestion-trigger-api

- [x] 15.1 Implement `POST /api/v1/internal/ingestion/run` (optional provider/timeframe, defaults to Binance/D1) — `IngestionTriggerHandler` + `IngestionTriggerRequest` (compact-constructor defaulting, matches the old app); missing/empty body treated the same as `{}`
- [x] 15.2 Wire 202-Accepted + run-ID response, and 409 on concurrent-run rejection — required refactoring `PipelineOrchestrator`: it previously only exposed a fully-synchronous `runDaily` (blocks until the whole ingest→indicators→rollup→indicators chain finishes) which doesn't honor 202's "accepted, not yet complete" semantic. Split into `start()` (synchronous - so the concurrent-run check and the new run's id are both known immediately) and `executePhases()`; added `triggerAsync` which does `start()` synchronously then runs `executePhases()` on a virtual thread, returning the run id immediately. `runDaily` (used by `DailyScheduler`) is unchanged in behavior - still fully synchronous, just internally recomposed from the same two pieces
- [x] 15.3 Confirm manual-trigger run records are indistinguishable in shape from scheduled-trigger run records — trivially true by construction: `runDaily` and `triggerAsync` both call the same `start()`/`executePhases()` internals and the same `IngestionRunDao` methods, so there is no "trigger source" column or shape difference to begin with

(Not yet wired into `Main` - the full composition root (DB + all DAOs/services + HTTP server + scheduler) is deferred to group 16, matching the pattern established across groups 9-14.)

## 16. End-to-end validation

- [x] 16.1 Seed an independent dev database and run the full pipeline once end-to-end (ingest → indicators → signals → pulse → rollup → W1) — required first wiring `PipelineOrchestrator`'s phase chain to actually call `SignalStateDetectionService`/`MarketBreadthPulseService` (built in groups 6/7, never wired in) for both D1 and W1, and wiring `Main` as the full composition root (all DAOs/services, every `api` handler, `DailyScheduler`) — neither had been done yet since every prior API group deliberately deferred it. Ran once against a throwaway Postgres + the real Binance API with the full 60-asset seed: completed in ~47s, 15 delisted symbols correctly deactivated, consistent D1+W1 data through every phase, and a repeat trigger was a clean no-op (idempotent). See design.md's new "Group 16 Validation Results" section for full detail
- [x] 16.2 Characterization-test every in-scope endpoint's JSON output against `backend/java`'s equivalent endpoint for the same underlying data (also covers the indicator-level comparison deferred from 2.7 — diff raw `indicator_supertrend` values, not just endpoint JSON) — ran both apps side by side against independent throwaway Postgres instances with the same seed list/backfill window against real live Binance data; `indicator_supertrend` matched exactly on 6758/6758 common rows (ATR/bands/value/direction, modulo the deliberate `SUPERTREND_UP`→`UP` naming change); `/signals`, `/summary` pulse, `/candles/coverage` cross-checked the same way. The only observed differences were fully explained by a timing gap between the two runs crossing a UTC-midnight boundary, not a computation bug. Full detail in design.md
- [x] 16.3 Confirm no Spring, JPA/Hibernate, Lombok, SLF4J, or Micrometer dependency exists anywhere in the module's dependency tree — `mvn dependency:tree`: none present. `slf4j-api` appears as an unused transitive dependency of `HikariCP` (no binding on the classpath, so it's a no-op) — doesn't contradict the logging decision, which is about this codebase's own choice (`System.Logger`)
- [x] 16.4 Document final package layout and any deviations from the design.md sketch back into design.md — added a "Final Package Layout" section (10 packages — `rollup` split out of `ingestion` as its own responsibility, not in the original sketch) and a "Group 16 Validation Results" section covering 16.1/16.2/16.3
