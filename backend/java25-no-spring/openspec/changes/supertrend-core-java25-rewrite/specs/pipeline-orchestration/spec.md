## ADDED Requirements

### Requirement: Sequential phase ordering with per-phase parallelism
A pipeline run SHALL execute its phases strictly in order — ingestion, then D1 indicator computation, then D1 signal detection, then D1 market pulse, then D1-to-W1 rollup, then W1 indicator computation, W1 signal detection, and W1 market pulse — with each phase fully complete (and, where persistence is involved, committed) before the next phase begins. Within a phase, per-asset work MAY run concurrently using virtual threads, but cross-phase ordering SHALL NOT be relaxed.

#### Scenario: Signal detection sees committed indicator writes
- **WHEN** D1 signal detection runs
- **THEN** it reads indicator values that were fully persisted by the preceding D1 indicator computation phase for every active asset

#### Scenario: Per-asset indicator computation runs concurrently
- **WHEN** the indicator computation phase processes multiple active assets
- **THEN** individual assets' computations may execute concurrently on virtual threads, but the phase as a whole does not begin until ingestion has fully completed, and does not report complete until every asset's computation has finished

### Requirement: Concurrent-run prevention
The system SHALL reject a new pipeline run request for a given provider+timeframe if a run for that same provider+timeframe is currently in `RUNNING` status, responding with a conflict rather than starting a second run.

#### Scenario: Second run rejected while one is in progress
- **WHEN** a new run is requested for a provider+timeframe while an existing run for that same provider+timeframe is `RUNNING`
- **THEN** the new run is rejected with a conflict response and no new run record is created

### Requirement: Run status lifecycle
Each pipeline run SHALL be tracked with a status of `RUNNING` while in progress, transitioning on completion to `SUCCESS` (zero errors), `PARTIAL` (some assets errored, some succeeded), or `FAILED` (all assets errored) — along with inserted/updated/skipped/error counts and a truncated error sample.

#### Scenario: All-success run
- **WHEN** a run completes with zero per-asset errors
- **THEN** its final status is `SUCCESS`

#### Scenario: Mixed-result run
- **WHEN** a run completes with some assets erroring and others succeeding
- **THEN** its final status is `PARTIAL`

#### Scenario: All-failed run
- **WHEN** every asset in a run errors
- **THEN** its final status is `FAILED`

### Requirement: Manual and scheduled triggers use the identical orchestration path
Whether a run is started by the scheduled daily trigger or by a manual API request, the system SHALL execute the exact same orchestration sequence and produce the same kind of run record — no divergent code path for manual vs. scheduled runs.

#### Scenario: Manual trigger produces the same run record shape as scheduled
- **WHEN** a run is started manually via the ingestion-trigger API instead of the daily schedule
- **THEN** the resulting run record has the same fields, phases, and status semantics as a scheduled run
