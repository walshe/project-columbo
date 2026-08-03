# Story 008 — Plan  
## Incremental Provider Time Window Optimization

---

## 1. Purpose

Optimize candle ingestion by fetching only missing candles using provider-supported `startTime` and `endTime` parameters.

This change:

- Reduces unnecessary API payload
- Improves rate-limit safety
- Scales ingestion by delta instead of history
- Preserves full determinism and idempotency

No behavioral changes to candle, signal, or pulse logic are allowed.

---

## 2. Architectural Principles

- Single finalized boundary rule (UTC)
- Deterministic window computation
- Idempotent persistence unchanged
- No schema changes
- No change to pipeline orchestration

---

## 3. Finalized Boundary Rule

For timeframe `D1`:


finalizedBoundary = UTC start of current day


Only candles where:


close_time < finalizedBoundary


are considered valid.

`endTime` passed to Binance must equal `finalizedBoundary`.

All system phases must already use this same rule.

---

## 4. Time Window Strategy

### 4.1 Incremental Mode

If a last stored candle exists:


startTime = lastCloseTime + 1 millisecond


If no candle exists:


startTime = configuredBackfillStart


### 4.2 Full Mode

Ignore last stored state:


startTime = configuredBackfillStart


In both modes:


endTime = finalizedBoundary


---

## 5. Configuration

Add configuration property:


app.ingestion.backfill-start=2020-01-01T00:00:00Z


- Must be parsed as UTC
- Converted to epoch milliseconds

---

## 6. Provider Interface Changes

Update provider interface:

Before:


fetchDailyCandles(String symbol)


After:


fetchDailyCandles(String symbol, Long startTime, Long endTime)


Rules:

- `startTime` and `endTime` nullable
- Incremental mode must supply both
- Full mode may omit lastClose logic but still use finalized boundary

---

## 7. Binance Provider Implementation

Update KLINES call to include:

- symbol
- interval=1d
- startTime
- endTime

Use proper URI builder.

Ensure:

- Millisecond timestamps
- UTC consistency
- No string concatenation errors
- Correct query parameter encoding

---

## 8. Ingestion Service Changes

### 8.1 Retrieve Last Stored Candle

Add repository method:

- Query by asset + timeframe
- Order by close_time descending
- Limit 1

### 8.2 Compute Window

Per asset:

1. Retrieve lastCloseTime
2. Compute startTime
3. Compute endTime
4. Log window boundaries

### 8.3 Skip Condition

If:


startTime >= endTime


Then:

- Do not call provider
- Log skip
- Continue to next asset

---

## 9. Persistence Integrity

No change to:


INSERT ... ON CONFLICT (asset_id, timeframe, close_time)


Upsert behavior remains identical.

Add defensive guard:

If provider returns any candle where:


close_time >= finalizedBoundary


Discard it.

---

## 10. Testing Strategy

### 10.1 Unit Tests

- startTime computed correctly
- endTime equals finalizedBoundary
- skip condition works
- provider receives correct parameters

### 10.2 Integration Tests

Scenario 1 — Initial backfill  
- Empty DB  
- Run ingestion  
- Verify candles inserted  

Scenario 2 — Incremental delta  
- DB contains candles up to yesterday  
- Run ingestion  
- Verify only new candle fetched  

Scenario 3 — No delta  
- DB fully up to date  
- Verify provider not called  

Scenario 4 — Idempotency  
- Run ingestion twice  
- Verify no duplicates  

---

## 11. Validation Checklist Before Merge

- No schema changes introduced
- No regression in candle count
- No regression in signal_state
- No regression in market_pulse
- No change in pipeline orchestration
- Logs clearly show window boundaries
- API payload size reduced
- Ingestion remains deterministic

---

## 12. Expected Outcome

Before optimization:

- Fetch fixed-limit historical candles every run

After optimization:

- Fetch only missing delta candles between stored boundary and finalized boundary

System becomes:

- Quota-efficient
- Production-scalable
- Deterministic
- Backfill-safe