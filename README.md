# 🕵️‍♂️ Project Columbo

<p align="center">
  <img src="https://upload.wikimedia.org/wikipedia/commons/4/4c/Columbo_Peter_Falk_1973.JPG" alt="Columbo" width="300" />
  <br />
  <em>"Just one more thing..."</em>
</p>

**AI-Ready Market Intelligence Backend**
*Built with Spring Boot, PostgreSQL, Supabase, and Binance Market Data*

---

## 📘 Overview

Project Columbo is a modular backend for **market trend detection, aggregation, and orchestration**.
It ingests OHLCV data from Binance, computes technical indicator signals (SuperTrend, RSI, etc.),
and exposes a flexible API for market scanning, signal tracking, and pulse aggregation.

It’s designed to power AI-driven systems like *OpenClaw* — a next-generation market assistant that connects context, insight, and automation.

👉 **[Sample API Queries & Responses](docs/sample-api-responses.md)**

---

## 📈 Trading Strategies

Project Columbo is built around specific, documented trading strategies. Each strategy guide explains the underlying system, the daily workflow, and the exact API calls to use.

| Strategy | Indicators | Guide |
|----------|-----------|-------|
| **Elder Impulse System + Market Thermometer** | W1 & D1 Elder Impulse (permission), D1 Market Thermometer (entry timing) | [📄 Read the guide](docs/strategies/elder-impulse-and-thermometer.md) |

*SuperTrend and RSI strategy guides coming soon.*

---

## 🧩 Core Concepts

