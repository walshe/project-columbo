# Story 001 — Implementation Plan
Fetch & Persist Daily OHLC Data

This plan translates the requirements into deterministic implementation steps.

Follow in order.

---

## Phase 1 — Schema Foundation (Flyway)

1. Create Flyway migration:

   File:
   V1__create_asset_timeframe_candle.sql

2. Migration must:

   - Create Postgres ENUM type:
       CREATE TYPE timeframe AS ENUM ('1D');

   - Create `asset` table:
       id (PK)
       provider_id (VARCHAR, NOT NULL)   # CoinGecko id usage
       symbol (VARCHAR, unique)
       name (VARCHAR)
       active (BOOLEAN)
       created_at (TIMESTAMPTZ DEFAULT now())

   - Create `candle` table:
       id (PK)
       asset_id (FK → asset.id)
       timeframe (timeframe ENUM)
       open_time (TIMESTAMPTZ)
       close_time (TIMESTAMPTZ)
       open (NUMERIC)
       high (NUMERIC)
       low (NUMERIC)
       close (NUMERIC)
       volume (NUMERIC)
       source (VARCHAR)
       raw_payload (JSONB NULL)
       created_at (TIMESTAMPTZ DEFAULT now())

   - Add unique constraint:
       (asset_id, timeframe, close_time)

3. Verify:

   - Start application
   - Confirm Flyway runs
   - Inspect schema manually

Do not proceed until schema is correct.

---

## Phase 2 — Provider Abstraction

1. Define interface:

   MarketDataProvider:
       List<CandleDto> fetchDailyCandles(String providerId);

2. Implement CoinGeckoMarketDataProvider.

3. The provider must:

   - Hit `/coins/{id}/ohlc?vs_currency=usd&days=365`
   - Use the demo API key if provided (optional)
   - Parse JSON of form:
       [
         [timestamp_ms, open, high, low, close],
         ...
       ]
   - Convert timestamp to UTC `close_time` (converted from ms)
   - Set `open_time = close_time - 24h`
   - Populate CandleDto with open, high, low, close
   - Normalize all timestamps to UTC
   - Do NOT persist anything here

4. Rate limiting consideration:
   - Since CoinGecko free plan allows 30 calls/minute, consider a mild delay between calls (e.g., Thread.sleep(250–500ms)) to avoid 429.

---

## Phase 3 — Ingestion Service

Create CandleIngestionService.

Method:
    ingestDaily()

Procedure:

1. Fetch all active assets.
2. For each asset:

   a. Fetch daily candles via provider
   b. Sort ascending by close_time
   c. Only include finalized candles

Finalized rule:

    A daily candle is finalized if:
    Candle.close_time < current UTC day boundary.

3. Map DTO → Candle entity.
4. Upsert deterministically.

Each asset loop should be within a transaction.

---

## Phase 4 — Idempotent Upsert Logic

Use native SQL:

    INSERT ... ON CONFLICT (asset_id, timeframe, close_time)

Rules:

- If identical (open, high, low, close, volume, source):
    → ignore

- If different:
    → log WARNING
    → update

Comparison must EXCLUDE:

- raw_payload
- created_at

Revision detection must be deterministic.

---

## Phase 5 — Scheduler

1. Add @Scheduled method.
2. Cron externalized to application.yaml.
3. Method calls ingestDaily().

Scheduler must:

- Catch exceptions
- Log failures
- Never crash application

---

## Phase 6 — Testing

Unit tests:

- Finalized filter logic
- Revision detection
- Provider parsing correctness

Integration test (Testcontainers Postgres):

- Seed 2 assets
- Run ingestion twice
- Assert no duplicates
- Simulate modified candle
- Assert WARNING

---

## Phase 7 — Manual Verification

1. Insert 2 assets via SQL:
   e.g., INSERT INTO asset(provider_id, symbol, name, active) VALUES ('bitcoin', 'BTC', 'Bitcoin', true);
2. Run ingestion
3. Inspect DB
4. Run ingestion again
5. Confirm:
   - No duplicates
   - No unexpected updates
   - Clean logs
