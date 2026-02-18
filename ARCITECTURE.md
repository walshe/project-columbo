1. Overview

Project Columbo is a local-first autonomous signal detection engine.

It consists of:

A deterministic Java-based signal engine

A PostgreSQL state store

An AI agent layer (OpenClaw) that produces structured intelligence summaries

Core philosophy:

Deterministic computation

Event-driven state transitions

Close-time anchored truth

AI used only for synthesis, never for math

2. System Components
2.1 Signal Engine (Spring Boot)

Responsibilities:

Fetch finalized daily OHLC candles

Normalize and persist candles

Fetch minimal lookback window per indicator (indicator-defined) to compute the latest finalized state safely

Compute SuperTrend(10,2)

Derive SignalState (BULLISH / BEARISH)

Detect state transitions (SignalEvents)

Maintain AssetState

Compute MarketPulse

Expose REST endpoints

2.2 State Store (PostgreSQL via Docker)

Stores:

Assets

Candles (normalized)

AssetState signal_state plus optional indicator_payload (JSONB) for debugging

SignalEvents

MarketPulse snapshots

Liquibase manages schema evolution.

2.3 AI Layer (OpenClaw)

Responsibilities:

Scheduled execution (daily)

Calls structured REST endpoints

Generates markdown intelligence brief

Never computes indicators

Never infers direction from price directly

The AI consumes structured data only.

3. Canonical Time Model

All signal events are anchored to:

Finalized candle close time in UTC

Rules:

Never compute on partial candles

Never anchor events to detection time

Detection time is metadata only

Close time uniquely identifies state transitions

4. Core Domain Model
4.1 Candle

Normalized OHLC record.

Contains optional raw_payload JSONB for debugging.

Unique:

(asset_id, timeframe, close_time)

4.2 SignalIndicator (Minimal Abstraction)
public interface SignalIndicator {
    int requiredLookback();
    IndicatorResult compute(List<Candle> candles);
}


Phase 1:

Only SuperTrend implementation

No plugin framework yet

Each indicator declares the minimum required lookback window.
The engine fetches sufficient historical candles to compute the latest finalized state safely.

4.3 SignalState
enum SignalState {
    BULLISH,
    BEARISH
}


Future-safe for NEUTRAL if needed.

4.4 SignalEvent

Represents state transition.

Fields:

asset_id

timeframe

close_time (canonical)

from_state

to_state

detected_at

Unique:

(asset_id, timeframe, close_time)

4.5 AssetState

Current known state per asset/timeframe.

Fields:

asset_id

timeframe

current_state

last_signal_event_close_time

last_seen_close_time

indicator_payload (optional JSONB; diagnostic only)

4.6 MarketPulse

Aggregate distribution snapshot.

Fields:

timeframe

snapshot_time

bullish_count

bearish_count

bullish_ratio

total_assets

5. Data Flow (Daily Cycle)

Scheduler triggers ingestion

Fetch finalized daily candles

Upsert candles (idempotent)

If provider revises an existing candle, log WARNING and update the row

Compute indicator

Derive SignalState

Compare with previous state

If changed → create SignalEvent

Update AssetState

Compute MarketPulse

OpenClaw generates daily brief

6. REST API
Health
GET /api/health

Asset States
GET /api/assets/state?timeframe=1D

Recent Signal Events
GET /api/events/recent?timeframe=1D&since=PT72H

Market Pulse
GET /api/market-pulse/latest?timeframe=1D
GET /api/market-pulse/history?timeframe=1D&days=30

7. Deployment

Docker Compose:

Postgres

Spring Boot app

OpenClaw runs locally.

Environment-configured:

Seed parameters (e.g., initial topN, stablecoin filter)

Provider configuration

LLM key

8. Guardrails

No trade execution

No price prediction

No partial candle computation

Deterministic unit tests for indicator logic

9. Scheduling Model

Spring Boot owns ingestion and signal computation schedule.

OpenClaw owns reporting schedule.

OpenClaw does not trigger ingestion.

Signal engine remains fully functional without AI layer.

10. Universe Management

Universe is stored in asset table.

Assets are activated once seeded.

Universe does not automatically change daily.

Assets may be manually activated/deactivated.

Historical data is never deleted.