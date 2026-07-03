## Why

The summary and trend-alignment reports list many assets, each linking to a TradingView chart. Reviewing them means opening links one at a time. TradingView's native way to review many symbols is a watchlist — one chart you arrow through — and it imports/pastes from a simple text format with section headers. Emitting the reports in that format lets a user pull every flagged asset into TradingView grouped by the same sections the report already uses, instead of opening dozens of tabs.

## What Changes

- Add a `WATCHLIST` value to `SummaryFormat` (alongside `JSON`, `MARKDOWN`).
- `GET /api/v1/summary?format=WATCHLIST` and `GET /api/v1/summary/trend-alignment?format=WATCHLIST` return TradingView watchlist text: `###Section` headers matching the report's existing sections, with `EXCHANGE:SYMBOL` lines (e.g. `BINANCE:BTCUSDT`) under each. Served as `text/plain`.
- Symbols are derived from data already on each result (the `EXCHANGE:SYMBOL` string is already embedded in each DTO's `tradingviewUrl`), so no DTO changes are needed.
- No symbol cap — every flagged asset is emitted. TradingView tier limits (e.g. free-tier watchlist size, import gating) are the consumer's concern, not something the API pre-truncates.
- Per-symbol detail (flip recency, volume, % change) is **not** included — TradingView's watchlist format only supports section headers and symbol lines; that richer context stays in `MARKDOWN`.

## Capabilities

### New Capabilities

- `watchlist-export`: A TradingView-importable watchlist output of the summary and trend-alignment reports, section-labeled, so all flagged symbols can be pulled into one chart.

### Modified Capabilities

_(none — `SummaryFormat` is an internal enum; adding a value doesn't change existing formats' behaviour)_

## Impact

- `SummaryFormat` — new `WATCHLIST` constant
- `SummaryController` / `ConfluenceSummaryController` — handle `format=WATCHLIST`, return `text/plain`
- `SummaryReportFormatter` — new watchlist formatting for both report shapes
- `TradingViewUtil` (or similar) — a small helper to produce/derive the `EXCHANGE:SYMBOL` token
- **Out of scope**: a bare-URL/`LINKS` format (the URLs already exist in `MARKDOWN`; a local one-liner can open them), the `/signals` endpoint (no `format` param today), and any per-symbol annotations in the watchlist
