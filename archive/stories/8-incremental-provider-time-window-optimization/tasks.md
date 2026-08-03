# Story 008 — Tasks
## Incremental Provider Time Window Optimization

This task list implements incremental time-window fetching using Binance `startTime` and `endTime`.

Follow phases in order.

---

## Phase 1 — Configuration

### 1.1 Add Backfill Start Property

- [x] Add configuration property:

  ```properties
  app.ingestion.backfill-start=2025-12-12T00:00:00Z
  ```

- [x] Bind property to a configuration class
- [x] Ensure it is parsed as `OffsetDateTime` in UTC
- [x] Add method to convert to epoch milliseconds

---

## Phase 2 — Provider Interface Update

### 2.1 Update Interface

- [x] Change provider method from:

  ```java
  fetchDailyCandles(String symbol)
  ```

  to:

  ```java
  fetchDailyCandles(String symbol, Long startTime, Long endTime)
  ```

- [x] Ensure both parameters are nullable
- [x] Update all implementations accordingly

### 2.2 Update Binance Implementation

- [x] Modify KLINES request to include parameters:
  - `symbol`
  - `interval=1d`
  - `startTime`
  - `endTime`
- [x] Use a URI builder (no string concatenation)
- [x] Ensure timestamps are epoch milliseconds
- [x] Ensure UTC consistency
- [x] Verify request URL in logs

---

## Phase 3 — Repository Support

### 3.1 Add Latest Close Time Query

- [x] Add repository method that:
  - Queries by asset + timeframe
  - Orders by `close_time` DESC
  - Limits 1
  - Returns `Optional<OffsetDateTime>`

---

## Phase 4 — Ingestion Service Update

### 4.1 Compute Finalized Boundary

- [x] Implement utility method:
  - `getFinalizedBoundary()` → UTC start of current day
- [x] Ensure the same logic is used across the system

### 4.2 Compute Time Window

- [x] Inside ingestion loop:
  - Fetch last stored candle close time
  - If exists: compute `startTime = lastClose + 1ms`
  - If not: compute `startTime = backfillStart`
  - Compute `endTime = finalizedBoundary`
  - Log window as:

    ```text
    INGESTION_WINDOW asset=... start=... end=...
    ```

### 4.3 Implement Skip Condition

- [x] If `startTime >= endTime`:
  - Log "No new candles required"
  - Skip provider call
  - Continue to next asset

### 4.4 Call Provider With Window

- [x] Pass `startTime` and `endTime` to provider
- [x] Handle empty response gracefully
- [x] Preserve existing upsert logic

---

## Phase 5 — Defensive Guard

- [x] Before persisting each candle:
  - [x] Ensure `close_time < finalizedBoundary`
  - [x] Discard any invalid candle returned by provider

---

## Phase 6 — Regression Safety

- [x] Verify no changes to:
  - [x] Candle schema
  - [x] Unique constraints
  - [x] Upsert logic
  - [x] Pipeline orchestration
- [x] Confirm `ingestion_run` tracking unchanged

---

## Phase 7 — Unit Tests

- [x] `startTime` computed correctly when `lastClose` exists
- [x] `startTime` computed correctly when DB empty
- [x] `endTime` equals finalized boundary
- [x] Skip condition works
- [x] Provider called with correct timestamps
- [x] Provider not called when no delta

---

## Phase 8 — Integration Tests

### Scenario 1 — Initial Backfill

- [x] Empty database
- [x] Run ingestion
- [x] Verify candles inserted

### Scenario 2 — Incremental Delta

- [x] Database contains candles up to yesterday
- [x] Run ingestion
- [x] Verify only new candle fetched

### Scenario 3 — No Delta

- [x] Database fully up to date
- [x] Verify provider not called

### Scenario 4 — Idempotency

- [x] Run ingestion twice
- [x] Verify no duplicate candles
- [x] Verify identical row count

---

## Phase 9 — Validation Checklist

- [x] No schema changes introduced
- [x] No regression in candle count
- [x] No regression in `signal_state`
- [x] No regression in `market_pulse`
- [x] Logs show correct time windows
- [x] API payload size reduced
- [x] Ingestion remains deterministic

---

## Done Criteria

Story complete when:

- [x] Provider calls include `startTime` and `endTime`
- [x] Only missing candles are requested
- [x] No unnecessary API calls occur
- [x] No open candles are stored
- [x] Ingestion remains idempotent
- [x] System behavior unchanged except efficiency improvement