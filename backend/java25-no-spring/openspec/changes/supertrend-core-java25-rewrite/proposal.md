## Why

The existing backend (`backend/java/`, Spring Boot) implements SuperTrend as one indicator among several (RSI, Elder Impulse/Thermometer — the latter already disabled), wrapped in a full Spring stack (MVC, Data JPA, Validation, Actuator, springdoc, `@Async`/`@Scheduled`) that adds considerable framework surface for what is, at its core, a fairly small deterministic computation plus a handful of read APIs. The owner wants to validate how much simpler and more explicit this system becomes when rebuilt as a plain Java 25 application — no Spring, minimal third-party libraries, SOLID principles — scoped *only* to SuperTrend and the APIs that depend on it. This is a from-scratch rewrite, not a port: package structure, class boundaries, and internal design are free to improve on the old implementation wherever the old code was awkward (e.g. the generic multi-indicator abstractions built for RSI/Elder that no longer need to exist).

## What Changes

- New standalone application at `backend/java25-no-spring/`, targeting Java 25, with no Spring Framework dependency.
- Reimplements the SuperTrend indicator (ATR-based bands, direction, flip detection) for both D1 and W1 timeframes, matching the shipped algorithm and defaults (`atrLength=10`, `multiplier=2.0` — the docs' `3.0` is stale and not followed).
- Reimplements the minimum data pipeline needed to feed it: Binance daily candle ingestion, D1→W1 rollup, idempotent upsert persistence, and a sequential multi-phase pipeline run with run-status tracking, using **virtual threads / structured concurrency** for per-asset parallel phases instead of a managed thread pool.
- Reimplements SuperTrend-only signal-state detection (bullish/bearish/unknown, flip events) and SuperTrend-only market breadth (pulse) snapshots.
- Reimplements the following REST APIs, trimmed to SuperTrend-only behavior:
  - `GET /api/v1/signals`, `GET /api/v1/assets/by-state`
  - `GET /api/v1/summary/trend-alignment` (confluence + retest, JSON/Markdown/Watchlist)
  - `GET /api/v1/summary` (SuperTrend bullish/bearish lists + pulse — **BREAKING**: drops the RSI-scan branch and `bullishRsiOverbought`/`bearishRsiOversold` fields present in the old response)
  - `POST /api/v1/scan` (condition matching — **BREAKING**: only `IndicatorType.SUPERTREND` conditions are valid; RSI/Elder conditions are rejected, not just deprioritized)
  - `GET /api/v1/supertrend-market-pulse`, `GET /api/v1/supertrend-market-pulse/history`
  - `GET /api/v1/candles/coverage`
  - `POST /api/v1/internal/ingestion/run`
- Persistence via plain JDBC (no JPA/Hibernate), Postgres, schema trimmed to only the tables/enum values SuperTrend needs (no RSI/EMA/MACD/Thermometer tables or enum values).
- Explicitly **out of scope**: RSI, EMA, MACD, Elder Impulse/Thermometer (any form), CoinGecko provider (dead code in the old impl), Actuator-style health/metrics endpoints, OpenAPI doc generation, and Flyway migration history replay (new schema is defined fresh, not migrated from the old one).
- Explicitly **carried forward as design principles** (from `ARCITECTURE.md`): deterministic computation with no partial-candle math, all signal events anchored to finalized-candle close time in UTC, idempotent upserts with warn-on-revision (never silent overwrite, never duplicate rows).

## Capabilities

### New Capabilities
- `supertrend-indicator-core`: SuperTrend calculation (TR/ATR/bands/direction/flip) for D1 and W1, pure and deterministic, `BigDecimal`-based.
- `market-data-ingestion`: Binance candle fetch, incremental time-windowing, idempotent candle persistence, D1→W1 rollup.
- `signal-state-detection`: SuperTrend-only bullish/bearish/unknown state derivation and flip-event emission from indicator history.
- `market-breadth-pulse`: SuperTrend-only bullish/bearish/missing asset counts per timeframe, snapshotted per run, with history retrieval.
- `pipeline-orchestration`: sequential multi-phase pipeline run (ingest → indicators → signal detection → pulse → W1 rollup → W1 indicators/signals/pulse), concurrency guard against overlapping runs, run-status tracking (RUNNING/SUCCESS/PARTIAL/FAILED), virtual-thread-based per-asset parallelism within a phase.
- `data-freshness`: shared staleness/freshness evaluation (expected-latest-candle boundary, grace window, `requireFresh` 503 gating) used across the read APIs below.
- `signals-api`: `GET /api/v1/signals` and `GET /api/v1/assets/by-state`.
- `trend-alignment-api`: `GET /api/v1/summary/trend-alignment` (cross-timeframe confluence + retest, JSON/Markdown/Watchlist output formats).
- `summary-api`: `GET /api/v1/summary` (SuperTrend-only bullish/bearish lists + pulse; no RSI scan).
- `scan-api`: `POST /api/v1/scan`, condition matching restricted to `SUPERTREND` conditions.
- `candle-coverage-api`: `GET /api/v1/candles/coverage`.
- `ingestion-trigger-api`: `POST /api/v1/internal/ingestion/run`.

### Modified Capabilities
- None — this is a new, independent application (`backend/java25-no-spring/`) with its own OpenSpec instance. It does not modify any capability defined in `backend/java/openspec/specs/`.

## Impact

- **New code**: entirely new module at `backend/java25-no-spring/` — no changes to `backend/java/` or `backend/supabase/`.
- **New dependencies**: none required beyond the JDK (Java 25) and a JDBC driver for Postgres; Flyway may optionally be retained for migrations since it's not Spring-coupled (decide in design.md).
- **Removed/not ported**: Spring Boot and all its starters (web, data-jpa, validation, actuator, springdoc), Hibernate/JPA, Lombok, SLF4J/Logback (replaced by `System.Logger`), Micrometer, the old `@Async`/custom-executor parallelism machinery, RSI/EMA/MACD/Elder Impulse/Thermometer domain code, CoinGecko provider, `RetryUtil` (confirmed dead code in the old impl).
- **Data**: a new, independently-owned Postgres schema (or schema namespace) — not shared with `backend/java`'s database, to avoid coupling two independently-evolving implementations to one schema.
- **Consumers**: none yet — this is a parallel implementation for evaluation, not a replacement deployed to existing consumers of `backend/java`'s APIs.
