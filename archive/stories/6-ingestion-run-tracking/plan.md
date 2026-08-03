# Story 001.5 — Ingestion Run Tracking & Manual Trigger
## Implementation Plan (plan.md)

---

# Phase 1 — Database Schema

## 1.1 Create ENUM

- Add Liquibase changeSet to create:

    ingestion_run_status AS ENUM (
        'RUNNING',
        'SUCCESS',
        'PARTIAL',
        'FAILED'
    )

- Ensure idempotent creation (check existence)

---

## 1.2 Create ingestion_run Table

Add Liquibase changeSet to create:

- id (BIGSERIAL PK)
- provider (provider ENUM NOT NULL)
- timeframe (timeframe ENUM NOT NULL)
- started_at (TIMESTAMPTZ NOT NULL)
- finished_at (TIMESTAMPTZ NULL)
- duration_ms (BIGINT NULL)
- status (ingestion_run_status NOT NULL)
- asset_count (INT NOT NULL)
- inserted_count (INT NOT NULL DEFAULT 0)
- updated_count (INT NOT NULL DEFAULT 0)
- skipped_count (INT NOT NULL DEFAULT 0)
- error_count (INT NOT NULL DEFAULT 0)
- error_sample (TEXT NULL)
- created_at (TIMESTAMPTZ DEFAULT now())

---

## 1.3 Add Indexes

- Index on (started_at DESC)
- Composite index on (provider, timeframe, started_at DESC)
- Partial index on (provider, timeframe) WHERE status = 'RUNNING'

Do NOT attempt hard DB-level uniqueness on RUNNING rows.
Concurrency enforcement handled in application layer.

---

# Phase 2 — Domain & Repository Layer

## 2.1 Create IngestionRun Entity

- Map provider as NAMED_ENUM
- Map timeframe as NAMED_ENUM
- Map status as NAMED_ENUM
- Map timestamps as OffsetDateTime (UTC)
- Map duration_ms as Long

Use:

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)

for ENUM mappings.

---

## 2.2 Create IngestionRunRepository

Add methods:

- Optional<IngestionRun> findFirstByProviderAndTimeframeAndStatusOrderByStartedAtDesc(...)
- Optional<IngestionRun> findTopByProviderAndTimeframeOrderByStartedAtDesc(...)
- List<IngestionRun> findByStatus(...)

Add basic repository test (Testcontainers).

---

# Phase 3 — Orchestration Layer

Create new component:

    IngestionOrchestrator

This is the only class allowed to create/update ingestion_run rows.

---

## 3.1 runInternal(provider, timeframe)

Flow:

1. Concurrency check:
   - Query repository for RUNNING row
   - If exists → reject

2. Create RUNNING row:
   - started_at = now
   - status = RUNNING
   - asset_count from repository
   - counts = 0

3. Call CandleIngestionService.ingestDaily()

4. Capture returned IngestionStats

5. Determine status:
   - SUCCESS / PARTIAL / FAILED

6. Finalize row:
   - finished_at
   - duration_ms
   - counts
   - error_sample (if any)

7. Save

Must use try/finally to ensure finalization attempt.

---

## 3.2 Concurrency Handling Strategy

Initial implementation:

- Application-level check:
  - If RUNNING row exists → reject

Optional future enhancement:
- PostgreSQL advisory lock

For this story, application-level check is sufficient.

---

# Phase 4 — Scheduler Integration

Refactor scheduled method:

Old:
    CandleIngestionService.ingestDaily()

New:
    IngestionOrchestrator.runScheduled()

Scheduler must:

- Catch concurrency rejection
- Log: "Ingestion skipped — already running"

No duplicate logic allowed.

---

# Phase 5 — Manual Trigger Endpoint

## 5.1 Create Internal Controller

    @RestController
    @RequestMapping("/internal/ingestion")

Endpoint:

    POST /run

Accept body:

- provider (optional, default BINANCE)
- timeframe (optional, default D1)

Return:

{
  "runId": 123,
  "status": "STARTED"
}

---

## 5.2 Error Handling

If RUNNING exists:

- Return HTTP 409
- Response:

{
  "error": "INGESTION_ALREADY_RUNNING"
}

---

## 5.3 Security

For now:

- Restrict to local profile
OR
- Protect via Spring Security basic auth

Do not expose publicly.

---

# Phase 6 — Error Sampling

In CandleIngestionService:

- Capture first asset exception message
- Return in IngestionStats
- Orchestrator stores truncated (1000 char) message in error_sample

No stack traces persisted.

---

# Phase 7 — Testing Strategy

## 7.1 Unit Tests

- Status derivation logic
- Duration calculation
- Concurrency rejection
- Manual trigger uses same orchestration path

---

## 7.2 Integration Tests (Testcontainers)

Scenario 1 — SUCCESS
- Stub provider
- Run ingestion
- Assert one ingestion_run row
- Validate counts + status

Scenario 2 — PARTIAL
- Force one asset failure
- Assert status = PARTIAL
- error_count = 1
- error_sample populated

Scenario 3 — FAILED
- Force all assets fail
- Assert status = FAILED

Scenario 4 — Concurrency
- Manually create RUNNING row
- Trigger ingestion
- Expect rejection

---

# Phase 8 — Logging & Observability

Log at:

- Run start
- Run completion
- Run failure
- Concurrency rejection

Log format must include:

- runId
- provider
- timeframe
- duration
- counts

---

# Phase 9 — Refactoring Boundaries

Ensure:

- CandleIngestionService does NOT know about ingestion_run
- Only Orchestrator handles run persistence
- Controller does NOT call ingestion service directly
- Scheduler does NOT call ingestion service directly

Single execution path only.

---

# Final Architecture After Story

Scheduler
    ↓
Manual REST
    ↓
IngestionOrchestrator
    ↓
CandleIngestionService
    ↓
Provider
    ↓
Candle Repository

And independently:

IngestionOrchestrator
    ↓
IngestionRunRepository

---

# Risk Mitigation

Edge Case: App crash mid-run

Result:
- RUNNING row remains unfinished

Acceptable for now.
Future story may:
- Detect stale RUNNING rows
- Mark them FAILED automatically

---

# Implementation Order

1. Liquibase migration
2. Entity + repository
3. Orchestrator
4. Refactor scheduler
5. Add controller
6. Write tests
7. Verify idempotency
8. Manual testing

---

# Completion Criteria

✔ ingestion_run table exists  
✔ Every ingestion creates exactly one row  
✔ Status correctly classified  
✔ Concurrency rejection works  
✔ Manual trigger works  
✔ Scheduler uses same orchestration path  
✔ Candle determinism unchanged  
