## Why

`POST /api/v1/weekly-trend-briefing` and `POST /api/v1/weekly-pullback-briefing` both throw a `NullPointerException` (500) in production. Both handlers build their `regimePulses` map with `Collectors.toMap`, which is merge()-based internally and rejects any null value regardless of overload used. `marketBreadthSnapshotDao.findLatest(Timeframe.W1, assetClass).orElse(null)` legitimately produces `null` for any `AssetClass` with zero active assets - a case that was only theoretical until `fix-pipeline-connection-pool-exhaustion` retired Binance's tokenized ETF assets entirely, leaving zero active ETF-class assets in production. The bug was latent before that change and is now live on every call to either endpoint.

## What Changes

- `WeeklyTrendBriefingHandler.buildReport` and `WeeklyPullbackBriefingHandler.buildReport` build `regimePulses` with a plain loop into a `LinkedHashMap` instead of `Collectors.toMap`, so a null snapshot (no active assets, or none with a signal state yet, for that asset class) is stored rather than throwing.
- No change to `WeeklyBriefingFormatting.appendRegimeSection`, which already null-checks `pulse` and renders "no snapshot yet" - this bug was purely in map construction, never reachable render-time.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
(none tracked as formal specs yet for the weekly briefing endpoints - captured here as a delta requirement instead, see specs/)

## Impact

- `WeeklyTrendBriefingHandler.java`, `WeeklyPullbackBriefingHandler.java`: `regimePulses` construction only; no behavior change for asset classes that do have active assets.
- New regression test `WeeklyBriefingHandlerIntegrationTest` seeding only a CRYPTO asset (no STOCK/ETF at all) to reproduce the exact production shape.
