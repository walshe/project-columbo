# Developer Notes

A maintainable, human-oriented map of this codebase — how it's put together, why it's shaped the way it is, and the non-obvious conventions that don't show up just by reading one file at a time. Update this doc whenever you introduce or change one of these conventions; it drifts otherwise.

For point-in-time design rationale on any specific historical decision, see `openspec/changes/*/design.md` (each merged change has one). This doc is the living summary; those are the archived reasoning behind it.

## What this service does

Ingests daily (D1) candles for a fixed list of Binance assets, rolls them up into weekly (W1) candles, computes the SuperTrend indicator on both timeframes, derives a bullish/bearish/unknown trend state and flip events from it, aggregates a market-breadth snapshot, and exposes all of that over a small HTTP API (JSON, Markdown reports, and plain-text watchlists). One indicator, two timeframes, no other technical indicators, no user accounts, no persistence beyond Postgres.

## How it starts

There is no DI container. `Main.main()` is the entire composition root: it constructs every DAO, then every service (passing the DAOs it needs into each constructor), then every HTTP handler (passing the services it needs), registers each handler on a Javalin app, starts the HTTP server, starts `DailyScheduler`, and registers a shutdown hook. If you add a new service or handler, it gets wired here — nowhere else.

Reading `Main.java` top to bottom **is** the dependency graph of the whole application.

## Package tour

- **`shared`** — cross-cutting value types with no dependencies on anything else in the app: `Timeframe` (D1/W1, plus `openTimeFor(closeTime)` — see below), `Provider` (currently only `BINANCE`), `AssetClass` (`CRYPTO`/`STOCK`/`ETF`/`COMMODITY` — every asset onboarded so far is `CRYPTO`), `FinalizedBoundary` (UTC-midnight-today, the cutoff for "is this candle finalized yet"), `TradingViewUrl` (chart deep-link construction).
- **`indicator`** — `Candle` (the OHLCV record everything else is built from), `SuperTrendCalculator` (pure, stateless, `BigDecimal`-based SuperTrend math), `IndicatorComputationService` (persists SuperTrend results per active asset, incrementally).
- **`ingestion`** — `BinanceMarketDataProvider` (the only `MarketDataProvider` actually wired up), `CandleIngestionService` (per-asset incremental D1 fetch + upsert, isolates one asset's failure from the rest), `BackfillStartValidator` (fails fast at startup if `SUPERTREND_BACKFILL_START` doesn't leave enough history for W1's ATR to warm up).
- **`rollup`** — `CandleRollupService`: derives W1 candles from finalized D1 candles, Monday-start weeks, only once a full 7-day week exists.
- **`signal`** — trend-state/flip detection (`SignalStateDetectionService`), the read model behind `/signals` and `/assets/by-state` (`SignalQueryService`), cross-timeframe confluence/retest (`TrendAlignmentService`), condition scanning (`ScanService`).
- **`pulse`** — `MarketBreadthPulseService`: one bullish/bearish/missing tally per timeframe per pipeline run.
- **`pipeline`** — `PipelineOrchestrator` (the phase sequencer — see below), `DailyScheduler`, `ParallelAssetExecutor` (the one place per-asset work is parallelized).
- **`api`** — Javalin HTTP layer. One handler class per capability, each registering its own route(s) and carrying its own `@OpenApi` annotations. Response DTOs are plain records; Markdown/watchlist rendering is separate `*Formatter` classes, not baked into the handler.
- **`persistence`** — hand-written JDBC DAOs (no ORM, no JPA), `DataSourceFactory` (HikariCP, env-configured), `SchemaMigrator` (Flyway).
- **`freshness`** — `FreshnessService`: is a timeframe's data up to date, and if not, is it stale enough to matter (past a 6-hour grace window)? Shared by every read endpoint's `requireFresh` param and every response's `stale`/`lastIngestionAt`/`candlesThrough` fields.

## The pipeline

`PipelineOrchestrator` runs eight phases in a strict, fixed order:

```
ingest (D1) → D1 indicators → D1 signals → D1 pulse → W1 rollup → W1 indicators → W1 signals → W1 pulse
```

Phases always run in this sequence and each phase's writes are fully committed before the next phase reads them — W1 rollup depends on all of D1 being ingested and indicator-computed first, etc. The **only** parallelism is *within* a phase: `IndicatorComputationService`/`SignalStateDetectionService`/`CandleIngestionService` fan out per-asset work across virtual threads via `ParallelAssetExecutor` (one virtual thread per asset, not a fixed pool — cheap enough at this scale that pooling isn't worth the complexity).

Two ways to trigger a run:
- **Scheduled**: `DailyScheduler` fires once a day at a fixed UTC time (00:05), re-scheduling itself after each run rather than using a fixed-rate timer (so a slow run never overlaps the next one).
- **Manual**: `POST /api/v1/internal/ingestion/run`.

