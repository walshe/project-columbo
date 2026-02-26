# Story 008 — Tasks
## Incremental Provider Time Window Optimization

This task list implements incremental time-window fetching using Binance `startTime` and `endTime`.

Follow phases in order.

---

## Phase 1 — Configuration

### 1.1 Add Backfill Start Property

- [ ] Add configuration property:

  ```properties
  app.ingestion.backfill-start=2020-01-01T00:00:00Z
  ```

- [ ] Bind property to a configuration class
- [ ] Ensure it is parsed as `OffsetDateTime` in UTC
- [ ] Add method to convert to epoch milliseconds

---

## Phase 2 — Provider Interface Update

### 2.1 Update Interface

- [ ] Change provider method from:

  ```java
  fetchDailyCandles(String symbol)
  ```

  to:

  ```java
  fetchDailyCandles(String symbol, Long startTime, Long endTime)
  ```

- [ ] Ensure both parameters are nullable
- [ ] Update all implementations accordingly

### 2.2 Update Binance Implementation

- [ ] Modify KLINES request to include parameters:
  - `symbol`
  - `interval=1d`
  - `startTime`
  - `endTime`
- [ ] Use a URI builder (no string concatenation)
- [ ] Ensure timestamps are epoch milliseconds
- [ ] Ensure UTC consistency
- [ ] Verify request URL in logs

---

## Phase 3 — Repository Support

### 3.1 Add Latest Close Time Query

- [ ] Add repository method that:
  - Queries by asset + timeframe
  - Orders by `close_time` DESC
  - Limits 1
  - Returns `Optional<OffsetDateTime>`

---

## Phase 4 — Ingestion Service Update

### 4.1 Compute Finalized Boundary

- [ ] Implement utility method:
  - `getFinalizedBoundary()` → UTC start of current day
- [ ] Ensure the same logic is used across the system

### 4.2 Compute Time Window

- [ ] Inside ingestion loop:
  - Fetch last stored candle close time
  - If exists: compute `startTime = lastClose + 1ms`
  - If not: compute `startTime = backfillStart`
  - Compute `endTime = finalizedBoundary`
  - Log window as:

    ```text
    INGESTION_WINDOW asset=... start=... end=...
    ```

### 4.3 Implement Skip Condition

- [ ] If `startTime >= endTime`:
  - Log "No new candles required"
  - Skip provider call
  - Continue to next asset

### 4.4 Call Provider With Window

- [ ] Pass `startTime` and `endTime` to provider
- [ ] Handle empty response gracefully
- [ ] Preserve existing upsert logic

---

## Phase 5 — Defensive Guard

- [ ] Before persisting each candle:
  - Ensure `close_time < finalizedBoundary`
  - Discard any invalid candle returned by provider

---

## Phase 6 — Regression Safety

- [ ] Verify no changes to:
  - Candle schema
  - Unique constraints
  - Upsert logic
  - Pipeline orchestration
- [ ] Confirm `ingestion_run` tracking unchanged

---

## Phase 7 — Unit Tests

- [ ] `startTime` computed correctly when `lastClose` exists
- [ ] `startTime` computed correctly when DB empty
- [ ] `endTime` equals finalized boundary
- [ ] Skip condition works
- [ ] Provider called with correct timestamps
- [ ] Provider not called when no delta

---

## Phase 8 — Integration Tests

### Scenario 1 — Initial Backfill

- [ ] Empty database
- [ ] Run ingestion
- [ ] Verify candles inserted

### Scenario 2 — Incremental Delta

- [ ] Database contains candles up to yesterday
- [ ] Run ingestion
- [ ] Verify only new candle fetched

### Scenario 3 — No Delta

- [ ] Database fully up to date
- [ ] Verify provider not called

### Scenario 4 — Idempotency

- [ ] Run ingestion twice
- [ ] Verify no duplicate candles
- [ ] Verify identical row count

---

## Phase 9 — Validation Checklist

- [ ] No schema changes introduced
- [ ] No regression in candle count
- [ ] No regression in `signal_state`
- [ ] No regression in `market_pulse`
- [ ] Logs show correct time windows
- [ ] API payload size reduced
- [ ] Ingestion remains deterministic

---

## Done Criteria

Story complete when:

- [ ] Provider calls include `startTime` and `endTime`
- [ ] Only missing candles are requested
- [ ] No unnecessary API calls occur
- [ ] No open candles are stored
- [ ] Ingestion remains idempotent
- [ ] System behavior unchanged except efficiency improvement