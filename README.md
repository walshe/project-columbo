# 🕵️‍♂️ Project Columbo

<p align="center">
  <img src="https://upload.wikimedia.org/wikipedia/commons/4/4c/Columbo_Peter_Falk_1973.JPG" alt="Columbo" width="300" />
  <br />
  <em>"Just one more thing..."</em>
</p>

---

**Jump to:** [For Traders](#for-traders) · [For Developers](#for-developers)

---

<a name="for-traders"></a>

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

## SuperTrend Signal Brief

A broader signal scan across the universe — market pulse plus recent flips, ordered by recency:

```
GET /api/v1/summary
GET /api/v1/summary?timeframe=W1
```

The exact sections available (e.g. whether RSI confirmation is included) depend on which backend is serving the request — see [For Developers](#for-developers) below.

---

## Strategies

| Strategy | What it does |
|----------|-------------|
| **SuperTrend + RSI** (Java/Spring backend only) | Momentum confirmation — SuperTrend identifies the trend direction; RSI crossing above 60 or below 40 confirms the move has force behind it. |
| **Cross-timeframe Alignment** | Use the trend alignment report to find assets where the weekly and daily are in agreement. Use the retest list to time entries into pullbacks within the weekly trend. |

---
---

<a name="for-developers"></a>

# ⚙️ For Developers

This repository contains **three parallel, independent backend implementations** of the same market-intelligence engine (ingest Binance OHLCV → compute SuperTrend → detect signal flips → aggregate market breadth → expose a REST API). They share no code, schema, or database — each is a self-contained evaluation of a different stack. Pick whichever one you're actually working on and read its own README/notes; this page is just the map.

| Backend | Stack | Status | Docs |
|---|---|---|---|
| **`backend/java`** | Java 17+, Spring Boot 4, JPA/Hibernate, Flyway | Original implementation — full feature set (SuperTrend + RSI, multi-timeframe scan, EMA/MACD/Elder Impulse tables retained but disabled) | [`backend/java/README.md`](backend/java/README.md) |
| **`backend/java25-no-spring`** | Java 25, no framework, plain JDBC, Javalin | Parallel rewrite evaluating how much simpler the system gets without Spring — SuperTrend only (no RSI/other indicators) | [`backend/java25-no-spring/README.md`](backend/java25-no-spring/README.md) + [`backend/java25-no-spring/developer-notes.md`](backend/java25-no-spring/developer-notes.md) (architecture/conventions overview) |
| **`backend/supabase`** | Supabase (Postgres + Deno edge functions) | Parallel implementation evaluating a managed/serverless stack — SuperTrend, signal state, market breadth | [`backend/supabase/README.md`](backend/supabase/README.md) |

## Development Process

Changes are designed and tracked using [OpenSpec](https://github.com/open-gsd/openspec), a spec-driven delivery framework. Each feature goes through a proposal → design → spec → tasks pipeline before implementation. Each backend keeps its own `openspec/changes/` directory.

Earlier milestones (on `backend/java`) were developed with [gsd-core](https://github.com/open-gsd/gsd-core); those artefacts are in `.planning/`.

---

## 💡 Just One More Thing…

Like its namesake, Project Columbo always asks the extra question —
not just *what* the market is doing, but *why it flipped*, *how long ago*,
and *what confirms the move*.

---

## 🪪 License

MIT – see [`LICENSE`](LICENSE)
