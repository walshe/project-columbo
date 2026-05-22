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

| Table                    | Purpose                                      |
| ------------------------ | -------------------------------------------- |
| **asset**                | Tracked trading pairs (e.g., BTC/USDT).      |
| **candle**               | OHLCV data including quote volume.           |
| **indicator_supertrend** | Stores calculated SuperTrend values.         |
| **indicator_rsi**        | Stores calculated RSI values.                |
| **signal_state**         | Tracks state and events per indicator/asset. |
| **market_pulse**         | Aggregates indicator outcomes.               |
| **ingestion_run**        | Logs all ingestion executions with metrics.  |
| **v_asset_liquidity**    | SQL view of assets ranked by liquidity.      |

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

**Why the lookback matters:** Weekly SuperTrend uses a 10-period ATR with Wilder's smoothing (RMA). The initial seed value's influence decays as `0.9^n` per week — you need ~44 W1 candles for it to drop below 1%. With fewer candles the bands are too tight and false signals appear.

**Minimum:** ~15 months of D1 data (~65 W1 candles). `2025-01-01` comfortably covers this.

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

Two internal cron jobs handle the daily market lifecycle:

| Job                     | Schedule  | Purpose                                    |
| ----------------------- | --------- | ------------------------------------------ |
| `ingestDaily()`         | 02:00 UTC | Fetch finalized daily candles from Binance |
| `computeDailySignals()` | 02:05 UTC | Compute indicators (SuperTrend, RSI)       |

---

## 🔮 Roadmap

* [ ] Add MACD and EMA indicators
* [ ] Extend `/scan` to support OR logic
* [ ] Introduce Prometheus metrics export
* [ ] Implement historical re-backfill
* [ ] Integrate with OpenClaw AI assistant
* [ ] Multi-timeframe signal confirmations
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