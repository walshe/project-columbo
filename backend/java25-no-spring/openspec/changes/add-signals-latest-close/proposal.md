## Why

The Monty chat agent needs to answer price questions like "what's the bitcoin price?" directly from the signal response, but `GET /api/v1/signals` currently doesn't include the latest close price — the agent has to make a separate `/candles` call per symbol to get it. `latestClose` is already fetched inside `SignalQueryService` to compute `pctChangeSinceFlip`; it just isn't exposed on `SignalSummary`.

## What Changes

- Add `latestClose: BigDecimal` (nullable) to `SignalSummary` — the most recent candle close for each asset on the requested timeframe.
- Thread the already-fetched value through `toSummary` and into the JSON response under `latestClose`.
- `null` when no candle exists for that symbol+timeframe (same null condition as `pctChangeSinceFlip`).
- No new DAO query, no DB schema change.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `signals-query`: `SignalSummary` gains a `latestClose` field. No change to filters, sort, or freshness behaviour — purely an additive field on each response object.

## Impact

- `SignalSummary.java` — add `latestClose` parameter to the record; `Objects.requireNonNull` is **not** added (nullable by design).
- `SignalQueryService.toSummary` — pass the already-available `latestClose` local variable into the `SignalSummary` constructor.
- `SignalQueryServiceTest`, `SignalQueryServiceIntegrationTest`, and any other tests that construct `SignalSummary` directly — update constructors to supply the new field.
- `@OpenApi` annotation on `SignalsHandler.getSignals` — document the new response field.
- No change to query parameters, response envelope, or other endpoints.
