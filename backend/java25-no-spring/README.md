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
| `SUPERTREND_BINANCE_SPOT_BASE_URL` | no | `https://api.binance.com` | Base URL for SPOT-venue assets. Overridable so tests can point the app at a stub server instead of the real Binance API - see the end-to-end test below |
| `SUPERTREND_BINANCE_FUTURES_BASE_URL` | no | `https://fapi.binance.com` | Base URL for FUTURES-venue assets (e.g. stocks/ETFs traded as Binance perpetual futures). Same override use case as the spot variable above |
| `TIINGO_API_KEY` | **yes** | — | API key for Tiingo's daily prices API, used for EXCHANGE-venue (real equity) assets. Startup fails fast if unset |
| `SUPERTREND_TIINGO_BASE_URL` | no | `https://api.tiingo.com` | Base URL for EXCHANGE-venue assets. Overridable so tests can point the app at a stub server instead of the real Tiingo API - see the end-to-end test below |

## HTTP endpoints

All under `/api/v1`, JSON by default unless noted.

| Method | Path | Notes |
|---|---|---|
| `GET` | `/signals` | `timeframe` required; `state`, `sort`, `assetClass`, `symbols` (comma-separated, exact-case symbol list, e.g. `symbols=BTCUSDT,ETHUSDT` - unmatched symbols silently omitted), `requireFresh` optional |
| `GET` | `/assets/by-state` | `timeframe`, `state` required; `assetClass` optional; no freshness gating |
| `GET` | `/summary` | `timeframe` required; `format` (`JSON`/`MARKDOWN`/`WATCHLIST`), `assetClass`, `requireFresh` optional; response echoes back `timeframe`/`assetClass` in every format; `pulse` (market-breadth) is scoped to `assetClass` too, combined across every class when omitted |
| `GET` | `/summary/trend-alignment` | `format`, `maxRetestAgeDays` (default 7), `assetClass`, `requireFresh` optional; freshness always checked against D1; response echoes back `maxRetestAgeDays`/`assetClass` in every format |
| `POST` | `/scan` | JSON body: `operator` (`AND`/`OR`), `conditions[]` (`timeframe`, `state`, optional `maxDaysSinceFlip`), optional `limit`, optional `assetClass`, optional `sort` (`SYMBOL_ASC` default, or `LIQUIDITY_DESC`); each result includes `avgVolume7d` |
| `GET` | `/candles/coverage` | per-timeframe earliest/latest/expected-latest/up-to-date/asset-count; optional `assetClass` restricts `earliest`/`assetCount` only - freshness fields stay global |
| `POST` | `/internal/ingestion/run` | optional JSON body: `provider`/`timeframe` (default `BINANCE`/`D1`); 202 + run id, 409 if already running for that provider+timeframe |
| `POST` | `/weekly-trend-briefing` | No params; runs D1 ingestion to completion, then returns a `text/markdown` report - see "Weekly briefings" below |
| `POST` | `/weekly-pullback-briefing` | No params; runs D1 ingestion to completion, then returns a `text/markdown` report - see "Weekly briefings" below |

Plus `GET /openapi` (OpenAPI spec) and `GET /swagger` (Swagger UI).

Every signal/scan-match entry that has an asset+timeframe includes a `tradingviewUrl` deep link to the matching TradingView chart; Markdown/watchlist output renders these as real links/importable watchlist tokens instead of plain symbol text. A FUTURES-venue asset's URL carries TradingView's `.P` perpetual-contract suffix (e.g. `BINANCE:BITOUSDT.P`) — without it TradingView resolves the symbol against the spot market instead. An EXCHANGE-venue (Tiingo) asset's `tradingviewUrl` is `null` — this system doesn't store the real per-asset TradingView exchange (NASDAQ/NYSE/OTC/SHG), so no link is generated rather than a fabricated one.

Every asset has an `assetClass` (`CRYPTO`/`STOCK`/`ETF`/`COMMODITY`) - crypto, a large batch of tokenized Binance stocks/ETFs, and a smaller set of real equities sourced from Tiingo (`provider = TIINGO`) are all onboarded as `STOCK`. Both a company's tokenized Binance asset and its real Tiingo asset can be active at once — these are additive, independent rows, not deduplicated. The `assetClass` query param above filters results to one class; each signal/scan-match entry also includes its own `assetClass` in the response.

Every asset also has a `venue` (`SPOT`, `FUTURES`, or `EXCHANGE`) that isn't exposed via the API but determines which provider/host `CandleIngestionService` fetches its candles from: `SPOT`/`FUTURES` route to Binance (`SUPERTREND_BINANCE_SPOT_BASE_URL`/`SUPERTREND_BINANCE_FUTURES_BASE_URL`) — separate Binance products with entirely separate symbol universes — while `EXCHANGE` routes to Tiingo (`SUPERTREND_TIINGO_BASE_URL`) for real equities. Stocks/ETFs tokenized on Binance and a handful of crypto symbols (e.g. `HYPEUSDT`) are `FUTURES`; ordinary crypto defaults to `SPOT`; Tiingo-sourced real equities are `EXCHANGE`.

## Weekly briefings

