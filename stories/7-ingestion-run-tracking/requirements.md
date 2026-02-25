# Story 001.5 — Ingestion Run Tracking (Provider Audit)

## 1. Objective

Persist an audit record for each ingestion execution (e.g. Binance candle fetch),
so the system can answer:

- When did ingestion last run?
- Did it succeed / partially succeed / fail?
- How many candles were inserted / updated / skipped?
- How many assets were processed and how many failed?
- How long did the run take?

This story adds observability and determinism metadata without changing candle logic.

---

## 2. Scope

This story:

- Adds an `ingestion_run` table to persist run metadata
- Creates an `IngestionRunService` wrapper to record start/end of each run
- Records per-asset failures at a summary level (counts + optional error message sample)

This story does NOT:

- Change candle upsert semantics
- Change provider API contract
- Add retries/backoff (future story)
- Add OpenTelemetry/exporters
- Add REST endpoints (optional future story)

---

## 3. Functional Requirements

### 3.1 Create IngestionRun Record

When `CandleIngestionService.ingestDaily()` begins:

- Create `ingestion_run` row with:
  - provider = BINANCE
  - timeframe = D1
  - started_at = now UTC
  - status = RUNNING
  - asset_count = number of active assets at start
  - inserted_count/updated_count/skipped_count/error_count = 0

### 3.2 Update IngestionRun During Execution

As each asset is processed:

- Accumulate totals:
  - inserted_count
  - updated_count
  - skipped_count
- If an asset fails (exception):
  - increment `error_count`
  - continue processing other assets (existing behavior)

### 3.3 Finalize IngestionRun Record

At the end of ingestion:

- finished_at = now UTC
- duration_ms = finished_at - started_at (ms)
- status rules:
  - SUCCESS: error_count == 0
  - PARTIAL: error_count > 0 AND (inserted_count + updated_count + skipped_count) > 0
  - FAILED: error_count > 0 AND (inserted_count + updated_count + skipped_count) == 0
- Persist final counts and status

### 3.4 Error Message Sampling (Optional but Recommended)

Store an optional `error_sample` text:

- If at least one failure occurred, store the first failure message (truncated to 1000 chars)
- Do not store full stack traces (logs already contain them)

### 3.5 Determinism & Idempotency

Run tracking is append-only:

- Each ingestion invocation creates exactly one run row
- No attempt is made to deduplicate runs
- A rerun creates a new row

This does not affect deterministic candle computation.

---

## 4. Data Model

### 4.1 ENUM Definitions

Create ENUM:

    ingestion_run_status AS ENUM (
        'RUNNING',
        'SUCCESS',
        'PARTIAL',
        'FAILED'
    )

---

### 4.2 ingestion_run Table

Create table:

    ingestion_run

Columns:

- id (PK)
- provider (provider ENUM NOT NULL)          -- reuse existing provider enum if present, else create
- timeframe (timeframe ENUM NOT NULL)

- started_at (TIMESTAMPTZ NOT NULL)
- finished_at (TIMESTAMPTZ NULL)
- duration_ms (BIGINT NULL)

- status (ingestion_run_status ENUM NOT NULL)

- asset_count (INT NOT NULL)

- inserted_count (INT NOT NULL)
- updated_count (INT NOT NULL)
- skipped_count (INT NOT NULL)
- error_count (INT NOT NULL)

- error_sample (TEXT NULL)

- created_at (TIMESTAMPTZ DEFAULT now())

Indexes:

- (started_at DESC)
- (provider, timeframe, started_at DESC)

Notes:
- `finished_at` remains NULL while RUNNING
- `duration_ms` set only after finishing

---

## 5. Service Layer

Create:

    IngestionRunService

Responsibilities:

- startRun(provider, timeframe, assetCount) -> runId
- finishRun(runId, totals, status, errorSample)

Integrate into:

    CandleIngestionService.ingestDaily()

Rules:

- Always start a run before processing assets
- Always finalize the run in a finally block

---

## 6. Non-Functional Requirements

- Must not slow ingestion significantly
- Must not affect candle persistence logic
- Must remain safe if DB is temporarily unavailable:
  - If run tracking fails, ingestion may proceed but must log ERROR
  - (Prefer: treat run tracking as best-effort, not ingestion-fatal)

---

## 7. Testing Requirements

### Unit Tests

- status derivation rules:
  - SUCCESS when error_count == 0
  - PARTIAL when errors exist but some work occurred
  - FAILED when errors exist and no work occurred
- duration_ms computed and persisted

### Integration Tests (Testcontainers)

- Seed active assets + stub provider responses
- Run ingestion
- Assert exactly one ingestion_run row created
- Validate:
  - started_at populated
  - finished_at populated
  - duration_ms > 0
  - status correct
  - counts match expected
- Force one asset failure:
  - status becomes PARTIAL
  - error_count increments
  - error_sample populated

---

## 8. Out of Scope (Future Stories)

- Per-asset ingestion_run_detail table
- Request-level logging (API call counts, rate-limit headers)
- Automatic retries/backoff / circuit breakers
- REST endpoints to query ingestion runs
- Prometheus metrics export