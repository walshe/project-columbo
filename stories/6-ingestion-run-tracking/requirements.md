# Story 001.5 — Ingestion Run Tracking & Manual Trigger

## 1. Objective

Introduce persistent ingestion run tracking and a safe manual trigger mechanism
to transform ingestion from a background task into an observable, operable system component.

The system must:

- Persist an audit record for every ingestion execution
- Track execution metrics and status
- Support safe manual triggering
- Prevent concurrent overlapping runs
- Preserve determinism and idempotency of candle data

This story elevates ingestion to production-grade operational maturity.

---

## 2. Scope

This story:

- Adds an `ingestion_run` table
- Wraps ingestion execution with run lifecycle tracking
- Introduces a manual trigger endpoint
- Prevents concurrent ingestion runs
- Tracks execution metadata (counts, duration, errors)

This story does NOT:

- Change candle computation logic
- Modify provider parsing
- Add retries or rate-limit backoff
- Add Prometheus/OpenTelemetry
- Add distributed job orchestration

---

## 3. Functional Requirements

---

## 3.1 Run Lifecycle Tracking

Every ingestion invocation must create exactly one `ingestion_run` row.

Lifecycle:

1. RUNNING (created at start)
2. SUCCESS / PARTIAL / FAILED (set at completion)

Each run must record:

- provider
- timeframe
- started_at
- finished_at
- duration_ms
- asset_count
- inserted_count
- updated_count
- skipped_count
- error_count
- status
- optional error_sample

---

## 3.2 Run Status Rules

At completion:

- SUCCESS  
  → error_count == 0

- PARTIAL  
  → error_count > 0 AND (inserted_count + updated_count + skipped_count) > 0

- FAILED  
  → error_count > 0 AND no successful asset processing

---

## 3.3 Concurrency Control (Critical)

The system must prevent overlapping ingestion runs for the same:

    provider + timeframe

Rules:

- If a RUNNING ingestion exists for same provider/timeframe
  → reject new invocation
  → return HTTP 409 (manual trigger)
  → scheduler must skip execution

Implementation options:

- Database check for RUNNING row
- OR PostgreSQL advisory lock (recommended for production safety)

Concurrency protection must be atomic.

---

## 3.4 Manual Trigger Support

Expose an internal administrative endpoint:

    POST /internal/ingestion/run

Request body (optional):

```json
{
  "provider": "BINANCE",
  "timeframe": "D1",
  "mode": "INCREMENTAL"
}
````

Behavior:

* Validates no RUNNING ingestion exists
* Starts ingestion using same internal orchestration path as scheduler
* Returns:

```json
{
  "runId": 123,
  "status": "STARTED"
}
```

Manual trigger must:

* Reuse identical ingestion service
* Reuse run tracking
* Not duplicate logic
* Not bypass finalization rules

---

## 3.5 Scheduler Integration

Scheduled ingestion must:

* Call same orchestration method as manual trigger
* Respect concurrency protection
* Log skipped execution if a run is already active

---

## 3.6 Error Sampling

If failures occur:

* Store first error message (max 1000 chars) in `error_sample`
* Full stack traces remain in logs only

---

## 3.7 Determinism & Idempotency

* Run tracking is append-only
* Every invocation creates a new row
* Re-running ingestion produces new run row
* Candle logic remains fully idempotent
* Run metadata does not affect data determinism

---

## 4. Data Model

---

### 4.1 ENUM Definitions

Create ENUM:

```
ingestion_run_status AS ENUM (
    'RUNNING',
    'SUCCESS',
    'PARTIAL',
    'FAILED'
)
```

---

### 4.2 ingestion_run Table

Columns:

* id (PK)

* provider (provider ENUM NOT NULL)

* timeframe (timeframe ENUM NOT NULL)

* started_at (TIMESTAMPTZ NOT NULL)

* finished_at (TIMESTAMPTZ NULL)

* duration_ms (BIGINT NULL)

* status (ingestion_run_status ENUM NOT NULL)

* asset_count (INT NOT NULL)

* inserted_count (INT NOT NULL)

* updated_count (INT NOT NULL)

* skipped_count (INT NOT NULL)

* error_count (INT NOT NULL)

* error_sample (TEXT NULL)

* created_at (TIMESTAMPTZ DEFAULT now())

Indexes:

* (started_at DESC)
* (provider, timeframe, started_at DESC)
* Partial index on status = 'RUNNING'

Constraints:

* Only one RUNNING row per provider + timeframe allowed

---

## 5. Service Architecture

---

### 5.1 IngestionOrchestrator (New)

Central orchestration entrypoint.

Methods:

* runScheduled()
* runManual(provider, timeframe)
* runInternal(provider, timeframe)

Responsibilities:

* Concurrency check
* Create RUNNING record
* Invoke CandleIngestionService
* Finalize run record
* Handle error classification
* Release lock

---

### 5.2 CandleIngestionService (Existing)

Remains focused on:

* Fetching assets
* Calling provider
* Persisting candles
* Returning IngestionStats

No direct DB write to ingestion_run.

Separation of concerns preserved.

---

## 6. Non-Functional Requirements

* O(n) per asset processing
* No cross-asset coupling
* No cross-timeframe coupling
* Run tracking must not significantly slow ingestion
* Concurrency protection must be reliable
* System must recover cleanly if app crashes mid-run:

  * RUNNING rows older than threshold may be considered stale (future enhancement)

---

## 7. Testing Requirements

---

### Unit Tests

* Status derivation logic
* Duration calculation
* Concurrency rejection logic
* Manual trigger path calls same orchestration method

---

### Integration Tests (Testcontainers)

* Run ingestion
* Assert one ingestion_run row created
* Assert correct counts and status
* Trigger manual run while one is RUNNING → expect rejection
* Force one asset failure → expect PARTIAL
* Force total failure → expect FAILED

---

## 8. Operational Guarantees

After this story, the system must be able to answer:

* When did ingestion last run?
* Was it successful?
* How long did it take?
* How many rows were affected?
* Did any assets fail?
* Is ingestion currently running?

This establishes ingestion as a first-class operational component.

---

## 9. Out of Scope (Future Stories)

* Per-asset ingestion_run_detail table
* Retry / backoff strategy
* Rate-limit tracking
* REST endpoint to query run history
* Prometheus metrics export
* Distributed ingestion cluster coordination
* Backfill window selection
* Stale RUNNING row auto-recovery
