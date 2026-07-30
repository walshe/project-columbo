# concurrent-asset-processing Specification

## Purpose
TBD - created by archiving change parallelize-ingest-run-endpoint. Update Purpose after archive.
## Requirements
### Requirement: Transaction isolation for concurrent asset writes
The system SHALL isolate database writes for each asset to prevent cross-asset data corruption and ensure consistent state under concurrent execution.

#### Scenario: Each asset uses isolated transaction
- **WHEN** an asset's indicator computation completes
- **THEN** all writes for that asset occur within a single transaction that commits or rolls back independently

#### Scenario: Failed transaction does not block other assets
- **WHEN** asset A's transaction fails to commit
- **THEN** asset A's changes rollback completely, other assets' transactions proceed unaffected

#### Scenario: No cross-asset transaction dependencies
- **WHEN** two assets compute indicators concurrently
- **THEN** neither asset's transaction waits for the other to complete

### Requirement: Concurrent database access is safe
The system SHALL prevent deadlocks, connection pool exhaustion, and data race conditions when multiple threads access the database simultaneously.

#### Scenario: Database connection pool does not exhaust
- **WHEN** all executor threads execute concurrently
- **THEN** active database connections do not exceed 60% of the configured connection pool size

#### Scenario: Long-running locks are avoided
- **WHEN** an asset's computation reads old data and writes new results
- **THEN** the transaction duration is minimized (read, compute, write immediately)

#### Scenario: Optimistic locking detects conflicts
- **WHEN** two threads attempt to update the same indicator record concurrently
- **THEN** optimistic lock version conflict is detected and the operation is retried or fails cleanly

### Requirement: Error handling in parallel context
The system SHALL gracefully handle failures in concurrent tasks without cascading errors.

#### Scenario: Partial asset failure is recorded
- **WHEN** asset computation throws an exception
- **THEN** the error is recorded in the IngestionRun record with asset ID and error details

#### Scenario: Phase completes with partial success
- **WHEN** 95 of 100 assets complete successfully and 5 fail
- **THEN** Phase 2 completes with status PARTIAL_SUCCESS and includes failure details for each failed asset

#### Scenario: Uncaught exception in worker thread is captured
- **WHEN** a parallel task encounters an unexpected exception
- **THEN** the exception is captured, logged, and propagated to the phase's completion handler

