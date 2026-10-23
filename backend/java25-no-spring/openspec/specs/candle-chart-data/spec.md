# candle-chart-data Specification

## Purpose
TBD - created by archiving change add-candles-chart-api. Update Purpose after archive.
## Requirements
### Requirement: Candle time-series endpoint
The system SHALL expose `GET /api/v1/candles` with required query params `symbol` and `timeframe` (`D1`|`W1`, case-insensitive), returning the asset's stored finalized candles as a `candles` array ordered oldest-to-newest. Each element SHALL be `{time, open, high, low, close, volume}`, where `time` is the candle's open time in UTC epoch seconds (integer) and `open`, `high`, `low`, `close`, `volume` are JSON numbers.

#### Scenario: Default request returns recent candles in ascending order
- **WHEN** a client calls `GET /api/v1/candles?symbol=BTCUSDT&timeframe=D1` for an active asset with stored D1 candles
- **THEN** the response is 200 with a `candles` array sorted by ascending `time`, each element having numeric `time`, `open`, `high`, `low`, `close`, `volume`

#### Scenario: Time is the candle open time in epoch seconds
- **WHEN** a stored D1 candle has open time `2026-10-01T00:00:00Z`
- **THEN** its element's `time` is `1790812800`

#### Scenario: Missing required parameter
- **WHEN** a client omits `symbol` or `timeframe`, or passes an unknown timeframe
- **THEN** the response is 400

#### Scenario: Coverage endpoint unaffected
- **WHEN** a client calls `GET /api/v1/candles/coverage`
- **THEN** it returns the existing coverage response, unchanged

### Requirement: Symbol resolves to an active asset
The `symbol` parameter SHALL be matched exactly (case-sensitive) against active assets only. Inactive assets and their candles SHALL never be returned.

#### Scenario: Unknown or inactive symbol
- **WHEN** no active asset has the given symbol, even if an inactive asset with that symbol has candles
- **THEN** the response is 404

#### Scenario: Ambiguous active symbol
- **WHEN** more than one active asset shares the symbol across providers
- **THEN** the response is 409 and the error details list the matching providers

### Requirement: Windowed and pageable results
The endpoint SHALL accept optional `from` (inclusive) and `to` (exclusive) bounds on candle open time, each as UTC epoch seconds (the same unit as `time`), an ISO-8601 instant, or an ISO-8601 date (meaning UTC midnight), and an optional `limit` (default 500, maximum 5000). It SHALL return the most recent `limit` candles inside the window, and a top-level boolean `hasMore` that is true if and only if at least one older candle exists inside the window before the first returned candle.

#### Scenario: Default limit returns the latest bars
- **WHEN** an asset has 800 stored D1 candles and the client passes no window params
- **THEN** the response contains the latest 500 candles and `hasMore` is true

#### Scenario: Paging older history without overlap
- **WHEN** the client repeats the request with `to` equal to the first returned candle's `time` (epoch seconds, passed as-is)
- **THEN** the response contains the next-older candles, none of which duplicate the previous page, and `hasMore` is false once the oldest candle is included

#### Scenario: Explicit range
- **WHEN** the client passes `from=2026-09-01&to=2026-10-01` for D1
- **THEN** only candles with open time on or after 2026-09-01T00:00Z and before 2026-10-01T00:00Z are returned

#### Scenario: Equivalent bound formats
- **WHEN** the client passes `from=1790812800`, `from=2026-10-01T00:00:00Z` or `from=2026-10-01`
- **THEN** all three select the same candles

#### Scenario: Invalid window parameters
- **WHEN** `limit` is less than 1 or greater than 5000, `from`/`to` cannot be parsed, or `from` is not before `to`
- **THEN** the response is 400

#### Scenario: No candles in window
- **WHEN** the asset is active but no candles fall inside the window
- **THEN** the response is 200 with an empty `candles` array and `hasMore` false

### Requirement: Optional SuperTrend and flip-signal overlay
When `include` contains `supertrend` (comma-separated list, case-insensitive), the response SHALL also contain `supertrend`: `[{time, value, direction}]` and `signals`: `[{time, event}]`, both ordered by ascending `time`, restricted to the close-time range of the returned candles, and using the same open-time epoch-seconds `time` as the candle with the same close time. `direction` SHALL be `UP` or `DOWN`; `signals` SHALL contain only `BULLISH_REVERSAL` and `BEARISH_REVERSAL` events. Without `include=supertrend`, the `supertrend` and `signals` fields SHALL be absent.

#### Scenario: Overlay aligned with candles
- **WHEN** a client calls `GET /api/v1/candles?symbol=BTCUSDT&timeframe=W1&include=supertrend`
- **THEN** every `supertrend[i].time` and `signals[j].time` equals the `time` of a candle in the same response

#### Scenario: Only flips are reported as signals
- **WHEN** the window contains signal-state rows with event `NONE` and one `BULLISH_REVERSAL`
- **THEN** `signals` contains exactly one element with `event` = `BULLISH_REVERSAL`

#### Scenario: Overlay omitted by default
- **WHEN** `include` is not passed
- **THEN** the response has no `supertrend` or `signals` fields

#### Scenario: Unknown include value
- **WHEN** `include` contains a value other than `supertrend`
- **THEN** the response is 400

### Requirement: Asset and freshness metadata
The response SHALL include top-level `symbol`, `name` (nullable), `assetClass`, `timeframe`, `tradingviewUrl` (built by the same logic as other endpoints; nullable) and the same freshness fields as `GET /api/v1/signals` (`lastIngestionAt`, `candlesThrough`, `stale`). Staleness SHALL NOT cause an error response.

#### Scenario: Metadata present
- **WHEN** a client requests candles for an active asset
- **THEN** the response includes the asset's symbol, name, asset class, requested timeframe, its TradingView chart URL for that timeframe, and freshness metadata

#### Scenario: Stale data still served
- **WHEN** the timeframe's ingested data is stale beyond the grace window
- **THEN** the response is still 200 with the stored candles

### Requirement: Agent-ready API documentation
The endpoint SHALL be documented in the OpenAPI spec with descriptions of every parameter and field, including: `time` is open time in epoch seconds, only finalized candles are returned (the forming candle is excluded), how to page with `to` + `hasMore`, and what `include=supertrend` adds.

#### Scenario: Endpoint in OpenAPI document
- **WHEN** a client fetches the OpenAPI document
- **THEN** it contains `GET /api/v1/candles` with the `symbol`, `timeframe`, `from`, `to`, `limit` and `include` parameters described

