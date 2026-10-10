## Context

Candles live in `candle` (keyed `(asset_id, timeframe, close_time)`, with an `open_time` column). The SuperTrend series lives in `indicator_supertrend` and per-candle trend state/events in `signal_state`, both keyed by the same `(asset_id, timeframe, close_time)`. Only finalized candles are stored: ingestion and rollup stop at `FinalizedBoundary.utcMidnightToday`, so today's forming D1 candle and the current forming W1 candle are never in the table.

Assets are unique on `(symbol, provider)`. Since the MEXC migration (V23-V26) MEXC is the only active provider, but deactivated Binance/Tiingo rows (and their candles) still exist, sometimes with the same symbol.

Consumers:
- **Monty** (Next.js web app) reaches Columbo server-side through Supabase functions. It will render charts with **TradingView Lightweight Charts** (the primary target, with the community line-tools plugins), plotting the SuperTrend line from this endpoint's `include=supertrend` data instead of recomputing it client-side, so the chart always matches Columbo's signals. KLineChart is a fallback; the response stays library-agnostic so either works.
- **AI agent** calls Columbo's API as a tool, guided by the `@OpenApi` descriptions.

## Goals / Non-Goals

**Goals:**
- One library-agnostic endpoint that any OHLC charting library can consume with a trivial adapter.
- Optionally return Columbo's own SuperTrend line and flip markers, aligned to the same candles.
- Bounded responses with a simple way to lazily load older history.

**Non-Goals:**
- Live/streaming updates (websocket/SSE). Data changes once per ingestion run; clients poll if they want updates.
- The forming (unfinalized) candle, including the provisional W1 bar. `ProvisionalTrendService` stays the source for provisional state.
- Persisting user drawings (lines, fibs, positions). Monty handles that (e.g. line-tools `exportLineTools()` JSON) - a possible later change.
- Other indicators (ATR bands, EMAs...). The client library computes those if needed.
- CORS. Monty calls Columbo server-side.

## Decisions

### D1. Path `GET /api/v1/candles`, not library-named
Sits next to the existing `/api/v1/candles/coverage` and says what it returns, not who renders it. Javalin resolves the literal `/coverage` segment independently, so the two never collide. *Alternative:* `/tradingview-light-charts/...` was rejected because it couples the API to one frontend library while two are being trialled.

### D2. Single asset, required `symbol` + `timeframe`
`symbol` is exact and case-sensitive (same as `/signals?symbols=`) and resolves against **active** assets only, so deactivated same-symbol rows from old providers are never picked. No active match → 404. More than one active match → 409 naming the providers (not reachable today, but defined rather than arbitrary). `timeframe` is required, matching `/signals`.

### D3. `time` = candle open time, UTC epoch seconds
Charting libraries position a bar by its open time. Lightweight Charts takes epoch seconds directly as `UTCTimestamp`; KLineChart needs `time * 1000`. `Timeframe.openTimeFor` already documents this convention. SuperTrend points and flip markers are keyed by `close_time` in the DB and are converted with `timeframe.openTimeFor(closeTime)`, so every series in the response shares one time axis. *Alternative:* ISO-8601 strings were rejected: every library would have to parse them.

### D4. Prices as JSON numbers
`BigDecimal` already serializes as a JSON number through the shared `JsonSupport.OBJECT_MAPPER`. Sub-cent MEXC prices (e.g. `0.14678772`) fit easily within double precision for display. *Alternative:* strings would be lossless but every consumer would have to convert them.

