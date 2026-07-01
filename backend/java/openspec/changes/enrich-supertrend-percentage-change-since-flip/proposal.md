## Why

Signal lists currently show *when* an asset flipped and how long ago, but not *how much it has moved* since then. Percentage change since the flip is a quick-read measure of whether the trend has momentum behind it or has stalled — a bullish asset up 20% since flipping is a very different setup from one flat at +0.3%.

## What Changes

- Add `pctChangeSinceFlip` (`BigDecimal`, nullable, rounded to 2 d.p.) to `SignalStateDto`
- Populate it in `SignalQueryService` by looking up the candle close at the flip time and the most recent finalized candle, then computing `((current − flip) / flip) × 100`
- `null` when no flip is recorded or no candle is found at the flip time

## Capabilities

### New Capabilities

- `signal-pct-change`: Percentage price change from the flip candle close to the most recent candle close, surfaced on every `SignalStateDto`

### Modified Capabilities

_(none — `SignalStateDto` is an internal DTO, not a versioned public spec)_

## Impact

- `SignalStateDto` — new `pctChangeSinceFlip` field
- `SignalStateMapper` — receives candle close prices and computes the percentage
- `SignalQueryService` — bulk-fetches flip-time and latest candle closes per asset, passes to mapper
- `CandleRepository` — may need a new bulk query to fetch latest close prices for a set of assets efficiently
- **Both `/api/v1/summary` and `/api/v1/summary/trend-alignment` are enriched automatically** — they both return `SignalStateDto` and no controller changes are needed
- Markdown formatters (`SummaryReportFormatter`) — optionally surface `pctChangeSinceFlip` alongside existing flip recency and volume
- All tests constructing `SignalStateDto` will need the new field added (can be `null`)
