## ADDED Requirements

### Requirement: Market pipeline maintains phase-based execution model
The system SHALL execute the market pipeline as a sequence of phases with explicit synchronization barriers, supporting both sequential and parallel execution within phases.

#### Scenario: Pipeline executes in 6 distinct phases
- **WHEN** MarketPipelineService.runDaily() is called
- **THEN** execution flows through phases: INGESTION → INDICATOR → SIGNAL → MARKET_PULSE → W1_ROLLUP → W1_PROCESSING

#### Scenario: External API behavior is unchanged
- **WHEN** caller invokes /api/v1/internal/ingestion/run
- **THEN** response is identical (ACCEPTED with run ID) regardless of internal parallelization

#### Scenario: Pipeline semantics are preserved
- **WHEN** pipeline completes successfully
- **THEN** all data is identical to sequential execution (deterministic output)

### Requirement: Phase synchronization prevents race conditions
The system SHALL ensure each phase completes fully before the next phase begins, preventing data consistency issues.

#### Scenario: Phase 2 completes before Phase 3 starts
- **WHEN** indicator computation for all assets finishes
- **THEN** the system waits for all async tasks to complete before starting signal detection

#### Scenario: Phase dependency invariants are enforced
- **WHEN** Phase N requires output from Phase N-1
- **THEN** execution blocks if Phase N-1 did not complete successfully

### Requirement: Pipeline concurrency is configurable
The system SHALL allow runtime configuration of concurrency behavior to balance performance and safety.

#### Scenario: Parallelization can be disabled
- **WHEN** application is configured with parallelization disabled
- **THEN** all phases execute sequentially as before

#### Scenario: Thread pool size is configurable
- **WHEN** application properties define executor pool sizes
- **THEN** the pipeline uses specified sizes instead of defaults

#### Scenario: Configuration changes take effect on next run
- **WHEN** thread pool configuration changes
- **THEN** the next pipeline invocation uses new settings

### Requirement: Pipeline provides observability for parallelization
The system SHALL expose metrics and logging to monitor parallel execution and diagnose issues.

#### Scenario: Phase execution times are logged
- **WHEN** each phase completes
- **THEN** log entry includes phase name, duration, asset count processed, and number of failures (if any)

#### Scenario: Executor pool metrics are available
- **WHEN** pipeline executes
- **THEN** metrics are recorded for active threads, queued tasks, and completed tasks

#### Scenario: Concurrent modification failures are logged
- **WHEN** optimistic locking conflict occurs
- **THEN** error is logged with asset ID, timestamp, and retry information
