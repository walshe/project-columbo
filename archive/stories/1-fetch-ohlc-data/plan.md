# Story 001 — Implementation Plan
Fetch & Persist Daily OHLC Data (Binance)

This plan translates requirements into deterministic implementation steps.

Follow in order.

---

## Phase 1 — Schema Foundation (Flyway)

1. Create Flyway migration:

   File:
   V1__create_asset_timeframe_candle.sql

2. Migration must:

   - Create ENUM type:
       CREATE TYPE timeframe AS ENUM ('1D');

   - Create ENUM type:
       CREATE TYPE provider AS ENUM ('BINANCE');

   - Create `asset` table:
       id (PK)
       symbol (VARCHAR, NOT NULL)               -- e.g. BTCUSDT
       name (VARCHAR)
       provider (provider ENUM NOT NULL)
       active (BOOLEAN DEFAULT true)
       created_at (TIMESTAMPTZ DEFAULT now())

   - Add unique constraint:
       (symbol, provider)

   - Create `candle` table:
       id (PK)
       asset_id (FK → asset.id, NOT NULL)
       timeframe (timeframe ENUM NOT NULL)
       open_time (TIMESTAMPTZ NOT NULL)
       close_time (TIMESTAMPTZ NOT NULL)
       open (NUMERIC NOT NULL)
       high (NUMERIC NOT NULL)
       low (NUMERIC NOT NULL)
       close (NUMERIC NOT NULL)
       volume (NUMERIC NOT NULL)
       source (provider ENUM NOT NULL)
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

## Phase 2 — Provider Abstraction (Binance)

1. Define interface:

   MarketDataProvider:
       List<CandleDto> fetchDailyCandles(String symbol);

2. Implement BinanceMarketDataProvider.

3. Symbol Normalization Rule:

   - Symbols must be uppercase.
   - If symbol does not end with "USDT",
     append "USDT".
   - Example:
       BTC → BTCUSDT
       eth → ETHUSDT
       BTCUSDT → BTCUSDT

   Normalization must occur before calling Binance.

4. Provider must:

   - Call:
       GET /api/v3/klines?symbol={symbol}&interval=1d&limit=365

   - Base URL:
       https://api.binance.com

   - Parse response format:
       [
         [
           openTime,
           open,
           high,
           low,
           close,
           volume,
           closeTime,
           ...
         ],
         ...
       ]

   - Map:
       openTime  → open_time (UTC)
       closeTime → close_time (UTC)
       open/high/low/close/volume → BigDecimal

   - Use BigDecimal for numeric precision.
   - Normalize timestamps to UTC.
   - Do NOT persist anything here.
   - Do NOT apply business logic here.

---

## Phase 3 — Ingestion Service

Create CandleIngestionService.

Method:
    ingestDaily()

Procedure:

1. Fetch all active assets.

2. For each asset:

   a. Fetch daily candles via provider.
   b. Sort ascending by close_time.
   c. Filter finalized candles.

Finalized Candle Logic (Precise Definition):

   Let:
       now = Instant.now()
       todayUtcStart = now truncated to UTC midnight.

   A candle is finalized if:
       candle.close_time < todayUtcStart

   This guarantees:
       - No partial current-day candle
       - Close-time anchored truth
       - Deterministic state

3. Map DTO → Candle entity.
4. Upsert deterministically.

Each asset ingestion must run within a transaction.

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

- Symbol normalization
- Finalized filter logic
- Revision detection logic
- Provider parsing correctness

Integration test (Testcontainers Postgres):

- Seed 2 assets
- Run ingestion twice
- Assert no duplicates
- Simulate modified candle
- Assert WARNING path

---

## Phase 7 — Manual Verification

1. Insert 2 assets:

   INSERT INTO asset(symbol, name, provider, active)
   VALUES ('BTC', 'Bitcoin', 'BINANCE', true);

2. Run ingestion
3. Inspect DB
4. Run ingestion again
5. Confirm:
   - No duplicates
   - No unexpected updates
   - Clean logs