Both go through the exact same `PipelineOrchestrator` methods, so the resulting `ingestion_run` row is identical in shape either way — there's no separate "manual run" code path to keep in sync.

A run is recorded `RUNNING` synchronously (so a concurrent-run 409 and the new run's id are both known immediately to an HTTP caller), then its phases execute on a background virtual thread. Two runs for the same `(provider, timeframe)` can never both be `RUNNING` — a plain in-app check (`isRunning`) is a fast-path rejection, but the actual guarantee is a **partial unique index** on `ingestion_run (provider, timeframe) WHERE status = 'RUNNING'` (migration V9). If two requests race past the fast-path check, the database itself rejects the second `INSERT` with a unique-violation, which `IngestionRunDao.start()` catches and converts into the same 409.

## Domain conventions worth knowing before you touch anything

**Candle boundaries.** A candle's `closeTime` is its finalized-at instant (`open + span - 1ms`, matching Binance's own kline convention exactly — D1 closes at `23:59:59.999` same day, W1 closes at `23:59:59.999` on the Sunday). `FinalizedBoundary.utcMidnightToday(now)` is the cutoff used everywhere to decide "is this candle actually closed yet" — never trust a candle that closes after that boundary.

**`Timeframe.openTimeFor(closeTime)`** is an *exact* reverse-derivation (not an estimate) of a candle's open time from its close time, relying on the invariant above. It exists because charting tools like TradingView position/label a candle by its **open** time, not its close — so anything user-facing that's timestamped by a flip's close time (originally `SignalState.closeTime()`) gets converted through this before being shown, keeping "days since flip" consistent with where a human would actually see the flip marker land on a chart. D1 is visually unaffected by this (open/close land on the same calendar day); W1 shifts by 6 days.

**SuperTrend parameters are fixed**: ATR length 10, multiplier 2.0, `hl2` source (`(high+low)/2`), Wilder smoothing. There's no per-asset or per-timeframe override — this matches the original app's defaults and TradingView's own default SuperTrend preset exactly (verified by independently reimplementing the calculation in Python against raw ingested OHLC and TradingView chart screenshots during development — see conversation history / commit messages around "weekly supertrend" if you need to re-verify this).

**Trend state and flip events.** `TrendState` is `BULLISH`/`BEARISH`/`UNKNOWN` (the latter only during ATR warm-up). A `SignalEvent` (`BULLISH_REVERSAL`/`BEARISH_REVERSAL`/`NONE`) is recorded on the candle where the direction actually flips — the transition *out of* `UNKNOWN` into a real state is `NONE`, not a reversal, since there's no real prior trend to have reversed from.

**Freshness vs. staleness are different questions.** "Up to date" means the latest ingested candle's close time has reached the expected boundary for right now. "Stale beyond grace window" means it's been up-to-date-failing for more than 6 hours — this exists to tolerate the normal lag between "candle technically closed" and "today's pipeline run finished," not to permanently excuse a real gap once the pipeline catches up. `requireFresh=true` on a read endpoint checks the second, not the first.

**Scan conditions implicitly target SuperTrend.** Unlike the prior (`backend/java`) implementation, there's no indicator-type field on a scan condition — SuperTrend is the only indicator in this system. Don't add a generic "indicator type" concept back in unless a second indicator actually gets added.

## HTTP layer conventions

- Javalin (not Spring MVC), configured once in `ApiServer.create()`: JSON via a shared Jackson `ObjectMapper` (`JsonSupport`), case-insensitive enum query-param converters, an `@OpenApi`-annotation-driven OpenAPI spec (`/openapi`) + Swagger UI (`/swagger`), and centralized exception→status-code mapping.
- **Every real route handler method needs an `@OpenApi` annotation**, even if you think it's obvious. The Swagger UI plugin only discovers "versions" to serve if at least one `@OpenApi`-annotated method exists anywhere — skip it on a new handler and the whole Swagger UI silently breaks with "No API definition provided," not just that one endpoint's docs.
- Domain exceptions (`IngestionAlreadyRunningException` → 409, `StaleDataException` → 503 + `Retry-After` header) are mapped once in `ApiServer.registerErrorMapping`. Javalin's own `HttpResponseException` subclasses (`BadRequestResponse`, etc.) are handled automatically — no registration needed for those.
- Markdown/watchlist rendering lives in separate `*Formatter` classes (`SummaryFormatter`, `TrendAlignmentFormatter`, `SignalTextFormatting`), not inline in handlers — keeps the three response formats (JSON/Markdown/watchlist) each independently testable and independently changeable.
- Report-shaped endpoints (`/summary`, `/summary/trend-alignment`) echo back the filters that produced them (`timeframe`/`maxRetestAgeDays`, plus `assetClass` when set) in every format, JSON included — a Markdown report meant to be read standalone (pasted into chat, a notebook) needs to say what it was generated with. List endpoints (`/signals`, `/assets/by-state`, `/scan`) don't do this — same info is implicit in the request the caller just made.

