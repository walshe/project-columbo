# 1. Problem Statement

Digital asset traders and analysts must manually scan charts to detect indicator state changes across multiple assets and timeframes.

This is:
- Time-consuming
- Inconsistent
- Error-prone
- Difficult to monitor at scale

There is no lightweight, local-first system that:
- Detects indicator state changes deterministically
- Tracks state over time
- Surfaces structured signal events
- Produces AI-assisted summaries

# 2. Objective

Build a local-first autonomous signal detection platform that:
- Ingests OHLC market data
- Computes deterministic technical indicators
- Detects and records signal events (e.g. SuperTrend flips)
- Tracks asset state over time
- Computes aggregate market pulse metrics
- Exposes structured APIs
- Uses an AI agent layer to generate daily intelligence summaries

# 3. Core Design Principles

**Deterministic core logic**
- The deterministic signal engine operates independently of the AI layer. The AI layer is a consumer, not a controller.
- All indicator calculations are reproducible and testable.

**Separation of concerns**
- Signal computation (Java)
- Intelligence summarization (LLM via OpenClaw)

**Structured over free-form**
- LLM consumes structured JSON only.

**Local-first**
- Runs on developer workstation
- No cloud infrastructure required

**Portable**
- Dockerized Postgres
- Easily movable to another machine

# 4. Phase 1 Scope

**Included**
- Daily timeframe only
- Configurable asset universe (e.g., top 20–50 tokens)
- SuperTrend(10,2) indicator
- Signal event detection (bullish ↔ bearish flips)
- Asset state tracking
- Market pulse calculation (distribution of states)
- REST API endpoints
- OpenClaw-generated daily summary (Markdown output)

**Excluded**
- Automated trade execution
- Intraday timeframes
- ML price prediction
- Full UI dashboard
- Portfolio management

# 5. Key Functional Requirements

## 5.1 Data Ingestion
- Fetch daily OHLC from provider (initially CoinGecko)
- Persist candles idempotently
- Normalize all timestamps to UTC

## 5.2 Indicator Engine
- Compute SuperTrend(10,2)
- Store indicator outputs
- Validate correctness against TradingView

## 5.3 Signal Event Detection
- Detect change in direction between consecutive candles
- Persist signal event with canonical candle close time
- Maintain current asset state

## 5.4 Market Pulse
- Count bullish vs bearish assets
- Compute bullish ratio
- Persist daily snapshot
- Provide historical pulse data

## 5.5 AI Summary Layer
- OpenClaw agent calls structured endpoints
- Generates daily intelligence brief
- **Highlights:**
  - Recent signal events
  - Shift in market pulse
  - Notable clustering of events

# 6. Non-Functional Requirements

- Runs on 2017 Mac with Docker
- No GPU required
- Deterministic test coverage for indicator engine
- Idempotent ingestion
- **Clear logging for:**
  - Provider failures
  - Number of events detected
  - Pulse metrics

# 7. Success Criteria

- System detects signal events accurately vs TradingView
- Produces automated daily summary
- Runs reliably for 30+ consecutive days
- Architecture clean enough to demo in interview

# 8. Future Extensions

- Weekly and monthly timeframes
- Multiple indicators
- Signal strength scoring
- Sector-based pulse (AI coins, L1s, etc.)
- RAG over historical signal events
- Minimal web UI
- Local LLM fallback