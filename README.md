# 🕵️‍♂️ Project Columbo

<p align="center">
  <img src="https://upload.wikimedia.org/wikipedia/commons/4/4c/Columbo_Peter_Falk_1973.JPG" alt="Columbo" width="300" />
  <br />
  <em>"Just one more thing..."</em>
</p>

---

**Jump to:** [For Traders](#-for-traders) · [For Developers](#-for-developers)

---

---

# 📈 For Traders

Project Columbo runs a nightly pipeline after the daily market close and produces a structured brief you can act on the next morning. It does not predict — it filters. The reports tell you which assets currently have permission to trade in a given direction, and whether entry conditions are calm enough to act.

---

## The Elder Daily Brief

The primary output is the **Elder Impulse System Daily Brief** — a single report that covers:

| Section | What it tells you |
|---------|-------------------|
| **Market Breadth** | What percentage of the universe is in a weekly uptrend vs downtrend. Sets the macro bias before you look at any individual name. |
| **Primary Bear Shortlist** | Assets where the weekly EMA is falling, the daily EMA and MACD are both falling, and the market is calm. The actionable short setups for tonight. |
| **Primary Bull Shortlist** | Same logic, long side. Assets where weekly and daily are both rising and entry conditions are quiet. |
| **Fresh W1 Flips** | Assets whose weekly EMA just changed direction in the last 7 days. Your watchlist for the coming week — not yet actionable until the daily confirms. |
| **Spike Alerts** | Assets where today's volatility was more than 3× the recent average. Elder calls these *gifts from the crowd* — take profits into them, do not open new positions. |

The report is ordered by macro bias: on a bearish night the bear shortlist leads, on a bullish night the bull shortlist leads.

```
GET /api/v1/elder-summary
```

Returns a formatted Markdown brief ready to read. Everything in the sections above comes from this single call.

---

## The SuperTrend Brief

A momentum-focused brief based on SuperTrend trend direction and RSI confirmation:

| Section | What it tells you |
|---------|-------------------|
| **Market Pulse** | How many assets are currently in a SuperTrend bullish vs bearish state — the overall sentiment of the universe. |
| **Recent Bullish Flips** | Assets that recently flipped to SuperTrend bullish. Trend has just switched up — earliest entry window. |
| **Recent Bearish Flips** | Assets that recently flipped to SuperTrend bearish. Trend has just switched down. |
| **Bullish Trend + RSI Cross Above 60** | Assets in a SuperTrend uptrend where RSI has recently crossed above 60 — momentum confirming the trend. High-probability long candidates. |
| **Bearish Trend + RSI Cross Below 40** | Symmetric short side — SuperTrend down and RSI confirming downward momentum. |

```
GET /api/v1/summary
```

---

## Strategies

| Strategy | What it does | Full guide |
|----------|-------------|------------|
| **Elder Impulse System + Market Thermometer** | Trend-following with volatility-timed entries. Uses W1 and D1 Elder Impulse for direction, D1 Thermometer for entry timing. ~15 minutes each evening. | [📄 Read the guide](docs/strategies/elder-impulse-and-thermometer.md) |
| **SuperTrend + RSI** | Momentum confirmation strategy. SuperTrend identifies the trend direction; RSI crossing above 60 or below 40 confirms the move has force behind it. | Guide coming soon |

The strategy guides cover:
- How each indicator works and what it measures
- The full daily workflow step by step
- Exit rules (the hardest part)
- The prohibition rule — what you cannot do in each market state
- Data requirements and minimum backfill needed for reliable signals

---

## Sample Output

*From the 30 May 2026 close — acted on during the 1 June 2026 session:*

```
## Market Breadth
- W1 Impulse: 9 GREEN / 32 RED / 4 NEUTRAL  (22% GREEN / 71% RED)
- D1 Impulse: 9 GREEN /  7 RED / 29 NEUTRAL
- D1 Thermometer: 30 QUIET / 13 HOT/SPIKE
- Cross-timeframe alignment: 1 bull-aligned / 2 bear-aligned (6% with conviction)

> Macro environment is bearish — bias to the short side or stand aside.

## Primary Bear Shortlist — W1 RED + D1 RED + D1 QUIET
- ATOMUSDT  W1 RED 14 days  D1 RED 3 days ⚡ fresh
            temp=0 (inside bar)  → target: yesterday low − 0.0428
- DOTUSDT   W1 RED 14 days  D1 RED 3 days ⚡ fresh
            temp=0 (inside bar)  → target: yesterday low − 0.0188

## Spike Alerts
W1 RED — short-covering rally into a downtrend. Consider selling into this strength.
- BNBUSDT:  temp=84, ema=13.4 (6.3× normal)
```

A full walkthrough of this report with section-by-section interpretation is in the [strategy guide](docs/strategies/elder-impulse-and-thermometer.md#sample-report-walkthrough).

---

---

# ⚙️ For Developers

Project Columbo is a Spring Boot backend that ingests OHLCV data from Binance, computes a stack of technical indicators, and exposes a REST API for scanning, signal tracking, and breadth aggregation.

---

## Architecture

```
┌──────────────────────────────┐
│  Binance Spot API            │
└────────────┬─────────────────┘
             │ OHLCV (JSON Klines)
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
│  - SuperTrend (10, 2.0)      │
│  - RSI (14)                  │
│  - EMA-13 (D1), EMA-26 (W1)  │
│  - MACD 12-26-9              │
│  - Market Thermometer        │
│  - Elder Impulse (D1 + W1)   │
└────────────┬─────────────────┘
             │
┌────────────▼─────────────────┐
│  Signal State Table          │
│  - Current state per asset   │
│  - Flip event history        │
└────────────┬─────────────────┘
             │
┌────────────▼─────────────────┐
│  Market Pulse Aggregator     │
│  - Breadth snapshots         │
│  - Cross-timeframe alignment │
└────────────┬─────────────────┘
             │
┌────────────▼─────────────────┐
│  REST API + Summary Layer    │
│  - Scan engine               │
│  - Elder daily brief         │
│  - Swagger UI                │
└──────────────────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17+ |
| Framework | Spring Boot 4.x |
| Database | PostgreSQL 15 |
| ORM | JPA / Hibernate |
| Migrations | Flyway |
| Testing | JUnit 5 / Testcontainers |
| Market data | Binance Spot REST API |
| Deployment | Docker Compose |

---

## Running via Docker Compose

All source is under `backend/java/`. The compose file provisions both the PostgreSQL database and the application.

```bash
# Start
docker compose --profile prod up -d --build

# Stop
docker compose down

# Tail logs
docker compose logs -f app
```

---

## Initial Data Backfill

On a fresh database, candles are fetched from `app.ingestion.backfill-start` (configured in `application.yaml`, default `2025-01-01`).

The most demanding indicator is the **26-week EMA** used by W1 Elder Impulse. It requires ~86 weekly bars (~20 months of D1 data) to fully converge. The current default of `2025-01-01` (~74 W1 candles) will produce values, but signals should be treated as indicative until ~86 bars have accumulated.

**Recommended minimum backfill start: `2024-01-01`**

| Indicator | Converges after |
|-----------|----------------|
| D1 EMA-13, MACD, Thermometer | ~7 weeks of D1 data |
| W1 SuperTrend (10-period ATR) | ~44 W1 candles (~10 months) |
| **W1 Elder Impulse (26-week EMA)** | **~86 W1 candles (~20 months) ← binding constraint** |

Binance returns 500 candles per request, so a full backfill requires multiple ingestion trigger runs:

```bash
# Repeat until response shows 0 new candles inserted
curl -X POST http://localhost:8080/api/v1/internal/ingestion/run \
  -H 'Content-Type: application/json' \
  -d '{"provider": "BINANCE", "timeframe": "1D"}'
```

Check progress:

```bash
docker compose logs app | grep INGESTION_WINDOW
```

When all assets show `start >= end`, the backfill is complete.

> Full data requirements breakdown: [Elder Impulse Strategy Guide — Data Requirements](docs/strategies/elder-impulse-and-thermometer.md#️-data-requirements)

---

## API & Swagger

Once the stack is running:

👉 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**

Key endpoints:

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/elder-summary` | Full Elder daily brief (markdown) |
| `GET /api/v1/elder-impulse-market-pulse?timeframe=W1` | W1 breadth snapshot |
| `GET /api/v1/elder-impulse-market-pulse?timeframe=D1` | D1 breadth snapshot |
| `POST /api/v1/scan` | Composable multi-indicator scan |
| `GET /api/v1/signals` | Recent flip events by indicator and timeframe |
| `POST /api/v1/internal/ingestion/run` | Trigger a manual ingestion run |

---

## Database Schema

| Table | Purpose |
|-------|---------|
| **asset** | Tracked trading pairs (e.g., BTCUSDT) |
| **candle** | OHLCV data including quote volume |
| **indicator_ema** | EMA values (13-period D1, 26-period W1) |
| **indicator_macd** | MACD line, signal line, histogram (D1 12-26-9) |
| **indicator_thermometer** | Daily temperature and 22-day EMA |
| **indicator_supertrend** | SuperTrend values |
| **indicator_rsi** | RSI values |
| **signal_state** | Current state and flip events per indicator/asset |
| **market_pulse** | Breadth snapshots aggregated per indicator |
| **ingestion_run** | Full audit log of every ingestion execution |
| **v_asset_liquidity** | View of assets ranked by 7-day average quote volume |

---

## Daily Scheduler

A single pipeline (`MarketPipelineScheduler`) runs at **00:05 UTC** every day:

| Phase | What it does |
|-------|-------------|
| 1 — Ingestion | Fetch finalized D1 candles from Binance |
| 2 — D1 Indicators | Compute SuperTrend, RSI, EMA-13, MACD, Thermometer |
| 3 — D1 Signal Detection | Detect state flips for D1 SuperTrend and RSI |
| 4 — D1 Impulse | Derive Elder Impulse GREEN/RED/NEUTRAL for D1 |
| 5 — D1 Thermometer | Derive QUIET/HOT/SPIKE from thermometer values |
| 6 — D1 Market Pulse | Aggregate all D1 states into a breadth snapshot |
| 7 — W1 Rollup | Derive W1 candles from completed Mon–Sun weeks |
| 8 — W1 Processing | Compute W1 EMA-26 and Elder Impulse state |
| 9 — W1 Market Pulse | Aggregate W1 states into a breadth snapshot |

Configured via `app.market-pipeline.cron` in `application.yaml`.

---

## Design Principles

- Deterministic and idempotent data processing
- Separation of concerns: ingestion → computation → aggregation → API
- Extensible indicator framework — adding a new algorithm does not touch existing ones
- Full operational audit trail via `ingestion_run`

---

## Roadmap

- [x] SuperTrend and RSI indicators
- [x] Multi-timeframe scan with AND/OR logic (v2.0)
- [x] EMA, MACD, Elder Impulse, Market Thermometer (v3.0)
- [x] Elder daily brief with bear/bull shortlists and spike alerts
- [ ] Prometheus metrics export
- [ ] Historical re-backfill endpoint
- [ ] 4H timeframe support
- [ ] OpenClaw AI assistant integration

---

## 💡 Just One More Thing…

Like its namesake, Project Columbo always asks the extra question —
not just *what* the market is doing, but *why it flipped*, *how long ago*,
and *what confirms the move*.

---

## 🪪 License

MIT – see [`LICENSE`](LICENSE)
