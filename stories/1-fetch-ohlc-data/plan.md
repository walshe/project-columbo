# Story 001 — Implementation Plan
Fetch & Persist Daily OHLC Data

This plan translates the requirements into deterministic implementation steps.

Follow in order.

---

## Phase 1 — Schema Foundation

1. Create Liquibase changeset:
   - Create `asset` table (if not already present)
   - Create `timeframe` ENUM (e.g., 1D)
   - Create `candle` table:
       - asset_id (FK)
       - timeframe (ENUM)
       - open_time (TIMESTAMPTZ)
       - close_time (TIMESTAMPTZ)
       - open, high, low, close (NUMERIC)
       - volume (NUMERIC)
       - source (VARCHAR)
       - raw_payload (JSONB, nullable)
       - created_at
   - Add unique constraint:
       (asset_id, timeframe, close_time)

2. Verify schema via:
   - `docker compose up`
   - Inspect table definitions

Do not proceed until schema is correct.

---

## Phase 2 — Provider Abstraction

1. Define interface:

   MarketDataProvider:
   - List<CandleDto> fetchDailyCandles(String symbol)

2. Implement:
   - CoinGeckoMarketDataProvider

3. Responsibilities:
   - Fetch OHLC data
   - Map provider JSON to internal DTO
   - Normalize timestamps to UTC
   - Do not persist anything

No business logic here.

---

## Phase 3 — Ingestion Service

1. Create CandleIngestionService:

   Method:
   - ingestDaily()

2. Steps inside ingestDaily():

   a. Fetch all active assets from DB
   b. For each asset:
       - Fetch daily candles via provider
       - Filter finalized candles only
       - Map to Candle entities
       - Upsert into DB
           - On conflict:
               - If identical → ignore
               - If different → log WARNING and update

3. Track metrics:
   - inserted_count
   - updated_count
   - skipped_count

4. Log summary at end of job.

---

## Phase 4 — Idempotent Upsert Logic

Implement using:

- Native SQL `INSERT ... ON CONFLICT`
OR
- Custom repository method

Before updating:
- Compare existing vs incoming candle values
- Only log WARNING if values differ

Ensure logic is deterministic.

---

## Phase 5 — Scheduler

1. Add Spring `@Scheduled` method
2. Cron configurable via application.yml
3. Method calls `CandleIngestionService.ingestDaily()`

Scheduler must:
- Catch exceptions
- Never crash application

---

## Phase 6 — Testing

1. Unit test:
   - Finalized candle filtering
   - Revision detection logic

2. Integration test (Testcontainers Postgres):
   - Run ingestion twice
   - Assert no duplicate candles
   - Force modified candle → assert WARNING path

---

## Phase 7 — Manual Verification

1. Seed 2 active assets
2. Run ingestion
3. Inspect DB rows
4. Run ingestion again
5. Confirm:
   - No duplicates
   - Correct logging
   - Clean exit
