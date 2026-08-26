## Context

`v_asset_liquidity` (migration `V7`) computes `avg_volume_7d` as a plain `AVG(candle.volume)` over the last 7 days, with no per-provider normalization — it was written when Binance was the only provider, so "volume" had one unambiguous meaning (quote-asset dollar volume, from kline index 7 — see `BinanceMarketDataProvider.toCandle`'s comment on why index 7, not 5, is used). `TiingoMarketDataProvider` (from `add-tiingo-provider`) stored Tiingo's `adjVolume` field directly — a share count, not a dollar figure — without noticing the mismatch, since nothing in that change's testing compared liquidity rankings across providers.

This surfaced only once real usage combined both providers in one ranked list: `ScanSort.LIQUIDITY_DESC` (`SignalQueryService`/`ScanService`) sorts by this same raw `avgVolume7d`, and both weekly briefing endpoints use it to cap their scan-based sections to the top 15 candidates. A user reported every TradingView link in weekly briefings appearing to be Binance-sourced, which traced back to this — not a defect in link generation (already fixed and tested in `add-tradingview-exchange-ref`), but Tiingo assets never surviving the liquidity cut in the first place.

## Goals / Non-Goals

**Goals:**
- `candle.volume` has one consistent meaning ("dollar-notional value traded") regardless of provider, so `AVG(candle.volume)`-based ranking is meaningful when it mixes assets from different providers.
- Already-ingested Tiingo candle history is corrected, not just new data going forward — a user re-running a scan today shouldn't see the bug persist for months until old rows roll out of the 7-day window (they wouldn't roll out on their own anyway, since nothing re-fetches already-stored historical days).

**Non-Goals:**
- No change to `v_asset_liquidity`'s SQL, `ScanSort.LIQUIDITY_DESC`'s comparator, or any downstream consumer — they're already correct once the underlying stored value is; fixing this at the source (ingestion) is a smaller, more targeted change than adding per-provider normalization at every read site.
- No change to OHLC price fields, SuperTrend computation, or W1 rollup — volume isn't used by any of those (confirmed: `SuperTrendCalculator`/`CandleRollupService` don't reference `Candle.volume` at all).
- No general "declare each provider's volume unit" abstraction — two providers, one obvious shared target unit (dollars); building a registry/config for this would be speculative complexity for a problem that has one concrete right answer today.

## Decisions

**1. Fix at the ingestion source (`TiingoMarketDataProvider.toCandle`), not at a read site.**
`candle.volume` is meant to be a provider-agnostic column (same table, same column, used identically for every asset elsewhere in the system) — storing it in inconsistent units was the actual bug, and fixing storage means every current and future consumer (the liquidity view, any future feature that reads volume) is correct automatically, with no per-call-site normalization to remember.
- *Alternative considered:* normalize in `v_asset_liquidity` or in `AssetLiquidityDao` (e.g. a `CASE` on `asset.provider`). Rejected — pushes provider-specific unit knowledge into a general-purpose liquidity query, and still leaves the stored `candle.volume` value itself wrong/misleading for any other purpose (e.g. a future API field exposing raw volume).

**2. Dollar-notional = `adjVolume * adjClose`, using Tiingo's split/dividend-adjusted close.**
Matches `add-tiingo-provider`'s existing rationale for using adjusted fields throughout (consistency across split/dividend events) — multiplying by the *adjusted* close keeps that consistency; multiplying by the raw unadjusted close would reintroduce exactly the kind of discontinuity the adjusted-fields decision was meant to avoid.

**3. Backfill via a migration that multiplies each stored row's own `close`, not a fresh Tiingo API re-fetch.**
The already-stored `close` on each row *is* the adjusted close it was computed from (see Decision 2 in `add-tiingo-provider`'s design.md — `Candle.close` is always `adjClose`), so `volume * close` reproduces exactly what the corrected ingestion code would have stored, with no network calls, no rate-limit exposure, and no risk of drift if a symbol's history has since changed on Tiingo's side.
- *Alternative considered:* re-run ingestion for all 47 Tiingo assets from their backfill-start date. Rejected — unnecessary API load and slower for a value that's directly derivable from data already in the database.

## Risks / Trade-offs

- **[Risk] If this backfill migration ever ran twice against the same database (e.g. a manual re-run outside Flyway's normal tracking), it would double-multiply and corrupt the data.** → Accepted: standard Flyway versioned-migration guarantee (runs exactly once, tracked in `flyway_schema_history`) already protects against this the same way every other migration in this project is protected; not a new risk class introduced here.
- **[Trade-off] `candle.volume`'s meaning ("dollar-notional value traded") is now an implicit cross-provider contract enforced only by convention/comments, not a schema constraint.** → Accepted, consistent with how this codebase already handles similar cross-cutting invariants (e.g. `Candle`'s adjusted-vs-raw field choice) via documentation and code comments rather than schema-level enforcement.

## Migration Plan

1. Both the `TiingoMarketDataProvider.toCandle` code change and `V21__backfill_tiingo_dollar_volume.sql` ship in the same deploy. `SchemaMigrator.migrate()` runs synchronously in `Main.main()` before the HTTP server or `DailyScheduler` start, so the backfill always completes before the new code could possibly ingest a single new (already-correct) candle — there is no window where the migration's blanket `UPDATE` could double-multiply an already-corrected row. This ordering guarantee is why the migration doesn't need to distinguish old rows from new ones.
- **Rollback:** this migration is **not idempotent** — it must never be re-run manually outside Flyway's own tracking, since a second application would double-multiply every row. If a rollback is ever needed, restore from a pre-migration backup rather than attempting to divide back out (there's no way to distinguish an already-corrected row from one that coincidentally has `close = 1` after the fact).