## Persistence conventions

- Plain JDBC, no ORM. Every DAO takes a `DataSource` in its constructor and opens/closes its own `Connection` per method — no shared transaction/session object threaded through call chains.
- **Upserts are idempotent and self-logging.** `CandleDao.upsert`/`SuperTrendIndicatorDao.upsert` return an `UpsertOutcome` (`INSERTED`/`UPDATED`/`UNCHANGED`), and a revision (existing row's values differ from the new ones) is logged at `WARN` — this is meant to be rare and worth a human's attention, not routine.
- **Incremental computation, not full recompute.** `IndicatorComputationService`/`SignalStateDetectionService` recompute the SuperTrend series from full in-memory candle history every run (cheap, pure arithmetic — needed for correct trend continuity, since SuperTrend depends on its own prior value), but only *persist* rows after the asset's last stored close time, so DB writes stay proportional to new candles, not total history.
- Migrations are plain versioned SQL under `src/main/resources/db/migration/` (`V1__...sql`, `V2__...sql`, ...), run by Flyway at startup (`SchemaMigrator.migrate`, called from `Main` before anything else touches the database). Never edit an already-applied migration — add a new one.

## Testing conventions

- Roughly half the test suite is integration tests (`*IntegrationTest`, spinning up a real, throwaway Postgres via Testcontainers — no manual container setup needed), half is pure unit tests against static or otherwise DB-free methods.
- **Prefer a pure, package-private static method for anything with real logic**, with the public method being a thin wrapper that does the I/O and delegates. `SignalQueryService.summarize(...)`, `TrendAlignmentService.align(...)`, `FreshnessService.evaluate(...)` (the 4-arg static overload) are the pattern: the unit test constructs the inputs directly (no DB), the integration test only needs to prove the DB-backed public method feeds the pure method correctly.
- **`javalin-testtools` gotcha**: `HttpClient.post(path, Object)` always JSON-serializes its second argument via Jackson — you cannot pass a pre-built `okhttp3.RequestBody` there and expect it sent as a literal body (it gets bean-serialized instead, producing bogus fields). Use `client.request(path, builder -> builder.post(RequestBody.create(json, MediaType.get("application/json"))))` when you need to send a specific raw body.
- **Don't share a static Javalin `app` across multiple `JavalinTest.test()` calls** in one test class — Javalin's plugin registration throws `KeyAlreadyExistsException` on the second call. Build a fresh app per test via a small `newApp()`/`appWithFixedClock()` factory method instead (see any existing `*HandlerIntegrationTest` for the pattern).
- Testcontainers Postgres is shared across every `@Test` in a class. Tables scoped to seeded test data (most of them) are naturally isolated by symbol/asset id, but a handful of tables are genuinely global per timeframe (`market_breadth_snapshot`, `ingestion_run` — one row per timeframe/close-time, not per asset). Tests touching those either route themselves onto an untouched `Timeframe` or explicitly `DELETE FROM <table>` at the top of the test — check for this pattern before assuming a clean slate.
- **`@ParameterizedTest`/`@CsvSource`/`@MethodSource`** (vanilla JUnit, no extra dependency — ships in `junit-jupiter-params`, already pulled in transitively) is the default choice whenever a test asserts the exact same behavior across a small set of interchangeable inputs (see `TimeframeTest` for the pattern: one case per `Timeframe`). Don't reach for JUnit Pioneer's `@CartesianTest` unless there's a genuine 2+-dimension input matrix to collapse — there isn't one in this suite today.
- **JUnit Pioneer** (`org.junit-pioneer:junit-pioneer`) is in the project narrowly for `@SetEnvironmentVariable`/`@ClearEnvironmentVariable` — the only practical way to test code that reads `System.getenv()` directly (`IngestionConfig`, `DataSourceFactory`, `Main.httpPort()`) without refactoring production code just to make it injectable. See `IngestionConfigTest` for the pattern. Two things had to be fixed to make it work at all, not just added cleanly — both matter if you ever touch JUnit versions:
  - Pioneer 2.3.0 transitively pulls `junit-platform-launcher:1.11.2` (the pre-rebrand Platform version) while the rest of this project is on the unified `6.x` line; that mismatch crashes test discovery outright (`OutputDirectoryCreator not available`). Fixed by declaring `junit-platform-launcher` directly at `${junit.version}` in `pom.xml` so Maven's dependency mediation picks it over the transitive one — if you ever bump `junit.version`, bump this alongside it, and re-run `mvn dependency:tree | grep junit` to confirm everything still resolves to one version.
  - The env-var reflection trick needs `--add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED` on the forked test JVM (Surefire's `argLine`) to work under the module system at all.
  - Pioneer's own docs call the env-var mechanism itself fragile (reflection into JDK internals, OS/JVM-version sensitive) — treat it as narrowly justified for this one use case, not a general-purpose testing library to reach for by default.
- **`mvn verify -Pe2e`** runs a fourth, opt-in test tier: `PipelineEndToEndIT` builds this module's actual Docker image via Testcontainers' `ImageFromDockerfile` and runs it against a real Postgres container plus a WireMock container stubbing Binance's klines API, then drives the full pipeline through real HTTP exactly as a client would. Not part of `mvn test`/plain `mvn verify` — building the image and running three containers is much slower than everything else here (~3-5 minutes). This is the automated, repeatable counterpart to the one-time manual "Group 16" validation in `openspec/changes/supertrend-core-java25-rewrite/design.md`, which hit the real Binance API and can't safely be repeated in CI.
  - Candle fixtures are a fixed 2020 date range, not "N days before now" — candles from a fixed point in the past are always finalized, so the fixtures never rot as real time passes.
  - `ImageFromDockerfile` gotcha: `.withDockerfile(Path)` (a *local filesystem* path) looks like the obvious way to point at the Dockerfile, but it requires an internal "base directory" that's never set in this Transferable-upload build mode and fails with a bare `NullPointerException` before a single build step runs. The fix is `.withFileFromPath("Dockerfile", Path.of("Dockerfile"))` (transferring it into the build context the same way as `pom.xml`/`src`) plus `.withDockerfilePath("Dockerfile")` (a path *within that uploaded context*, not the local filesystem).
  - `BinanceMarketDataProvider`'s base URL is overridable via `SUPERTREND_BINANCE_BASE_URL` (defaults to the real API) specifically so this test can point the app container at the WireMock stub instead.

## Logging

SLF4J (`org.slf4j.Logger`/`LoggerFactory`), bound at runtime to `slf4j-simple`, configured via `src/main/resources/simplelogger.properties`. Every class gets its own logger via `LoggerFactory.getLogger(ClassName.class)`. This also backs Javalin/Jetty's own internal logging — without a binding present those logs are silently dropped (this is exactly what Javalin's "you don't have a logger in your project" startup warning means if you ever see it again after removing the dependency).

Use `{}` placeholders (SLF4J), not string concatenation or `String.format` in the message — and when logging an exception, pass it as the trailing argument after all the placeholder values (`LOG.error("Failed for {}", id, exception)`), never call `.getMessage()` yourself — the trailing `Throwable` gets its own stack trace printed by the backend.

## Known gotchas (keep this section updated)

- **`javap -p -c` decompilation of the actual downloaded jar is the only reliable way to determine a third-party library's true runtime behavior** when docs/web search/GitHub main branch disagree with what you're observing. This has come up repeatedly with Javalin's OpenAPI/Swagger plugins and slf4j-simple's config file contract — both were only pinned down this way.
- **Shell `cwd` can silently reset between tool calls** in some agent/CI environments when re-sourcing SDKMAN — always `cd` explicitly to the project root before build/test commands rather than assuming the previous `cd` stuck.
- **`docker compose down -v` doesn't stop profile-gated services** (like the `app` service, gated behind `--profile prod`). If you see "Network ... Resource is still in use" after a `down -v`, check for a leftover profile-gated container first (`docker ps -a`) before assuming something's stuck.
- **A weekly candle's flip "8 days ago" vs. TradingView showing it 14 days ago** was a real, since-fixed confusion — see the `Timeframe.openTimeFor` note above. If you ever see a report's day-count not matching where a chart shows the same flip, this is the first thing to check.
- **Every enum used as a Javalin query param needs an explicit `config.validation.register(...)` in `ApiServer.create()`** — Javalin does not auto-convert arbitrary enum types. Forgetting one doesn't fail at startup or return a clean 400; the handler throws `MissingConverterException` at request time, which the catch-all exception mapper turns into a generic 500 ("Internal server error"), with the real cause (`Can't convert to <Type>. Register a converter...`) only visible in the server log. Surfaced when `AssetClass` was added as a query param but not registered — every handler using it returned 500 until the registration was added.

## Where to go for more

- `openspec/changes/*/design.md` — the full design rationale behind every major historical decision (why no Spring, why Javalin, why plain JDBC, why no generic indicator-type abstraction, etc.), one per merged change.
- `README.md` — how to run it, environment variables, the HTTP endpoint list.