Two composite, trader-facing endpoints that each script a full weekly market read into a single Markdown report - they run ingestion, then compose several of the read endpoints above into one opinionated take on what's worth looking at. Both are `POST` (they trigger ingestion as a side effect), take no request body/query params, and return `text/markdown` only - point a browser, curl, or any HTTP client at them and read the response directly.

Every report opens with the same objective context, regardless of which briefing:

- **Regime Read** - W1 bullish/bearish market breadth for CRYPTO, ETF, and STOCK, side by side. No inferred rotation between asset classes (e.g. "ETF strength implies crypto weakness") - each class's breadth is measured directly, not guessed from another class.
- **BTC Alignment** - BTCUSDT's W1 vs D1 trend state, plus BTC's *provisional* W1 read (see below) when there's enough data to compute one. When committed W1/D1 agree, that's crypto's prevailing direction for the week; when they conflict, every crypto candidate in the report should be treated with extra caution regardless of anything else it shows.

Each briefing then takes its own single, opinionated stance on what to do with that context:

### `POST /weekly-trend-briefing` - follow the confirmed move

Headlines **confluence**: assets where W1 and D1 agree (both bullish, or both bearish). Scan candidates are liquidity-gated (top 15 by 7-day average volume) and then ranked by momentum - the size of the move since the D1 flip, biggest confirming move first. The premise: back the trend that's already proven itself on both timeframes, not one still working itself out. A separate **Flips Forming** section previews names not yet confluence-eligible by committed data, but whose *provisional* W1 read (see below) now agrees with their committed D1 - never blended into the confirmed lists above it.

### `POST /weekly-pullback-briefing` - buy the dip in an established trend

Headlines **retest**: W1 trend intact, but D1 has recently flipped counter to it - a pullback (or bounce, on the bearish/crypto side) within an otherwise-established trend. Confluence isn't shown here at all. Scan candidates are liquidity-gated the same way, then ranked by depth of the counter-move - deepest dip first for a bullish pullback, biggest bounce first for a bearish one. The premise: a better entry often comes from a temporary disagreement within a trend, not from chasing a move that's already run. Any candidate whose *provisional* W1 read (see below) has drifted away from the committed W1 state it was selected on gets an inline `watch:` annotation - a sign this "pullback" might be turning into an actual reversal, not a temporary dip.

### Shared conventions across both

- **Stocks and ETFs are longs-only** everywhere - only the bullish side is scanned or reported for those classes. Crypto is reported both directions, since BTC Alignment (not the briefing itself) is the intended signal for caution on the short side.
- **Liquidity gates; the briefing's opinion ranks.** Every scan-candidate list is cut down to the 15 most liquid matches first, then re-ordered by whichever signal that briefing is built around. A thin, illiquid mover never outranks a liquid one just because it moved more.
- Neither endpoint takes query parameters - asset classes, retest window, and candidate limit are fixed constants tuned for this specific weekly routine, not a general-purpose reporting API.
- Future `weekly-<something>-briefing` variants are expected to follow the same shape: share the Regime Read/BTC Alignment header, then express one clear opinion about what to do with a confirmed-vs-diverging W1/D1 signal, rather than trying to cover every angle in one report.

### Provisional W1 flips

Both briefings only ever headline *committed* trend state - the state as of last Friday's actual weekly close. A **provisional read** answers a different question: "if the current week closed right now, based on this week's daily closes so far, would this asset's weekly trend flip, and at what price?" It's synthesized from this week's already-ingested D1 candles (no new data source, no extra ingestion), run through the same SuperTrend calculation used everywhere else in this system, and is never persisted - recomputed fresh on every request, with no effect on `signal_state`, `signal_event`, or `candles`. It's also W1-only: a provisional *daily* flip would need genuinely new intraday ingestion (multiple polls a day, or lower-timeframe candles), which this system doesn't do.

A provisional read only ever surfaces when it *disagrees* with the committed state for that asset/timeframe - an asset whose provisional read still agrees with its committed state has nothing new to report, so it's shown as-is with no extra annotation. Early in the week a provisional read can be noisy (it's built from only a few days of data, and the underlying ATR bands are still settling) - treat it as "worth watching," not as a confirmed signal in its own right. See `openspec/changes/provisional-w1-flip-detection/` for the full design rationale.

## Logging

SLF4J, bound to `slf4j-simple` (plain console output, configured via `src/main/resources/simplelogger.properties`) — this also backs Javalin/Jetty's own internal logging. See `developer-notes.md` for conventions if you're adding log statements.

## Testing

```sh
mvn test
```

Uses Testcontainers to spin up a real, throwaway Postgres per integration test class — no manual container setup needed. JUnit Pioneer is also in the mix, narrowly for testing env-var-driven config classes — see `developer-notes.md`'s Testing conventions section for why and how.

### End-to-end test

```sh
mvn verify -Pe2e
```

Opt-in only (not part of `mvn test`/plain `mvn verify`) - builds this module's actual Docker image and runs it against a real Postgres container plus a WireMock container stubbing Binance, driving the full pipeline through real HTTP. Takes a few minutes (Docker image build + three containers), which is why it's separate from the fast default suite. See `developer-notes.md` for how it works.
