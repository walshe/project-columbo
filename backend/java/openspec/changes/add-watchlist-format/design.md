## Context

`SummaryFormat` (`JSON`, `MARKDOWN`) is a query param on `GET /api/v1/summary` and `GET /api/v1/summary/trend-alignment`; both controllers branch on `format == MARKDOWN` and otherwise return the JSON report. `SummaryReportFormatter` renders the Markdown for both report shapes (`formatMarkdown`, `formatConfluenceMarkdown`). Each asset in a report is a `SignalStateDto` (summary flip lists, trend-alignment) or `ScanResult` (RSI scan sections); both already expose a `tradingviewUrl` whose `symbol=` query param is the URL-encoded `EXCHANGE:SYMBOL` (e.g. `BINANCE%3ABTCUSDT`) produced by `TradingViewUtil.generateUrl(provider, symbol, timeframe)`. TradingView's watchlist import/paste format is plain text: `###Section` header lines and `EXCHANGE:SYMBOL` lines; anything else is ignored on import.

## Goals / Non-Goals

**Goals:**
- One `WATCHLIST` output that maps the report's existing sections to `###` groups and lists each asset as `EXCHANGE:SYMBOL`
- Reuse the exchange/symbol the app already computes for chart links — no divergent mapping
- Emit everything; never truncate for tier limits

**Non-Goals:**
- A separate bare-URL/`LINKS` format (URLs already live in `MARKDOWN`; a local `grep … | xargs open` covers that need)
- `/signals` support (it has no `format` param today)
- Per-symbol annotations (unsupported by the watchlist format)
- Designing around free-tier caps (import gating / 30-symbol limit are the consumer's concern)

## Decisions

**Derive `EXCHANGE:SYMBOL` from the existing `tradingviewUrl`, not new DTO fields**
Each result already carries a `tradingviewUrl` containing `symbol=<encoded EXCHANGE:SYMBOL>`. A small helper (e.g. `TradingViewUtil.watchlistSymbol(url)`) decodes that back to `BINANCE:BTCUSDT`. This avoids adding an `exchangeSymbol` field to `SignalStateDto`/`ScanResult` and threading it through every construction site (which the recent `stale`/`pctChangeSinceFlip` changes showed is churny). *Alternative*: add a dedicated field populated by the mapper (which has the `provider`); rejected for now to keep the change small, but it is the cleaner long-term home if more consumers need the token. A guard returns nothing for a null URL.

**New value on `SummaryFormat`, handled in the controllers like `MARKDOWN`**
Add `WATCHLIST`; each controller adds a branch returning `formatter.format…Watchlist(report)` with `Content-Type: text/plain`. Keeps the format switch in one obvious place per endpoint and mirrors the existing `MARKDOWN` handling.

**Formatter owns section→header mapping; empty sections omitted**
`SummaryReportFormatter` gains `formatWatchlist(SummaryReport)` and `formatConfluenceWatchlist(ConfluenceSummaryReport)`. Section headers reuse the same labels as the Markdown sections. A section with no assets emits no header (avoids stray empty groups in TradingView).

**`text/plain`, no cap**
TradingView import expects `.txt`; `text/plain` is the honest content type. The API emits all symbols; tier limits are enforced by TradingView, not pre-empted here.

## Risks / Trade-offs

[Deriving the token by parsing `tradingviewUrl`] If the URL scheme changes, the watchlist token breaks. → Centralise the parse in one helper next to `generateUrl` so the encode/decode pair lives together; cover with a unit test. If it proves fragile, promote to a first-class DTO field.

[Null/absent `tradingviewUrl`] A result without a URL yields no token. → Skip that asset's line (it also has no working chart link in Markdown), rather than emit a malformed entry.

[Free-tier users can't one-click import] Import is likely Pro-gated and free caps watchlist size. → Out of scope by decision; the text is still paste-able, and non-goals note the local-`open` alternative for the URL use case.
