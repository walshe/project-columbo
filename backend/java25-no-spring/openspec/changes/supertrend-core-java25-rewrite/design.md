## Context

`backend/java/` (Spring Boot 4, Java 17) implements SuperTrend as one of several indicators (RSI, Elder Impulse/Thermometer — the latter already disabled in production) behind a full Spring stack: Spring MVC, Spring Data JPA/Hibernate, Bean Validation, Actuator, springdoc-openapi, `@Async`/custom thread pools, `@Scheduled` cron, and Flyway. A research pass over that codebase (class names, line numbers on file) informed every decision below; see the change's originating conversation for the full trace. Three scope questions were explicitly decided by the owner before this design was written:

1. **ATR multiplier**: follow the shipped code (`atrLength=10`, `multiplier=2.0`), not the stale docs (`3.0`).
2. **W1 (weekly) SuperTrend**: in scope, including the D1→W1 rollup step — required because `/summary/trend-alignment` depends on it.
3. **`/scan`**: in scope, trimmed to `SUPERTREND`-only conditions — because `/summary` (not `/summary/trend-alignment`) calls the scan engine internally to combine SuperTrend state with other conditions, so the engine is a load-bearing internal dependency, not just a standalone public endpoint.

This is a **rewrite, not a port**. The old package structure (`walshe.projectcolumbo.*`) and several of its abstractions exist to support multiple indicator types (RSI, Elder Impulse/Thermometer) that are entirely out of scope here — e.g. `SignalStateAssetProcessor` is generic over `IndicatorType` specifically to share code with RSI; `MarketPulseService` switches over all `IndicatorType` values; `signal_state`/`indicator_type` was deliberately designed multi-indicator-ready per its original story doc. None of that generality is needed for a SuperTrend-only system, and carrying it forward would reintroduce exactly the kind of abstraction-for-a-future-that-may-not-come that this rewrite is meant to avoid. Package names, class boundaries, and internal seams should be designed fresh around SOLID principles for a single-indicator system, using the old code only as a behavioral reference (algorithm, API contracts, edge cases), never as a structural template.

## Goals / Non-Goals

**Goals:**
- Faithfully reproduce SuperTrend's computed output (values, direction, flips) and the read-API contracts (`/signals`, `/assets/by-state`, `/summary`, `/summary/trend-alignment`, `/scan`, `/supertrend-market-pulse[/history]`, `/candles/coverage`, ingestion trigger) for SuperTrend-only behavior.
- No Spring Framework anywhere in the dependency graph.
- Prefer JDK-native facilities (`java.net.http.HttpClient`, `com.sun.net.httpserver.HttpServer`, `java.sql`/JDBC, `System.Logger`, virtual threads / structured concurrency) over adding a dependency, but this is not a hand-rolling purity test — a small, well-established library is fine, and preferred over hand-written code, when it's genuinely the pragmatic choice (e.g. JSON parsing, structured logging). "No Spring, minimal libraries" means avoid framework weight and unnecessary dependencies, not "reimplement anything the JDK doesn't ship."
- Apply SOLID principles: small, single-responsibility classes; dependencies expressed as interfaces at seams that plausibly vary (market data provider, persistence, clock/time source); no framework magic (reflection-based DI, proxies, annotation-driven behavior) — dependencies wired explicitly in a composition root.
- Use Java 25 virtual threads / structured concurrency for per-asset parallel work wherever it removes complexity (thread pool sizing, `@Async` proxying, `CompletableFuture` fan-out/join boilerplate) rather than because it's fashionable — if a phase is inherently sequential or single-item, don't parallelize it just to use the feature.

**Non-Goals:**
- RSI, EMA, MACD, Elder Impulse, Elder Thermometer — no code, no schema, no enum values for any of these.
- CoinGecko provider (confirmed dead code in the old impl — `MarketProvider` enum only ever had `BINANCE` wired up).
- Actuator-style health/metrics endpoints, OpenAPI doc generation/UI, Micrometer.
- Replaying the old Flyway migration history (including its mid-project enum renames) — the new schema is authored correctly from scratch.
- Sharing a database/schema with `backend/java` — this is an independently-owned Postgres schema.
- Feature parity beyond SuperTrend — anything the old app does for other indicators is explicitly out.
- A migration/cutover plan for existing consumers — this is a parallel implementation for evaluation, not a production replacement (yet).

