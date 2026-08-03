# Architecture

Project Columbo is a deterministic signal-detection engine, not a trading bot: it ingests market data, computes indicators, and surfaces structured state — it never predicts prices or executes trades.

## Core model (shared across all three backends)

```
Market data provider (Binance)
        │  finalized OHLCV candles
        ▼
Candle ingestion  ──idempotent upsert──►  Candle store (Postgres)
        │
        ▼
Indicator computation (SuperTrend(10, 2), per-asset)
        │
        ▼
Signal state detection  ──►  flip events (bullish ↔ bearish)
        │
        ▼
Market breadth / pulse aggregation
        │
        ▼
REST API (JSON / Markdown / watchlist)
```

Same shape on both D1 (daily) and W1 (weekly) — W1 candles are rolled up from finalized D1 candles, then the same indicator → signal → pulse chain runs again on top.

**Invariants every implementation holds to:**
- Compute only on **finalized** candles — never a partial/in-progress one.
- Every signal event is anchored to the candle's **close time**, in UTC — never to wall-clock detection time.
- Candle/indicator upserts are **idempotent**, keyed on `(asset, timeframe, close_time)`; if a provider revises an already-stored candle, that's logged (not silently overwritten or duplicated).
- One pipeline run per `(provider, timeframe)` at a time — concurrent runs are rejected, not queued or merged.

## Three parallel implementations

This repo evaluates the same engine built three different ways. They share no code, schema, or database.

| | Stack | Scope |
|---|---|---|
| `backend/java` | Spring Boot, JPA/Hibernate | Original, full-featured (SuperTrend + RSI, multi-timeframe scan; EMA/MACD/Elder Impulse retained but disabled) |
| `backend/java25-no-spring` | Java 25, no framework, plain JDBC | SuperTrend only — evaluates how much simpler the system gets without Spring |
| `backend/supabase` | Postgres + Deno edge functions | SuperTrend only — evaluates a managed/serverless stack |

Each backend's own README (and `backend/java25-no-spring/developer-notes.md`) covers its concrete package layout, HTTP endpoints, and implementation-specific conventions — this document only covers what's conceptually true across all three. See the root [`README.md`](README.md) for the full backend index.

## History

This document used to describe a single planned system, including an AI summarization layer that was never built. That plan, and the earlier per-story planning workflow that preceded the current [OpenSpec](https://github.com/open-gsd/openspec) process, are preserved under [`archive/`](archive/) for reference — they no longer reflect what's actually implemented.
