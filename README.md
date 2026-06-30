# 🕵️‍♂️ Project Columbo

<p align="center">
  <img src="https://upload.wikimedia.org/wikipedia/commons/4/4c/Columbo_Peter_Falk_1973.JPG" alt="Columbo" width="300" />
  <br />
  <em>"Just one more thing..."</em>
</p>

---

**Jump to:** [For Traders](#-for-traders) · [For Developers](#-for-developers)

---

# 📈 For Traders

Project Columbo runs a nightly pipeline after the daily market close and produces structured reports you can act on the next morning. It does not predict — it filters. The reports tell you which assets are aligned across timeframes and whether a retest setup has emerged.

---

## SuperTrend Trend Alignment

The primary output is the **Trend Alignment Report** — a cross-timeframe view of which assets are aligned bullish or bearish on both the weekly and daily SuperTrend, and which are in retest (the weekly trend is intact but the daily has briefly pulled counter-trend).

```
GET /api/v1/summary/trend-alignment
GET /api/v1/summary/trend-alignment?format=MARKDOWN
GET /api/v1/summary/trend-alignment?format=MARKDOWN&maxRetestAgeDays=5
```

| Section | What it tells you |
|---------|-------------------|
| **W1 + D1 Bullish Confluence** | Assets bullish on both weekly and daily SuperTrend — the cleanest long candidates. Ordered by most recent D1 alignment. |
| **W1 + D1 Bullish Retest** | W1 bullish but D1 recently flipped bearish (within `maxRetestAgeDays`, default 7). The weekly trend is intact — this is a potential pullback before continuation. |
| **W1 + D1 Bearish Confluence** | Assets bearish on both timeframes — short candidates. Ordered by most recent D1 alignment. |
| **W1 + D1 Bearish Retest** | W1 bearish but D1 recently flipped bullish — a potential dead-cat bounce before continuation to the downside. |

Use `maxRetestAgeDays` to tighten or widen the retest window. If an asset's D1 has been counter-trend for longer than the window, it drops off the retest list entirely.

---

## SuperTrend + RSI Brief

A broader signal scan across the universe:

| Section | What it tells you |
|---------|-------------------|
| **Market Pulse** | How many assets are currently in a SuperTrend bullish vs bearish state — overall sentiment. |
| **Recent Bullish Flips** | Assets that recently flipped to SuperTrend bullish. Earliest entry window. |
| **Recent Bearish Flips** | Assets that recently flipped to SuperTrend bearish. |
| **Bullish Trend + RSI Cross Above 60** | SuperTrend uptrend with RSI momentum confirming. High-probability long candidates. |
| **Bearish Trend + RSI Cross Below 40** | Symmetric short side — SuperTrend down and RSI confirming downward momentum. |

```
GET /api/v1/summary
GET /api/v1/summary?timeframe=W1
```

---

## Strategies

| Strategy | What it does |
|----------|-------------|
| **SuperTrend + RSI** | Momentum confirmation — SuperTrend identifies the trend direction; RSI crossing above 60 or below 40 confirms the move has force behind it. |
| **Cross-timeframe Alignment** | Use the trend alignment report to find assets where the weekly and daily are in agreement. Use the retest list to time entries into pullbacks within the weekly trend. |

---

---

# ⚙️ For Developers

Project Columbo is a Spring Boot backend that ingests OHLCV data from Binance, computes SuperTrend and RSI indicators, and exposes a REST API for scanning, signal tracking, and cross-timeframe alignment.

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
│  (parallelised per-asset)    │
│  - SuperTrend (10, 2.0)      │
│  - RSI (14)                  │
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
│  - Trend alignment report    │
│  - Scan engine               │
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

## Development Process

Changes are designed and tracked using [OpenSpec](https://github.com/open-gsd/openspec), a spec-driven delivery framework. Each feature goes through a proposal → design → spec → tasks pipeline before implementation. Artefacts live under `openspec/changes/` in the repository.

Earlier milestones were developed with [gsd-core](https://github.com/open-gsd/gsd-core); those artefacts are in `.planning/`.

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

The binding constraint is the **W1 SuperTrend ATR** — it needs approximately 100 weekly bars (~44 candles for the 10-period ATR to converge, plus a calculation warmup). The default backfill start of `2025-01-01` provides ~130 W1 bars, which is comfortable.

| Indicator | Converges after |
|-----------|----------------|
| D1 SuperTrend (10-period ATR) | ~100 D1 candles |
| RSI (14) | ~28 D1 candles |
| W1 SuperTrend (10-period ATR) | ~100 W1 candles (~2 years) ← binding constraint |

> If the Elder Impulse System is ever reinstated, move `backfill-start` back to `2023-01-01` — W1 EMA-26 needs ~86 weekly bars (~20 months) to converge.

Binance returns up to 500 candles per request. A backfill from `2025-01-01` to present requires multiple ingestion trigger runs:

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

---

## API & Swagger

Once the stack is running:

👉 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**

Key endpoints:

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/summary/trend-alignment` | Cross-timeframe SuperTrend confluence + retest report |
| `GET /api/v1/summary` | SuperTrend + RSI signal brief |
| `POST /api/v1/scan` | Composable multi-indicator scan |
| `GET /api/v1/signals` | Recent flip events by indicator and timeframe |
| `POST /api/v1/internal/ingestion/run` | Trigger a manual ingestion run |

All endpoints accept `format=MARKDOWN` for a human-readable brief where supported.

---

## Database Schema

| Table | Purpose |
|-------|---------|
| **asset** | Tracked trading pairs (e.g., BTCUSDT) |
| **candle** | OHLCV data including quote volume |
| **indicator_supertrend** | SuperTrend values per asset/timeframe |
| **indicator_rsi** | RSI values per asset |
| **signal_state** | Current state and flip events per indicator/asset |
| **market_pulse** | Breadth snapshots aggregated per indicator |
| **ingestion_run** | Full audit log of every ingestion execution |
| **v_asset_liquidity** | View of assets ranked by 7-day average quote volume |

> The schema also contains EMA, MACD, and Thermometer tables from an earlier Elder Impulse milestone. These are retained for schema continuity but are not written to by the current pipeline.

---

## Daily Scheduler

A single pipeline (`MarketPipelineService`) runs at **00:05 UTC** every day. Indicator computation is parallelised per-asset using a dedicated async thread pool.

| Phase | What it does |
|-------|-------------|
| 1 — Ingestion | Fetch finalized D1 candles from Binance |
| 2 — Indicator Computation | Compute SuperTrend and RSI per-asset in parallel |
| 3 — Signal Detection | Detect state flips for D1 SuperTrend and RSI |
| 4 — Market Pulse | Aggregate D1 states into a breadth snapshot |
| 5 — W1 Rollup | Derive W1 candles from completed Mon–Sun weeks |
| 6 — W1 Processing | Compute W1 SuperTrend and signal state |

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
- [x] EMA, MACD, Elder Impulse, Market Thermometer (implemented, currently disabled)
- [x] Parallel indicator computation per-asset
- [x] Cross-timeframe SuperTrend confluence + retest report (`/trend-alignment`)
- [ ] Prometheus metrics export
- [ ] Historical re-backfill endpoint
- [ ] 4H timeframe support

---

## 💡 Just One More Thing…

Like its namesake, Project Columbo always asks the extra question —
not just *what* the market is doing, but *why it flipped*, *how long ago*,
and *what confirms the move*.

---

## 🪪 License

MIT – see [`LICENSE`](LICENSE)
