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

- [ ] Extend `IngestionStats` (if needed)
  - [ ] Add `errorCount`
  - [ ] Add `firstErrorMessage`
- [ ] Ensure `CandleIngestionService`:
  - [ ] Tracks inserted / updated / skipped counts
  - [ ] Increments errorCount on asset failure
  - [ ] Captures first error message only
  - [ ] Returns populated `IngestionStats`

---

# Phase 4 — Orchestration Layer

- [ ] Create `IngestionOrchestrator`
- [ ] Implement `runInternal(provider, timeframe)`
  - [ ] Concurrency check (RUNNING row exists?)
  - [ ] If exists → throw `IngestionAlreadyRunningException`
  - [ ] Create RUNNING `IngestionRun` record
  - [ ] Call `CandleIngestionService`
  - [ ] Derive final status
  - [ ] Compute duration_ms
  - [ ] Persist final state
- [ ] Ensure try/finally ensures run is finalized
- [ ] Add logging with runId

---

# Phase 5 — Scheduler Refactor

- [ ] Refactor scheduled ingestion method
  - [ ] Replace direct service call with orchestrator call
- [ ] Handle concurrency rejection gracefully
- [ ] Log skipped execution message

---

# Phase 6 — Manual Trigger Endpoint

- [ ] Create `IngestionController`
- [ ] Add `POST /internal/ingestion/run`
- [ ] Accept optional provider + timeframe
- [ ] Call `IngestionOrchestrator.runInternal`
- [ ] Return runId + status STARTED
- [ ] Return HTTP 409 if ingestion already running
- [ ] Add basic controller test

---

# Phase 7 — Status Derivation Logic

- [ ] Implement status classification:
  - [ ] SUCCESS
  - [ ] PARTIAL
  - [ ] FAILED
- [ ] Add unit tests for status derivation
- [ ] Add test for duration calculation

---

# Phase 8 — Integration Tests

Using Testcontainers:

- [ ] SUCCESS scenario
  - [ ] Stub provider
  - [ ] Run ingestion
  - [ ] Assert one ingestion_run row
  - [ ] Validate counts + SUCCESS status

- [ ] PARTIAL scenario
  - [ ] Force one asset failure
  - [ ] Assert PARTIAL + error_count

- [ ] FAILED scenario
  - [ ] Force all assets fail
  - [ ] Assert FAILED

- [ ] Concurrency scenario
  - [ ] Insert RUNNING row manually
  - [ ] Attempt ingestion
  - [ ] Expect rejection

---

# Phase 9 — Manual Verification

- [ ] Start app
- [ ] Trigger manual endpoint
- [ ] Verify ingestion_run row created
- [ ] Verify scheduler still works
- [ ] Verify 409 when triggering twice quickly
- [ ] Confirm candle data remains deterministic

---

# Definition of Done

- [ ] Every ingestion creates exactly one run record
- [ ] Manual trigger works
- [ ] Scheduler uses orchestrator
- [ ] Concurrency protection enforced
- [ ] Status classification correct
- [ ] Integration tests pass
- [ ] No regression in candle ingestion logic

---

This story is complete when ingestion is fully observable,
manually operable, and concurrency-safe.