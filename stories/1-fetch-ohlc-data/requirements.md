# Story 001 — Fetch & Persist Daily OHLC Data

## Goal

Implement deterministic ingestion of finalized daily OHLC candles
for all active assets in the universe and persist them idempotently.

This establishes the data foundation for indicator computation.

---

## Scope

Phase 1:

- Daily timeframe only (1D)
- Active assets from `asset` table
- Single provider (CoinGecko initially)
- Idempotent upsert behavior
- No indicator computation yet

---

## Functional Requirements

### 1. Universe Selection

- Fetch all assets where `is_active = true`
- If no active assets exist → log INFO and exit gracefully

---

### 2. Provider Integration

- Fetch daily OHLC data per asset
- Only ingest finalized candles
- Ignore partial / current-day incomplete candles
- Normalize timestamps to UTC

Provider abstraction should allow future replacement.

---

### 3. Candle Normalization

Each candle must be persisted with:

- asset_id
- timeframe (ENUM)
- open_time (UTC)
- close_time (UTC)
- open
- high
- low
- close
- volume
- source
- raw_payload (optional JSONB)

---

### 4. Idempotency

Use PostgreSQL upsert semantics:

- Unique constraint on `(asset_id, timeframe, close_time)`
- If candle does not exist → insert
- If candle exists:
    - If identical → do nothing
    - If different → 
        - Log WARNING
        - Log old vs new values
        - Update row

---

### 5. Partial Candle Protection

- Never ingest the most recent candle if provider indicates it is still forming
- Daily candle is considered finalized only after UTC close

---

### 6. Error Handling

- If provider call fails for one asset:
    - Log ERROR
    - Continue processing other assets
- Do not abort entire job for single asset failure

---

### 7. Logging

Log at INFO level:

- Start of ingestion job
- Number of active assets
- Number of candles inserted
- Number of candles updated
- Duration of ingestion run

Log at WARNING level:

- Provider revisions (existing candle differs)

Log at ERROR level:

- Provider failure for asset

---

## Non-Functional Requirements

- Must be safe to run multiple times per day
- Must not create duplicate candles
- Must complete within reasonable time for 50 assets
- No indicator logic in this story

---

## Acceptance Criteria

- Candles are persisted correctly for active assets
- Duplicate ingestion does not create duplicate rows
- Provider revision triggers WARNING log
- Partial candle is not ingested
- Job completes successfully even if one asset fails
- Verified manually for at least 2 assets via DB inspection

---

## Out of Scope

- Indicator computation
- Signal detection
- MarketPulse computation
- AI integration
- Universe seeding logic