| Concept                   | Description                                                                                                   |
| ------------------------- | ------------------------------------------------------------------------------------------------------------- |
| **Ingestion Run**         | Tracks every import of market candles (1D, 4H, etc.) from Binance with audit data and concurrency protection. |
| **Indicator Computation** | Runs algorithms like [SuperTrend (10, 2.0)](https://www.tradingview.com/support/solutions/43000634738-supertrend/) and RSI (14) across assets and timeframes. |
| **Signal State**          | Represents the current indicator condition (e.g., BULLISH / BEARISH / NEUTRAL) and last flip event.           |
| **Market Pulse**          | Aggregates multiple indicators to produce a unified sentiment view.                                           |
| **Liquidity Rank**        | Derived from quote volume — helps surface the most actively traded assets.                                    |
| **Scan Logic**            | Enables composable, multi-indicator searches such as “SuperTrend bullish + RSI crossed above 60”.             |

---

## ⚙️ Architecture

```
┌──────────────────────────────┐
│  Binance Spot API            │
└────────────┬─────────────────┘
             │ (JSON Klines)
┌────────────▼─────────────────┐
│  IngestionOrchestrator       │
│  - Tracks runs               │
│  - Prevents overlaps         │
│  - Supports manual triggers  │
└────────────┬─────────────────┘
             │
┌────────────▼─────────────────┐
│  Candle Repository (DB)      │
│  - OHLCV + quote volume      │
└────────────┬─────────────────┘
             │
┌────────────▼─────────────────┐
│  Indicator Engines           │
│  - SuperTrend                │
│  - RSI                       │
│  - EMA (13-period D1,        │
│         26-period W1)        │
│  - MACD 12-26-9              │
│  - Market Thermometer        │
└────────────┬─────────────────┘
             │
┌────────────▼─────────────────┐
│  Signal State Table          │
│  - Current state per asset   │
│  - Event history             │
└────────────┬─────────────────┘
             │
┌────────────▼─────────────────┐
│  Market Pulse Aggregator     │
│  - Multi-indicator consensus │
│  - Reversal detection        │
└────────────┬─────────────────┘
             │
┌────────────▼─────────────────┐
│  REST API Layer              │
│  (Swagger UI available)      │
└──────────────────────────────┘
```

---

## 🗂️ Database Schema Highlights

| Table                       | Purpose                                                        |
| --------------------------- | -------------------------------------------------------------- |
| **asset**                   | Tracked trading pairs (e.g., BTC/USDT).                       |
| **candle**                  | OHLCV data including quote volume.                             |
| **indicator_supertrend**    | Stores calculated SuperTrend values.                           |
| **indicator_rsi**           | Stores calculated RSI values.                                  |
| **indicator_ema**           | Stores EMA values (13-period D1, 26-period W1).                |
| **indicator_macd**          | Stores MACD line, signal line, and histogram (D1 12-26-9).     |
| **indicator_thermometer**   | Stores daily temperature and its 22-day EMA.                   |
| **signal_state**            | Tracks current state and flip events per indicator/asset.      |
| **market_pulse**            | Aggregates indicator outcomes into breadth snapshots.          |
| **ingestion_run**           | Logs all ingestion executions with metrics.                    |
| **v_asset_liquidity**       | SQL view of assets ranked by 7-day average quote volume.       |

---

## 🚀 Running via Docker Compose

Project Columbo includes a ready-to-use **Docker Compose** setup (`compose.yaml`)
that provisions both the **PostgreSQL** database and the **backend application**.

### ▶️ Start the Stack

```bash
cd backend/java/project-columbo
docker compose --profile prod up -d --build
```

### 🧩 Stop the Stack

```bash
docker compose down
```

### 🧾 Logs

```bash
docker compose logs -f app
```

---

## 📦 Initial Data Backfill

On a fresh database, the system fetches historical D1 candles starting from `app.ingestion.backfill-start` (configured in `application.yaml`, currently `2025-01-01`).

**Why the lookback matters:** All EMA-based indicators start from a seed (the SMA of the first `period` candles). The seed's influence on current values decays slowly — too little history means indicators are anchored to an arbitrary starting price rather than the actual trend.

The most demanding indicator is the **26-week EMA** used by the W1 Elder Impulse System. Its decay factor is `(1 − 2/27)^n ≈ 0.926^n` per weekly bar. For seed influence below 1% you need **~60 bars after the seed** — combined with the 26-bar seed period that means **86 W1 candles (~20 months of D1 data)** for fully reliable W1 Impulse signals.

| Indicator | Reliable from |
|-----------|---------------|
| D1 EMA-13, MACD, Thermometer EMA | ~7 weeks of D1 data |
| W1 SuperTrend (10-period ATR/RMA) | ~44 W1 candles (~10 months) |
| **W1 Elder Impulse (26-week EMA)** | **~86 W1 candles (~20 months) ← binding constraint** |

**Recommended minimum:** `2024-01-01` — gives ~126 W1 candles as of 2026, fully past the convergence threshold for all indicators.

The current default `2025-01-01` (~74 W1 candles) will produce W1 Impulse values, but the 26-week EMA is not yet fully converged. Treat W1 Impulse signals from a `2025-01-01` backfill as indicative until ~86 weekly bars have accumulated.

> 💡 See [Elder Impulse + Thermometer Strategy Guide](docs/strategies/elder-impulse-and-thermometer.md#️-data-requirements) for a full breakdown.

**Binance returns 500 candles per request**, so a full backfill from `2025-01-01` requires **2 ingestion trigger runs**:

```bash
# Trigger via Swagger or curl — repeat until response shows 0 new candles inserted
curl -X POST http://localhost:8080/api/v1/internal/ingestion/run \
  -H 'Content-Type: application/json' \
  -d '{"provider": "BINANCE", "timeframe": "1D"}'
```

Check the logs to confirm backfill is complete:

```bash
docker compose -f backend/java/compose.yaml logs app | grep INGESTION_WINDOW
```

When all assets show `start >= end` (no new candles to fetch), the backfill is done.

---

## 🌐 API & Documentation

Once the containers are up and running, all API documentation and testing tools are available via Swagger:

👉 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**

Here you can:

* Explore all available endpoints
* Test requests live
* Inspect request/response schemas
* Monitor service behavior interactively

Swagger is the **canonical interface** for developers integrating with Project Columbo.

---

## 🧭 Design Principles

* Deterministic and idempotent data processing
* Separation of concerns (ingestion → computation → aggregation)
* Extensible indicator framework (plug in new algorithms easily)
* Auditable operational history (`ingestion_run`)
* Pure functional aggregation for consistent recomputation

---

## 🧰 Tech Stack

| Layer      | Technology                       |
| ---------- | -------------------------------- |
| Language   | Java 17+                         |
| Framework  | Spring Boot 4.x                  |
| Database   | PostgreSQL 15                    |
| ORM        | JPA / Hibernate                  |
| Migrations | Flyway                           |
| Testing    | JUnit 5 / Spock / Testcontainers |
| Provider   | Binance Spot REST API            |
| Deployment | Docker Compose                   |

---

## 🕰️ Daily Scheduler

A single pipeline (`MarketPipelineScheduler`) runs at **00:05 UTC** every day, executing 9 sequential phases:

| Phase | Name                | What it does                                                          |
| ----- | ------------------- | --------------------------------------------------------------------- |
| 1     | Ingestion           | Fetch finalized D1 candles from Binance                               |
| 2     | D1 Indicators       | Compute SuperTrend, RSI, EMA-13, MACD 12-26-9, and Thermometer for D1 |
| 3     | D1 Signal Detection | Detect state flips for D1 SuperTrend and RSI                         |
| 4     | D1 Impulse          | Derive Elder Impulse GREEN/RED/NEUTRAL state for D1                  |
| 5     | D1 Thermometer      | Derive QUIET/HOT/SPIKE state from thermometer values                 |
| 6     | D1 Market Pulse     | Aggregate all D1 indicator states into a breadth snapshot            |
| 7     | W1 Rollup           | Derive W1 candles from completed Mon–Sun D1 weeks                    |
| 8     | W1 Processing       | Compute W1 SuperTrend, RSI, EMA-26, and Elder Impulse state          |
| 9     | W1 Market Pulse     | Aggregate all W1 indicator states into a breadth snapshot            |

The schedule is configured via `app.market-pipeline.cron` in `application.yaml`.

---

## 🔮 Roadmap

* [x] Add EMA and MACD indicators (v3.0)
* [x] Elder Impulse System — GREEN/RED/NEUTRAL permission states (v3.0)
* [x] Market Thermometer — QUIET/HOT/SPIKE entry timing (v3.0)
* [x] Multi-timeframe scan with AND/OR logic (v2.0)
* [ ] Introduce Prometheus metrics export
* [ ] Implement historical re-backfill
* [ ] Integrate with OpenClaw AI assistant
* [ ] 4H timeframe support

---

## 💡 Just One More Thing…

Like its namesake, Project Columbo always asks the extra question —
not just *what* the market is doing, but *why it flipped*, *how long ago*,
and *what confirms the move*.

That’s what makes it more than a data engine — it’s a market detective.

---

## 🪪 License

MIT – see [`LICENSE`](LICENSE)

---