## Decisions

**Language/runtime**: Java 25, no Spring. Package root `walshe.projectcolumbo.supertrend` (or similar — finalized during implementation, not fixed here) with packages organized by responsibility, not by "layer-then-indicator-type": e.g. `indicator` (SuperTrend math), `ingestion` (provider + persistence + rollup), `signal` (state/flip detection), `pulse` (breadth snapshots), `pipeline` (orchestration), `api` (HTTP handlers + routing), `persistence` (JDBC DAOs), `freshness` (staleness evaluation). No package is parameterized over "which indicator" — there is exactly one, so that abstraction axis is deleted entirely, not generalized-then-restricted.

**HTTP layer**: `com.sun.net.httpserver.HttpServer` (JDK-built-in) with a small hand-written router (path + method → handler), since the endpoint count (~9) doesn't justify a framework. Query-param parsing, content negotiation (`format=JSON|MARKDOWN|WATCHLIST`), and error-body writing are explicit code, not annotations. *Alternative considered*: a micro-framework (Javalin, Helidon Nima). Rejected for now since the JDK's built-in server is genuinely sufficient at this endpoint count; revisit if the hand-rolled router becomes unwieldy in practice.

**JSON (de)serialization**: a small, well-established library (e.g. Jackson core standalone — not Spring-coupled, so it's not a "no Spring" violation), not hand-rolled. Per the owner's explicit guidance, "no Spring, minimal libraries" is about avoiding framework weight, not refusing every dependency on principle — JSON parsing is exactly the kind of thing a library is genuinely more pragmatic for (correct number/date-time/escaping handling for free) than hand-writing and maintaining a bespoke writer/parser for both response DTOs and Binance's kline responses (see market-data-ingestion).

**Persistence**: plain JDBC (`java.sql`), no JPA/Hibernate. DAOs are hand-written per aggregate (`AssetDao`, `CandleDao`, `SuperTrendIndicatorDao`, `SignalStateDao`, `MarketBreadthSnapshotDao`, `IngestionRunDao`), using `PreparedStatement` + a thin row-mapper. Optimistic-lock version columns from the old schema are **not** carried forward — the old `RetryUtil` that would have used them was confirmed dead code (zero call sites), and virtual threads make per-asset write serialization cheap enough that the concurrency problem the version column solved doesn't need solving the same way (see Risks). Postgres native enums are used for `timeframe`, `provider`, `supertrend_direction`, `trend_state`, `signal_event`, `ingestion_run_status` — `indicator_type` is **not** created as a column/enum at all in `signal_state` (only one value would ever exist; the old schema's "multi-indicator-ready" design intent doesn't apply to a single-indicator system, so the column is simply omitted rather than kept-but-constrained).

**Schema migrations**: Flyway is retained as a plain library (it has no Spring dependency) — a small, known tool is a better use of the "minimal libraries" budget than hand-rolling migration tracking. Migrations authored fresh for the trimmed schema; no attempt to replay or reference the old app's migration history.

**Market data provider**: only Binance (`java.net.http.HttpClient` + hand-written kline-array parsing), matching what's actually active in production. No CoinGecko stub — if a second provider is ever needed, the `MarketDataProvider` interface (one method: fetch daily candles for a symbol + time range) is the seam, added when there's a real second implementation, not speculatively now.

**Concurrency**: virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) and/or structured concurrency (`StructuredTaskScope`) for per-asset fan-out within a pipeline phase (indicator computation, ingestion per asset) — this directly replaces the old `@Async` + custom bounded `ThreadPoolTaskExecutor` + `CompletableFuture.allOf(...).join()` pattern, and removes the pool-sizing config (`AsyncProperties`) and pool-depth metrics (`ExecutorPoolMetrics`) that existed only to manage a bounded pool. Structured concurrency's scope-based cancellation/error-propagation also replaces the manual "collect all futures, fail if any errored" logic. The pipeline's *phase* ordering (ingest → indicators → signal detection → pulse → W1 rollup → W1 processing) stays strictly sequential — only work *within* a phase (per-asset) is parallelized. No transaction-propagation subtleties (the old code's `REQUIRES_NEW` self-invocation workaround) exist here since there's no proxy-based framework: transaction boundaries are explicit `Connection`/commit blocks in code.

**Scheduling**: a simple daily trigger — either a `ScheduledExecutorService` computing time-until-next-run, or (if simplicity wins) an explicit loop with `Thread.sleep` until the next UTC 00:05 boundary. No cron-expression parsing library; the schedule is fixed (once daily), so a next-fire calculation is a few lines.

**Validation**: manual guard clauses (mirroring the old codebase's already-hand-written `ScanValidator`) instead of Bean Validation annotations — this was already the pattern for domain-specific rules in the old code; it now extends to the request-shape checks that used to be `@NotNull`/`@NotEmpty`.

**Logging**: `System.Logger` (JDK built-in, `java.lang.System.Logger`), no SLF4J/Logback dependency.

**Determinism/correctness principles carried forward verbatim** (from `ARCITECTURE.md`, these are behavioral requirements, not implementation choices): all computation operates only on finalized candles (never a partial/in-progress candle); all signal events are anchored to finalized candle close time in UTC, never wall-clock detection time; persistence upserts are idempotent on `(asset, timeframe, close_time)` and log-warn (never silently overwrite or duplicate) when a re-computed value differs from what's stored.

## Risks / Trade-offs

- **[Risk]** Hand-rolled HTTP routing/dispatch could accumulate ad-hoc special cases as endpoints are added (content negotiation, error mapping) and end up less consistent than a framework's conventions. → **Mitigation**: extract a small shared `RequestContext`/`ResponseWriter` pair early so every handler follows the same shape; revisit if this grows past ~2-3 handlers' worth of duplicated logic.
- **[Risk]** Dropping the optimistic-lock `version` column removes a safety net for concurrent writes to the same `(asset, timeframe, close_time)` row, even though it was unused (`RetryUtil` had zero call sites). If per-asset work is ever parallelized *across* pipeline runs (not just within one run), this could resurface as silent lost updates. → **Mitigation**: the pipeline-orchestration concurrency guard (reject a new run while one is `RUNNING` for the same provider+timeframe) is preserved from the old design specifically to make this a non-issue; if that guarantee is ever relaxed, revisit adding version-checked writes.
- **[Risk]** Structured concurrency APIs are relatively new (finalized/near-finalized across recent JDKs) and less battle-tested at scale than Spring's thread-pool machinery. → **Mitigation**: scope is small (~60 seeded assets per the old `V11` migration), so this is a low-volume workload where any rough edges are cheap to hit and fix early.
- **[Trade-off]** No shared schema/data with `backend/java` means seed data (assets, historical candles) must be independently loaded for this implementation to be usable/testable — there is no "free" data reuse. This is accepted as the cost of true independence between the two implementations.

## Migration Plan

Not applicable in the deployment-cutover sense — this is a new, independent parallel implementation for evaluation, with no existing consumers to migrate and no shared data store with `backend/java`. The practical rollout is purely internal to this repo:
1. Stand up schema + seed data independently (own Postgres instance/schema).
2. Implement capabilities in the dependency order laid out in tasks.md.
3. Validate each read API's output against the equivalent `backend/java` endpoint for the same underlying data (characterization testing).
4. No rollback concerns beyond normal within-change git revert, since nothing external depends on this implementation yet.

## Open Questions

- **Exact package names**: sketched above (`indicator`, `ingestion`, `signal`, `pulse`, `pipeline`, `api`, `persistence`, `freshness`) as a starting point, not a final layout — expected to be refined once real classes exist and SOLID seams become concrete. (Already refined once: a `shared` package was added for cross-cutting value types like `Timeframe`/`Provider` — see tasks.md group 1 notes.)
- **Which JSON library**: leaning Jackson core standalone (widely known, not Spring-coupled) but the exact choice isn't locked in — decide when the first API capability's DTOs are drafted.

**Resolved**: seed data reuses `backend/java`'s ~60-asset list (done, see tasks.md 3.5); Flyway is retained (see Decisions).

**Library policy** (owner guidance): "no Spring, minimal libraries" means avoid framework weight and unnecessary dependencies — it does not mean hand-rolling everything the JDK doesn't ship. Add a small, well-established library when it's genuinely the pragmatic choice (JSON parsing, structured logging, etc.), same reasoning as retaining Flyway and HikariCP.