### D5. Windowing: `from` (inclusive), `to` (exclusive), `limit` (latest N), `hasMore`
- `from`/`to` are compared against candle **open time** and accept epoch seconds (the same unit as `time`), an ISO-8601 instant, or a bare date (UTC midnight). Accepting epoch seconds means a chart can page with `to=<candles[0].time>` verbatim.
- `limit` defaults to 500 and is capped at 5000 (400 above the cap). The query takes the **most recent** `limit` candles inside the window (`ORDER BY close_time DESC LIMIT limit+1`, then reversed), so the default call returns "the last 500 bars".
- `hasMore` is true when older candles exist before the first one returned, detected via the `+1` row.
- Lazy loading of older history: call again with `to=<candles[0].time>`. The exclusive `to` means no overlap or de-duplication.
- Filtering on `open_time` uses the `(asset_id, timeframe, …)` index prefix. One asset/timeframe is at most a few thousand rows, so no new index is needed.

### D6. Overlay via `include=supertrend` on the same endpoint
One request returns candles + `supertrend` + `signals`, already aligned to the same window: the overlay queries are bounded by the close times of the first and last candle returned. Without `include` those fields are omitted, so the default response stays small. *Alternative:* a separate `/api/v1/supertrend` endpoint was rejected: two round trips plus client-side window alignment, and it's clumsier for an agent. `include` is a comma-separated list, so more overlays can be added later without new routes.
- `supertrend`: `[{time, value, direction}]`, `direction` ∈ `UP`/`DOWN` (the stored `SuperTrendDirection`). The client colours segments by direction.
- `signals`: `[{time, event}]` for `BULLISH_REVERSAL`/`BEARISH_REVERSAL` rows only (`NONE` rows are filtered in SQL). The client places the marker on the candle with that `time`.

### D7. Response metadata and freshness
Top-level: `symbol`, `name`, `assetClass`, `timeframe`, `tradingviewUrl` (via `TradingViewUrl.generateUrl`, unchanged behaviour), `hasMore`, plus the same freshness fields as `/api/v1/signals` (`lastIngestionAt`, `candlesThrough`, `stale`). No `requireFresh`/503 path: a chart of slightly stale data is still useful, and the metadata lets the client show a hint.

### D8. Layering
`CandlesHandler` (api: parameter parsing, validation, symbol resolution, epoch-second mapping) → `CandleChartService` (new `chart` package: owns the one pooled connection per request and runs the windowed reads on it) → new DAO reads:
- `AssetDao.findActiveBySymbol(String)` → `List<Asset>`
- `CandleDao.findLatestInOpenTimeWindow(connection, assetId, timeframe, from, to, limit)` returning up to `limit+1` rows
- `SuperTrendIndicatorDao.findBetween(connection, assetId, timeframe, closeFrom, closeTo)`
- `SignalStateDao.findFlipsBetween(connection, assetId, timeframe, closeFrom, closeTo)`

The service returns a domain `CandleChart` (domain `Candle`/`SuperTrendResult`/`SignalState` lists + `hasMore`). The handler maps it to `CandlesResponse`. Each overlay row takes the `time` of the returned candle with the same close time, rather than recomputing it with `Timeframe.openTimeFor`, so alignment holds by construction even if a provider's stored open time doesn't follow the `close - span + 1ms` rule. A service rather than a `DataSource` in the handler keeps handlers free of JDBC, matching the existing handlers.

## Risks / Trade-offs

- [The last bar lags "now" by up to a day, because the forming candle is excluded] → Documented in the OpenAPI description; `candlesThrough` exposes it. A provisional-bar option can be added later via `include`.
- [JSON number precision for extreme prices] → MEXC prices have ≤ ~10 significant digits, well within double precision. Revisit only if a consumer needs exact decimal arithmetic.
- [Large requests (limit=5000, include=supertrend) for W1/D1 history] → Hard cap of 5000; roughly 5000×3 small objects is a few hundred KB. Acceptable for a single-user tool.
- [Symbol ambiguity if a second provider is reactivated] → 409 with the providers listed. A `provider` query param can be added then.

## Migration Plan

Additive endpoint, no schema change. Deploy as usual; rollback = revert the commit. Monty adopts it on its own chart branches.

## Open Questions

- Should W1 eventually include the provisional forming week bar (via `include=provisional`)? Deferred until Monty's chart is in use.
