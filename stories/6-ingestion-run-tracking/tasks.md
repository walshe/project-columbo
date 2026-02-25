# Story 001.5 — Ingestion Run Tracking & Manual Trigger
## tasks.md

---

# Phase 1 — Database Migration

- [x] Create Liquibase changeSet for `ingestion_run_status` ENUM
- [x] Create `ingestion_run` table
- [x] Add indexes:
  - [x] (started_at DESC)
  - [x] (provider, timeframe, started_at DESC)
  - [x] Partial index on (provider, timeframe) WHERE status = 'RUNNING'
- [x] Verify migration runs successfully
- [x] Confirm table + enum visible in DB

---

# Phase 2 — Domain Layer

- [x] Create `IngestionRunStatus` enum (Java)
- [x] Create `IngestionRun` entity
  - [x] Map provider as `NAMED_ENUM`
  - [x] Map timeframe as `NAMED_ENUM`
  - [x] Map status as `NAMED_ENUM`
  - [x] Map timestamps as `OffsetDateTime`
  - [x] Map duration_ms as `Long`
- [x] Add default values for counts (0)
- [x] Implement `IngestionRunRepository`
  - [x] `findFirstByProviderAndTimeframeAndStatusOrderByStartedAtDesc`
  - [x] `findTopByProviderAndTimeframeOrderByStartedAtDesc`
- [x] Add repository integration test (Testcontainers)

---

# Phase 3 — Stats & Error Propagation

- [x] Extend `IngestionStats` (if needed)
  - [x] Add `errorCount`
  - [x] Add `firstErrorMessage`
- [x] Ensure `CandleIngestionService`:
  - [x] Tracks inserted / updated / skipped counts
  - [x] Increments errorCount on asset failure
  - [x] Captures first error message only
  - [x] Returns populated `IngestionStats`

---

# Phase 4 — Orchestration Layer

- [x] Create `IngestionOrchestrator`
- [x] Implement `runInternal(provider, timeframe)`
  - [x] Concurrency check (RUNNING row exists?)
  - [x] If exists → throw `IngestionAlreadyRunningException`
  - [x] Create RUNNING `IngestionRun` record
  - [x] Call `CandleIngestionService`
  - [x] Derive final status
  - [x] Compute duration_ms
  - [x] Persist final state
- [x] Ensure try/finally ensures run is finalized
- [x] Add logging with runId

---

# Phase 5 — Scheduler Refactor

- [x] Refactor scheduled ingestion method
  - [x] Replace direct service call with orchestrator call
- [x] Handle concurrency rejection gracefully
- [x] Log skipped execution message

---

# Phase 6 — Manual Trigger Endpoint

- [x] Create `IngestionController`
- [x] Add `POST /internal/ingestion/run`
- [x] Accept optional provider + timeframe
- [x] Call `IngestionOrchestrator.runInternal`
- [x] Return runId + status STARTED
- [x] Return HTTP 409 if ingestion already running
- [x] Add basic controller test

---

# Phase 7 — Status Derivation Logic

- [x] Implement status classification:
  - [x] SUCCESS
  - [x] PARTIAL
  - [x] FAILED
- [x] Add unit tests for status derivation
- [x] Add test for duration calculation

---

# Phase 8 — Integration Tests

Using Testcontainers:

- [x] SUCCESS scenario
  - [x] Stub provider
  - [x] Run ingestion
  - [x] Assert one ingestion_run row
  - [x] Validate counts + SUCCESS status

- [x] PARTIAL scenario
  - [x] Force one asset failure
  - [x] Assert PARTIAL + error_count

- [x] FAILED scenario
  - [x] Force all assets fail
  - [x] Assert FAILED

- [x] Concurrency scenario
  - [x] Insert RUNNING row manually
  - [x] Attempt ingestion
  - [x] Expect rejection

---

# Phase 9 — Manual Verification

- [x] Start app
- [x] Trigger manual endpoint
- [x] Verify ingestion_run row created
- [x] Verify scheduler still works
- [x] Verify 409 when triggering twice quickly
- [x] Confirm candle data remains deterministic

---

# Definition of Done

- [x] Every ingestion creates exactly one run record
- [x] Manual trigger works
- [x] Scheduler uses orchestrator
- [x] Concurrency protection enforced
- [x] Status classification correct
- [x] Integration tests pass
- [x] No regression in candle ingestion logic

---

This story is complete when ingestion is fully observable,
manually operable, and concurrency-safe.