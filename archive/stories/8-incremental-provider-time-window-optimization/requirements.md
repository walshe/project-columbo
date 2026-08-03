# Story 008 — Incremental Provider Time Window Optimization

## 1. Objective

Optimize candle ingestion by introducing precise time-window querying
using provider-supported `startTime` and `endTime` parameters.

The system must fetch only missing candles between:

    last_stored_close_time + 1ms
    and
    finalized_boundary (UTC)

This transforms ingestion from history-based pulling to strictly
incremental, delta-based fetching.

The goal is:

- Reduce unnecessary API data transfer
- Improve rate-limit safety
- Improve scalability across many assets
- Preserve strict determinism and idempotency

---

## 2. Scope

This story:

- Extends provider interface to support time window parameters
- Computes incremental start and end times per asset
- Uses Binance `startTime` and `endTime` for klines
- Skips API calls when no delta exists
- Preserves full backfill mode
- Maintains idempotent candle persistence

This story does NOT:

- Modify candle schema
- Modify indicator logic
- Modify pipeline orchestration
- Introduce retries or backoff
- Introduce pagination loops (unless required)
- Modify ingestion_run semantics

This is a controlled ingestion optimization.

---

## 3. Functional Requirements

---

## 3.1 Time Window Calculation

For each asset and timeframe:

1. Retrieve latest stored candle close_time
2. Compute:

   startTime = lastCloseTime + 1 millisecond
   endTime   = finalized UTC boundary

Where:

    finalized boundary = start of current UTC day for D1

If no candle exists yet:

    startTime = configured historical backfill start
                OR provider earliest available time

---

## 3.2 Skip Condition

If:

    startTime >= endTime

Then:

- Do NOT call provider
- Log: "No new candles required"
- Continue pipeline

This prevents unnecessary API calls.

---

## 3.3 Provider Interface Update

Extend provider interface:

Before:

    fetchDailyCandles(String symbol)

After:

    fetchDailyCandles(String symbol, long startTime, long endTime)

Where:

- startTime and endTime are epoch milliseconds
- Both parameters are optional for backfill mode

Provider must:

- Map to Binance `startTime` and `endTime`
- Preserve interval=1d

---

## 3.4 Binance API Usage

Endpoint:

    GET /api/v3/klines

Parameters:

- symbol
- interval=1d
- startTime
- endTime

The provider must:

- Use absolute millisecond timestamps
- Respect Binance maximum limit constraints
- Not request unnecessary historical data

---

## 3.5 Determinism Guarantee

The ingestion logic must remain:

- Deterministic
- Idempotent
- Fully recomputable

Re-running ingestion for the same window must:

- Not duplicate candles
- Not corrupt data
- Produce identical results

---

## 3.6 Backfill Mode Support

Support two execution modes:

### INCREMENTAL

Uses:

    startTime = lastStoredCloseTime + 1ms

### FULL

Ignores stored state and fetches:

    startTime = configured historical baseline

Backfill must still respect provider limits.

---

## 3.7 Boundary Consistency

The finalized boundary rule must remain:

    close_time < current UTC day boundary

No open daily candle may be ingested.

Time window logic must align with:

- Ingestion
- Indicator computation
- Signal detection
- Market pulse aggregation

All phases must agree on boundary.

---

## 3.8 No Behavior Regression

This change must not:

- Change candle values
- Change number of stored candles (for finalized periods)
- Change signal_state output
- Change market_pulse output

Only data fetching efficiency changes.

---

## 4. Data Model

No schema changes required.

Existing candle unique constraint remains:

    (asset_id, timeframe, close_time)

Upsert semantics remain unchanged.

---

## 5. Service Layer Adjustments

---

### 5.1 CandleIngestionService

Must:

- Compute startTime and endTime per asset
- Pass values to provider
- Handle empty response gracefully
- Log window boundaries clearly

---

### 5.2 Provider Implementation (Binance)

Must:

- Map startTime and endTime into request
- Ensure millisecond precision
- Validate non-null timestamps
- Handle empty response

---

## 6. Non-Functional Requirements

- Reduce API payload size
- Improve rate-limit headroom
- Avoid redundant historical fetching
- Scale linearly with delta size, not history size
- Maintain ingestion performance for large asset sets

---

## 7. Testing Requirements

---

### 7.1 Unit Tests

- startTime computed correctly from lastClose
- endTime equals finalized boundary
- Skip condition works
- Provider receives correct timestamps

---

### 7.2 Integration Tests (Testcontainers + Mock/WireMock)

Scenario 1:
- No stored candles
- Verify backfill window used

Scenario 2:
- Existing candles up to yesterday
- Verify only new delta requested

Scenario 3:
- startTime >= endTime
- Verify no provider call made

Scenario 4:
- Re-run ingestion
- Verify idempotency preserved

---

## 8. Operational Guarantees

After this story:

- Ingestion requests only missing data
- API quota usage minimized
- System scales cleanly with more assets
- No unnecessary data parsing
- Deterministic boundaries maintained

---

## 9. Future Enhancements (Out of Scope)

- Automatic pagination loop for large gaps
- Rate-limit adaptive batching
- Per-provider earliest timestamp discovery
- Retry/backoff strategy
- Historical backfill scheduling
- Multi-timeframe window logic
- Adaptive window slicing for hourly data

---

## 10. Success Criteria

This story is complete when:

- Provider calls use startTime and endTime
- Only missing candles are requested
- No behavioral regression occurs
- Ingestion remains deterministic
- Re-running ingestion produces no duplicates
- System logs show correct time windows