## Why

After each ingestion, `IndicatorComputationService` and `SignalStateDetectionService` each load an asset's **entire** candle history from the database and re-run the SuperTrend calculator over all of it, on every run, for every active asset — even when a run added a single new daily candle, and even for assets that got no new candle at all. Only the DB *writes* are incremental; the reads and the recompute are O(total history). At 118 active assets (post `migrate-to-mexc-provider`) with multi-year daily series plus their weekly rollups, that is hundreds of full-history table scans and full-series recomputes per run to produce a handful of new rows. `SuperTrendCalculator.calculateIncremental` already proves a bounded warm-up window is sufficient for a correct result — the incremental read path just never got built to match the incremental write path.

## What Changes

- `IndicatorComputationService` bounds its candle fetch to a warm-up window ending just before the asset's last-stored indicator close time, instead of `candleDao.findByAssetAndTimeframe` (unbounded). It already calls `calculateIncremental` with that same anchor — this aligns the input load with what that method actually needs.
- `SignalStateDetectionService` switches from "recompute the full series with `calculate`, then filter writes by last-stored close time" to the same bounded-window incremental approach, deriving the pre-anchor trend state from the warm-up window so first-new-candle flip detection stays correct.
- Both services **skip an asset entirely** when its latest finalized candle is not newer than its latest stored indicator/signal-state row — no fetch, no recompute, no upsert attempts. Assets that errored on a prior run still catch up naturally, because their stored close time lags.
- A full-recompute path is retained for both (formula changes, backfills, detected gaps) — via the existing `calculateIncremental(..., fullRecalc=true)` and an equivalent for signal state.
- Merging the two per-asset passes into one (shared candle load + one calculator run writing both tables) is evaluated in `design.md` and **deferred** — once each pass reads only a small bounded window, the shared-load saving is marginal and it would erode the committed-phase-boundary invariant in `pipeline-orchestration`.

No change to SuperTrend math (`SuperTrendCalculator`), the atomic upsert shape, the phase ordering, `ProvisionalTrendService`, or any read/query/API path.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `supertrend-indicator-core`: the incremental recomputation requirement is extended so the **candle input load** is bounded to the warm-up window (not just the returned output), and per-asset computation is skipped when no new finalized candle exists since the last stored value.
- `signal-state-detection`: signal-state detection is specified as a bounded incremental recomputation (warm-up window + pre-anchor state) that is likewise skipped when the asset has no new finalized candle, replacing the implicit full-history recompute.

## Impact

- `CandleDao`: new bounded-fetch method (candles for an asset/timeframe from a start close time onward, or a "latest close time" helper) alongside the existing unbounded `findByAssetAndTimeframe` (kept — other callers rely on it).
- `IndicatorComputationService`, `SignalStateDetectionService`: internal computation flow only; constructors and `Main.java` wiring unchanged.
- `SuperTrendCalculator`: possibly a small helper for signal state's "keep results from the anchor onward" windowing; no change to the math.
- Tests: `IndicatorComputationServiceTest`, `SignalStateDetectionServiceIntegrationTest`, `PipelineOrchestratorTest`, `PipelineEndToEndIT` — assertions gain "unchanged assets are skipped" and "bounded fetch" coverage; existing correctness assertions must still pass unchanged.
- No schema migration.
