## Context

`CandleIngestionService` already routes each active asset to a `MarketDataProvider` instance per-asset, keyed by `Map<AssetVenue, MarketDataProvider> providersByVenue` (added in `add-asset-venue-routing`). `AssetVenue` today only has `SPOT`/`FUTURES` and its Javadoc explicitly frames it as "which Binance product an asset trades on" — a Binance-specific concept, even though the map it keys is already the general routing mechanism `CandleIngestionService` uses for *any* provider.

`PipelineOrchestrator.runDaily(Provider, Timeframe)` and `DailyScheduler` take a `Provider` argument, but it's used purely as a run-tracking/locking label passed to `IngestionRunDao` (`isRunning`/`start`) — it is **not** a filter. `CandleIngestionService.ingestDaily()` takes no provider argument at all and always processes every active asset via `assetDao.findAllActive()`, dispatching each one to the right client via its own `asset.venue()`. This means a single daily run already covers every provider in one pass; **no new scheduler, orchestrator, or run-tracking changes are needed** to bring a second provider online — only the venue→client routing map needs a new entry, plus a Tiingo client to put in it.

`java25-no-spring` has no `.env`/config-file loader anywhere — `IngestionConfig.fromEnvironment()` and `Main`'s Binance base-URL overrides both read straight from `System.getenv(...)`. `TIINGO_API_KEY` follows the exact same pattern; no new config mechanism is needed.

The `asset.name` column has existed since the very first migration and was wired into `Asset`/`AssetDao` earlier in this same body of work (uncommitted at design time, expected merged before or alongside this change) — this design treats it as already available.

## Goals / Non-Goals

**Goals:**
- Tiingo becomes a second, fully-routed `MarketDataProvider` for the 47 seeded assets, participating in the existing single daily ingestion run with zero changes to scheduling, locking, or orchestration.
- Real company display names are populated for all 47 seeded assets at insert time.
- Tiingo's free-tier rate limits (1000/day, 50/hour) are respected even under retries.

**Non-Goals:**
- No deactivation, dedup, or merging of the existing tokenized Binance STOCK/ETF assets — confirmed additive-only per user decision.
- No general-purpose "any REST market data provider" abstraction — matches the existing `MarketDataProvider` Javadoc's stated philosophy ("a second provider is added here — not speculatively beforehand — if one is ever genuinely needed"); build exactly what Tiingo needs, not a pluggable framework.
- No change to `PipelineOrchestrator`/`DailyScheduler`/`IngestionRunDao`'s provider-as-label semantics — out of scope, pre-existing behavior untouched by prior provider/venue work either.
- No W1 (weekly) fetching from Tiingo — Tiingo is EOD daily-only; `W1` continues to be derived from `D1` via the existing rollup path, same as every other asset.

## Decisions

