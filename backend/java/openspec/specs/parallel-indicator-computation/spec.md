# parallel-indicator-computation Specification

## Purpose
TBD - created by archiving change parallelize-ingest-run-endpoint. Update Purpose after archive.
## Requirements
### Requirement: Parallel indicator computation for multiple assets
The system SHALL execute SuperTrend and RSI calculations for all active assets concurrently, reducing indicator computation phase duration while maintaining data consistency.

#### Scenario: Multiple assets compute in parallel
- **WHEN** Phase 2 (INDICATOR) begins processing
- **THEN** each active asset's SuperTrend and RSI calculations run on separate threads from a managed thread pool

#### Scenario: All assets complete before next phase
- **WHEN** all asset computations complete (successfully or with errors)
- **THEN** the system waits for all parallel tasks before transitioning to Phase 3 (SIGNAL)

#### Scenario: One asset failure does not block other assets
- **WHEN** SuperTrend computation fails for asset A
- **THEN** asset A's computation is marked failed, other assets continue computing, and the phase fails after all assets complete

#### Scenario: Disabled indicators can be parallelize once enabled
- **WHEN** EMA, MACD, or Thermometer computations are re-enabled
- **THEN** those computations automatically parallelize with existing indicator computations using the same executor pool

### Requirement: Thread pool sizing respects database constraints
The system SHALL configure the parallel executor pool to avoid exhausting database connections while maximizing throughput.

#### Scenario: Pool size scales with asset count
- **WHEN** system has 200 active assets
- **THEN** executor core pool size is 20 and max pool size is 40

#### Scenario: Pool size respects upper bounds
- **WHEN** system has 500+ active assets
- **THEN** executor core pool size does not exceed 8 and max pool size does not exceed 16

#### Scenario: Configuration is overrideable
- **WHEN** application properties specify custom thread pool settings
- **THEN** system uses specified settings instead of defaults

### Requirement: Indicator computation results are deterministic
The system SHALL produce identical results whether indicators are computed sequentially or in parallel.

#### Scenario: Parallel and sequential results match
- **WHEN** Phase 2 completes with parallelization enabled
- **THEN** indicator values in the database are identical to running the phase sequentially

