## 1. Format enum

- [x] 1.1 Add `WATCHLIST` to `SummaryFormat` with a doc comment describing the TradingView watchlist text output

## 2. Symbol helper

- [x] 2.1 Add a helper (e.g. `TradingViewUtil.watchlistSymbol(String tradingviewUrl)`) that decodes the `symbol=` param back to `EXCHANGE:SYMBOL` (e.g. `BINANCE:BTCUSDT`); returns null/empty-safe for a null URL

## 3. Formatter

- [x] 3.1 Add `formatWatchlist(SummaryReport)` — `###` section headers for the report's asset groups (bullish/bearish flips, RSI scan sections) with `EXCHANGE:SYMBOL` lines beneath; omit empty sections
- [x] 3.2 Add `formatConfluenceWatchlist(ConfluenceSummaryReport)` — `###` headers for bullish/bearish confluence and retest sections with `EXCHANGE:SYMBOL` lines; omit empty sections

## 4. Controllers

- [x] 4.1 In `SummaryController`, handle `format == WATCHLIST` → return `formatWatchlist(...)` as `text/plain`
- [x] 4.2 In `ConfluenceSummaryController`, handle `format == WATCHLIST` → return `formatConfluenceWatchlist(...)` as `text/plain`

## 5. Tests

- [x] 5.1 Unit test the `watchlistSymbol` helper (URL → `EXCHANGE:SYMBOL`, null-safe)
- [x] 5.2 Formatter tests: sections rendered as `###` headers with `EXCHANGE:SYMBOL` lines, empty sections omitted, no per-asset detail present
- [x] 5.3 Controller tests: `format=WATCHLIST` returns `text/plain` with `###` headers for both `/summary` and `/summary/trend-alignment`
- [x] 5.4 Run the affected test suite and confirm green
