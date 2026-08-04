# SuperTrend Core — Java 25, No Spring

A from-scratch, standalone reimplementation of the SuperTrend indicator and its dependent read/trigger APIs, built with plain Java 25 (no Spring Framework, minimal third-party libraries), applying SOLID principles. It's independent from `backend/java` — no shared code, schema, or database — a parallel evaluation of how much simpler the system becomes without a framework.

The rewrite (`openspec/changes/supertrend-core-java25-rewrite/`) is functionally complete: the full pipeline (ingest → D1 indicators → D1 signals → D1 pulse → W1 rollup → W1 indicators → W1 signals → W1 pulse) and every read/trigger endpoint below are implemented and merged.

**New to this codebase?** See [`developer-notes.md`](developer-notes.md) for an architecture/conventions overview — package responsibilities, the pipeline's phase model, domain gotchas (candle boundaries, flip timing, freshness vs. staleness), and testing/logging conventions.

## Package layout

Ten packages under `walshe.projectcolumbo.supertrend`:

- `indicator` — `Candle`, `SuperTrendCalculator`, `IndicatorComputationService`
- `ingestion` — Binance market data provider, `CandleIngestionService`, backfill validation/config
- `rollup` — D1→W1 candle rollup
- `signal` — trend-state/flip detection, the signals read model, cross-timeframe confluence, condition scanning
- `pulse` — market-breadth snapshot computation
- `pipeline` — orchestration (`PipelineOrchestrator`), daily scheduling, per-asset parallel execution
- `api` — Javalin HTTP layer: one handler + response DTO(s) per capability
- `persistence` — hand-written JDBC DAOs, connection pool, schema migration
- `freshness` — staleness evaluation shared by every read endpoint
- `shared` — cross-cutting value types (`Timeframe`, `Provider`)

See `openspec/changes/supertrend-core-java25-rewrite/design.md` for the full rationale behind every major decision (why no Spring, why Javalin for HTTP, why plain JDBC, etc.) and its "Final Package Layout" / "Group 16 Validation Results" sections for how this was validated against real market data and against `backend/java`'s equivalent output.

## Running it locally (dev)

Start Postgres via Compose, then run the app directly against it:

```sh
docker compose up -d          # starts only postgres
```

```sh
export SUPERTREND_DB_URL=jdbc:postgresql://localhost:5432/supertrend_core
export SUPERTREND_DB_USER=postgres
export SUPERTREND_DB_PASSWORD=postgres
export SUPERTREND_BACKFILL_START=2026-01-01T00:00:00Z   # see "Environment variables" below

mvn compile exec:java -Dexec.mainClass=walshe.projectcolumbo.supertrend.Main
```

(Or run `walshe.projectcolumbo.supertrend.Main` directly from your IDE with the same env vars set, or package + run the jar — see below.)

To run the packaged jar without Maven:

```sh
mvn clean package -DskipTests
java -jar target/supertrend-core.jar   # requires target/lib/ alongside it - see Dockerfile
```

## Running it fully containerized

```sh
docker compose --profile prod up -d --build
```

This builds the app image locally, starts Postgres, and starts the app against it — matching `backend/java`'s Dockerfile/compose pattern. To run against a pre-built image instead of building locally (e.g. on a target deployment machine):

```sh
docker compose -f compose.yaml -f compose.prod.yaml --profile prod up -d
```

## Environment variables

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `SUPERTREND_DB_URL` | no | `jdbc:postgresql://localhost:5432/supertrend_core` | Postgres JDBC URL |
| `SUPERTREND_DB_USER` | no | `postgres` | Postgres user |
| `SUPERTREND_DB_PASSWORD` | no | `postgres` | Postgres password |
| `SUPERTREND_BACKFILL_START` | **yes** | — | ISO-8601 timestamp; how far back to backfill candles. Must be far enough in the past to give W1 SuperTrend's ATR at least ~147 days (~20 weekly candles) to warm up — startup fails fast (`BackfillStartValidator`) if it isn't. |
| `SUPERTREND_HTTP_PORT` | no | `8080` | HTTP port the API listens on |

## HTTP endpoints

All under `/api/v1`, JSON by default unless noted.

| Method | Path | Notes |
|---|---|---|
| `GET` | `/signals` | `timeframe` required; `state`, `sort`, `requireFresh` optional |
| `GET` | `/assets/by-state` | `timeframe`, `state` required; no freshness gating |
| `GET` | `/summary` | `timeframe` required; `format` (`JSON`/`MARKDOWN`/`WATCHLIST`), `requireFresh` optional; response echoes back `timeframe` in every format |
| `GET` | `/summary/trend-alignment` | `format`, `maxRetestAgeDays` (default 7), `requireFresh` optional; freshness always checked against D1; response echoes back `maxRetestAgeDays` in every format |
| `POST` | `/scan` | JSON body: `operator` (`AND`/`OR`), `conditions[]` (`timeframe`, `state`, optional `maxDaysSinceFlip`), optional `limit` |
| `GET` | `/candles/coverage` | per-timeframe earliest/latest/expected-latest/up-to-date/asset-count |
| `POST` | `/internal/ingestion/run` | optional JSON body: `provider`/`timeframe` (default `BINANCE`/`D1`); 202 + run id, 409 if already running for that provider+timeframe |

Plus `GET /openapi` (OpenAPI spec) and `GET /swagger` (Swagger UI).

Every signal/scan-match entry that has an asset+timeframe includes a `tradingviewUrl` deep link to the matching TradingView chart; Markdown/watchlist output renders these as real links/importable watchlist tokens instead of plain symbol text.

## Logging

SLF4J, bound to `slf4j-simple` (plain console output, configured via `src/main/resources/simplelogger.properties`) — this also backs Javalin/Jetty's own internal logging. See `developer-notes.md` for conventions if you're adding log statements.

## Testing

```sh
mvn test
```

Uses Testcontainers to spin up a real, throwaway Postgres per integration test class — no manual container setup needed. JUnit Pioneer is also in the mix, narrowly for testing env-var-driven config classes — see `developer-notes.md`'s Testing conventions section for why and how.
