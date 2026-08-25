## Context

`TradingViewUrl.generateUrl(provider, symbol, timeframe, venue)` already special-cases `AssetVenue.EXCHANGE` to return `null` (see `add-tiingo-provider`'s design.md, decision area around `TradingViewUrl`), because the Binance-shaped construction it uses for every other venue (`PROVIDER:SYMBOLUSDT[.P]`) is meaningless for a real equity. That `null` is correct as a *safety* default, but it means the 47 Tiingo assets never get a working chart link, even though TradingView does carry every one of them.

Two data sources were checked live before deciding how to fix this:
- Tiingo's own metadata endpoint (`GET /tiingo/daily/<ticker>`) returns an `exchangeCode` field (`NASDAQ`, `NYSE`, `PINK`, `SHG` confirmed for a sample of the 47 assets) — but this doesn't reliably match TradingView's own exchange labels (`PINK` → TradingView's `OTC`, `SHG` → TradingView's `SSE`) or ticker format (Tiingo's `BRK-A` → TradingView's `BRK.A`).
- TradingView's own symbol search (verified via the `mcp__tradingview__symbol_search` tool, ticker-by-ticker for all 47) gives the ground truth directly: exact `exchange:symbol` pairs as TradingView itself resolves them.

## Goals / Non-Goals

**Goals:**
- Every one of the 47 Tiingo assets gets a correct, working TradingView chart link.
- The stored reference is verified ground truth (from TradingView itself), not inferred/transformed from Tiingo's ticker or exchange code at runtime — no mapping-table logic that could silently drift or mis-map a future ticker.
- Assets without a verified ref (any future `EXCHANGE`-venue asset not yet checked) still get `null`, never a guessed link.

**Non-Goals:**
- No general Tiingo-exchangeCode-to-TradingView-exchange mapping table/service — rejected below.
- No change to ingestion, candle fetching, or any pipeline stage — this is a read-path-only enrichment of the existing `tradingviewUrl` field.
- No live/runtime call to TradingView or Tiingo's metadata endpoint at request time — the ref is static, verified once, and stored.

## Decisions

**1. Store a fully-formed, verified `EXCHANGE:SYMBOL` string (`tradingview_ref`) rather than storing Tiingo's raw `exchangeCode` and deriving the TradingView symbol at runtime.**
A derive-at-runtime approach would need a Tiingo-exchangeCode → TradingView-exchange mapping (`PINK`→`OTC`, `SHG`→`SSE`, ...) plus a ticker-format transform (Tiingo's `BRK-A` → `BRK.A`) — two more places to get wrong, for a value that's static per asset anyway. Storing the already-correct, individually-verified string is simpler and can't drift.
- *Alternative considered:* store Tiingo's `exchangeCode` and a mapping table. Rejected — more moving parts for no benefit when the final value never changes for a given asset, and the two sources don't even agree, so a table would itself need per-entry verification anyway.

**2. Verify every one of the 47 tickers individually against TradingView's own symbol search, not inferred from general knowledge of "well-known" exchange listings.**
This caught a real, easy-to-miss error before it shipped: Berkshire Hathaway's Tiingo ticker `BRK-A` is `BRK.A` on TradingView (dot, not dash) — a plausible-looking but wrong guess would have produced a dead link. All 47 resolved to a single unambiguous match; none were low-confidence.

**3. `tradingview_ref` is a plain nullable `VARCHAR`, not a foreign key or enum.**
It's a leaf, display-only value with no referential meaning elsewhere in the schema — matches the same pattern already used for `asset.name`.

**4. `TradingViewUrl.generateUrl` takes the ref as an explicit parameter rather than looking it up itself.**
Keeps `TradingViewUrl` a pure, DB-free utility (its existing shape) — `SignalQueryService` already has the `Asset` in hand and passes `asset.tradingviewRef()` through, same pattern as every other `Asset` field it already reads.

## Risks / Trade-offs

- **[Risk] A future Tiingo asset onboarded without also verifying/seeding its `tradingview_ref` silently gets `null` instead of a broken link.** → Accepted trade-off, not a bug: `null` was already the deliberate `EXCHANGE`-venue default from `add-tiingo-provider`; this change only improves coverage for the 47 assets actually verified, it doesn't weaken the safety default for anything unverified.
- **[Risk] TradingView could rename/re-list a ticker's exchange in the future (e.g. a listing migrates OTC→NASDAQ), making a stored ref stale.** → No automated re-verification exists; accepted as a low-frequency, low-impact staleness risk (a stale chart link, not a data-correctness issue for signals/candles) — revisit only if it's observed to actually happen.

## Migration Plan

1. `V18__add_tradingview_ref.sql` — `ALTER TABLE asset ADD COLUMN tradingview_ref VARCHAR` (nullable, no backfill needed at this step).
2. `V19__seed_tiingo_tradingview_refs.sql` — `UPDATE` the 47 `V17`-seeded rows with their verified refs, keyed by `(symbol, provider = 'TIINGO')`.
3. Code deploy: `Asset.tradingviewRef()`, `AssetDao` SELECT/mapper, `TradingViewUrl.generateUrl`'s new parameter, `SignalQueryService`'s call site.
4. No ordering constraint between the two migrations and the code deploy beyond the usual "migrations run before the code that reads the new column" — unlike `add-tiingo-provider`'s enum additions, a plain nullable column addition has no same-transaction restriction.
- **Rollback:** the column can simply be ignored (old code never reads it) or dropped; no rollback complexity like the enum-value case in `add-tiingo-provider`.