**1. Add `AssetVenue.EXCHANGE` rather than introducing a separate `Provider`-keyed routing structure.**
`providersByVenue` stays exactly `Map<AssetVenue, MarketDataProvider>` in shape; Tiingo assets simply get `venue = EXCHANGE` and one new map entry. This is the smallest possible diff to `CandleIngestionService` (the routing line `providersByVenue.get(asset.venue())` needs no change at all) and reuses a mechanism that's already proven to generalize past pure Binance-spot-vs-futures, since the map is already the thing that dispatches to *any* client. The `AssetVenue` Javadoc is updated from "which Binance product" to something like "which trading venue's market-data client an asset routes to — Binance's spot/futures are separate products with separate hosts and paths; `EXCHANGE` represents a real securities exchange (e.g. Tiingo) where that spot/futures split doesn't apply."
- *Alternative considered:* re-key the map by `Provider` (with Binance's single map value being an internal venue-routing wrapper). Rejected — it would require either changing `MarketDataProvider.fetchDailyCandles`'s signature to also take venue, or building a wrapper type purely to preserve today's call-site simplicity, for no behavioral gain over just adding one enum value.
- *Alternative considered:* reuse `AssetVenue.SPOT` for Tiingo assets since "spot" loosely reads as "not futures." Rejected — `SPOT` retains Binance-specific meaning elsewhere (default base URL selection, klines path selection inside `BinanceMarketDataProvider`), so overloading it for a real-equity provider risks a future accidental wiring of a Tiingo asset into a Binance client via a refactor that assumes `SPOT` implies Binance.

**2. New `TiingoMarketDataProvider implements MarketDataProvider`, structurally parallel to `BinanceMarketDataProvider`.**
Single class, constructed with an `HttpClient`, API key, and overridable base URL (mirroring the existing `(httpClient, venue)` / `(httpClient, venue, baseUrl)` constructor-overload pattern used for testability). Calls `GET /tiingo/daily/<ticker>/prices?startDate=...&endDate=...&token=...`, maps Tiingo's JSON objects to `Candle`, and uses the **adjusted** OHLC fields (`adjOpen/adjHigh/adjLow/adjClose`) rather than raw, so a split or dividend in an asset's history doesn't produce a discontinuous-looking candle series for SuperTrend — consistent with how a real equity's price history is normally consumed for technical indicators. Volume uses `adjVolume` for the same consistency reason.
- *Alternative considered:* raw (unadjusted) OHLC. Rejected — SuperTrend's ATR-based trend detection would see a false "gap" on every future split, indistinguishable from a real price move.

**3. `TIINGO_API_KEY` via `System.getenv`, no config file.**
Matches `IngestionConfig`/`Main`'s existing pattern exactly. No `.env` loader is introduced.

**4. Rate limiting: a simple minimum-interval throttle inside `TiingoMarketDataProvider`, not a shared scheduler-level limiter.**
50/hour = one request per 72 seconds sustained; a full 47-asset sweep already only needs one request per asset (Tiingo's `/prices` endpoint returns the full requested date range in one call, unlike Binance's paged klines), so 47 requests comfortably fits both the hourly and daily caps even with the existing `POLITE_DELAY_MS = 200` inter-asset delay `CandleIngestionService` already applies uniformly. No new limiter is needed for the steady-state case; the risk is only around **retries** (e.g. a transient failure retried per-asset in a tight loop). Mitigation: don't add automatic retries in `TiingoMarketDataProvider` itself — a failed fetch already surfaces as a per-asset error via `ingestForAssetSafely`'s existing catch-and-continue, which naturally caps worst-case request volume at one attempt per asset per run (47/day-run, nowhere near 1000/day) without needing new limiter code.
- *Alternative considered:* a token-bucket limiter shared across the Tiingo client. Rejected as unnecessary given the request volume math above — would be speculative complexity for a constraint that's already satisfied by existing behavior.

**5. Seed migration sets `name` and `venue = 'EXCHANGE'` explicitly on every inserted row**, rather than relying on column defaults (unlike `V12__seed_stock_assets.sql`, which relied on `venue`'s `DEFAULT 'SPOT'` and was silently wrong until `add-asset-venue-routing` backfilled it). Setting both explicitly at insert time avoids repeating that exact class of bug.

## Risks / Trade-offs

- **[Risk] Three of the 47 assets are US OTC ADRs (`SSNLF`, `TCEHY`, `RHHBY`), not the primary listing — OTC data is typically lower-liquidity/lower-quality than a primary exchange feed, which could mean sparser or staler daily bars than the other 44 names.** → Mitigation: none needed at build time; existing `InvalidSymbolException`-driven auto-deactivation already handles a symbol that stops resolving, and W1 SuperTrend's own multi-week warm-up already tolerates some daily noise. Revisit only if these three specifically show data-quality problems in practice.
- **[Risk] No DB-level constraint ties `provider = 'TIINGO'` to `venue = 'EXCHANGE'` (or `provider = 'BINANCE'` to `venue IN (SPOT, FUTURES)`) — a future manual insert or bug could pair them incorrectly, and `CandleIngestionService` would then either 404 against the wrong client or silently mis-route.** → Mitigation: consistent with existing practice — no such constraint exists for the current Binance spot/futures pairing either (V14 has no CHECK constraint). Accepted as pre-existing pattern, not introduced fresh by this change.
- **[Risk] `DailyScheduler`'s `Provider.BINANCE` argument to `runDaily` becomes even less accurate as a description of what a run actually does**, since one run has ingested both Binance- and now Tiingo-sourced assets in a single pass for a while (true since `add-asset-venue-routing` already collapsed venue routing into one `ingestDaily()` call, not newly introduced here). → Mitigation: none in this change — it's a pre-existing, purely cosmetic label used only for run-tracking/locking, and renaming that concept is unrelated scope creep for this change.
- **[Trade-off] Additive-only means the same company can appear twice** (e.g. tokenized Binance `AAPLUSDT` and real Tiingo `AAPL`) with potentially different, even contradictory, SuperTrend signals. This is an explicit, user-confirmed trade-off for this change, not an oversight — resolving it (deactivating the tokenized duplicates) is deferred to a future change.

## Migration Plan

1. New migration adding `TIINGO` to the `provider` enum (`ALTER TYPE provider ADD VALUE 'TIINGO'` — cannot run inside the same transaction as any statement that *uses* the new value, so it must be its own migration file, before the seed migration that inserts rows with it).
2. New migration adding `EXCHANGE` to the `asset_venue` enum, same transactional constraint as above.
3. New seed migration inserting the 47 assets with `provider = 'TIINGO'`, `venue = 'EXCHANGE'`, `asset_class = 'STOCK'`, and `name` populated per asset.
4. Code deploy: `Provider.TIINGO`, `AssetVenue.EXCHANGE`, `TiingoMarketDataProvider`, `Main` wiring a Tiingo client into `providersByVenue` under `EXCHANGE`, `TIINGO_API_KEY` set in the deployment environment.
5. Order matters: the enum migrations (1–2) must run and be deployed *before* the code that references `Provider.TIINGO`/`AssetVenue.EXCHANGE` goes live, and the seed migration (3) must not run until the code deploy (4) can actually ingest the new rows (or they'll just sit inactive-by-absence-of-provider until the next deploy — not harmful, just delayed).
- **Rollback:** dropping an enum value is not straightforward in Postgres (no `DROP VALUE`); rollback of a bad deploy is via deactivating the 47 seeded rows (`active = false`) rather than reverting the migration, consistent with how this system already self-heals via `active` rather than schema rollback elsewhere (e.g. `add-asset-venue-routing`'s re-activation UPDATE).

## Open Questions

- Should `TiingoMarketDataProvider` re-fetch a wider `startDate` on every incremental run (Tiingo's endpoint is a single ranged call, not paginated cursor-based like Binance) or rely purely on `CandleIngestionService`'s existing `lastClose`-based incremental window? Recommend the latter for consistency with Binance's ingestion model — no change needed to `ingestForAsset`'s date-window logic, just confirm Tiingo's `/prices?startDate=&endDate=` accepts the same millisecond-precision window math already in use (may need day-precision `YYYY-MM-DD` conversion instead, since Tiingo's daily endpoint takes calendar dates, not timestamps — a `TiingoMarketDataProvider`-local formatting detail, not an architectural one).
