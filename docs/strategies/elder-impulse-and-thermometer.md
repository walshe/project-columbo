# Elder Impulse System + Market Thermometer

**Strategy type:** Trend-following with volatility-timed entries  
**Timeframes:** W1 (weekly) + D1 (daily)  
**Typical time commitment:** ~15 minutes each evening after the daily close

---

## ⚠️ Data Requirements

> **This strategy will not produce reliable signals without sufficient historical data.**  
> The 26-week EMA that drives the W1 Impulse is the most demanding indicator in the stack — it requires more D1 history than anything else in Project Colombo.

### Why history depth matters

Every EMA starts from a seed value (the SMA of the first `period` candles). After that seed, subsequent values are weighted averages — but the initial seed's influence decays slowly and never fully disappears. Too little history means the W1 EMA is anchored to an arbitrary starting price rather than the actual long-term trend.

The decay rate for the 26-period EMA is `(1 − k)^n` where `k = 2/(26+1) ≈ 0.074`. For the seed to have less than 1% influence on the current value, you need approximately 60 weekly bars *after* the seed. Combined with the 26 bars needed to form the seed itself, the full requirement is:

```
26 weeks (seed) + 60 weeks (warmup) = 86 W1 candles ≈ 20 months of D1 data
```

### Minimum backfill requirements

| Indicator | Minimum D1 history | Reliable from |
|-----------|-------------------|---------------|
| D1 Elder Impulse (13-day EMA + MACD 12-26-9) | ~7 weeks (35 D1 candles) | Quickly converges |
| Market Thermometer (22-day temp EMA) | ~5 weeks (23 D1 candles) | Quickly converges |
| W1 SuperTrend (10-period ATR) | ~10 months (~44 W1 candles) | See README note |
| **W1 Elder Impulse (26-week EMA)** | **~6 months (26 W1 candles) to produce values** | **~20 months (86 W1 candles) for reliable values** |

**The 26-week EMA is the binding constraint.** Set your backfill start date accordingly.

### Recommended `backfill-start` settings

| Setting | W1 candles available (as of today) | Suitability |
|---------|-----------------------------------|-------------|
| `2025-01-01` *(current default)* | ~74 | ⚠️ Values appear but EMA is not fully converged |
| `2024-01-01` | ~126 | ✅ Fully converged — recommended minimum |
| `2023-01-01` | ~178 | ✅ Conservative, maximum confidence |

**If you are setting up a fresh instance, set `app.ingestion.backfill-start` to `2024-01-01` or earlier** in `application.yaml` before running the first ingestion. Running multiple ingestion trigger calls will be required to backfill the full window (Binance returns 500 candles per request).

W1 signals emitted before 86 W1 candles have accumulated should be treated as indicative only, not actionable.

---

## Overview

This strategy combines two systems developed by Dr. Alexander Elder:

**The Elder Impulse System** determines *whether you have permission to trade* in a given direction. It filters out the noise by requiring the weekly trend and daily momentum to agree before you act.

**The Market Thermometer** determines *when the timing is right to enter*. It measures daily volatility relative to its recent average — quiet markets have low slippage and predictable behaviour; hot or spiking markets carry higher risk and are better for taking profits than opening positions.

Together they answer the two questions that matter:

> *"Is the trend with me?"* → Impulse  
> *"Is now a good time to enter?"* → Thermometer

---

## The Indicators Explained

### Elder Impulse System — Permission States

The Impulse System assigns a colour to each bar based on two conditions on the **daily** chart:

| Condition | What it measures |
|-----------|-----------------|
| 13-day EMA slope | Trend inertia — is the asset still moving in the same direction? |
| MACD-Histogram slope | Momentum — is the force behind the move increasing or decreasing? |

| Both rising | Both falling | Diverging |
|-------------|-------------|-----------|
| 🟢 **GREEN** | 🔴 **RED** | ⬜ **NEUTRAL** |
| Permission to go long | Permission to go short | Stay out or manage existing position |

The **weekly** chart uses only the 26-week EMA slope to set the strategic direction — no MACD required.

### Market Thermometer — Entry Timing

Temperature is a daily measurement of how far today's bar extended outside yesterday's range:

```
Temperature = MAX(High_today − High_yesterday, Low_yesterday − Low_today, 0)
```

Always non-negative. Zero for inside bars (today's range entirely within yesterday's).

A 22-day EMA of the temperature series acts as the signal line:

| State | Condition | Meaning |
|-------|-----------|---------|
| 🟢 **QUIET** | Temperature ≤ EMA | Market is calm. Low slippage. Good entry timing. |
| 🟠 **HOT** | Temperature > EMA | Market is excited. Caution on new entries. |
| 🔴 **SPIKE** | Temperature > 3 × EMA | Panic or euphoria. Consider taking profits, not opening positions. |

---

## The Daily Workflow

The entire routine is done **after the daily candle closes**, before the next session opens. Everything is decided the evening before, and orders are placed for the next day.

### Step 1 — Read the market breadth (30 seconds)

Before looking at individual assets, check whether the macro environment is with you:

```
GET /api/v1/elder-impulse-market-pulse?timeframe=W1
GET /api/v1/elder-impulse-market-pulse?timeframe=D1
```

- **W1 mostly GREEN (>50%)** → macro trend favours longs. Work from the long side.
- **W1 mostly RED** → look for shorts, or stand aside entirely.
- **D1 mostly RED** → even in a bullish week, the daily environment is hostile for new longs. Wait.

If both pulses are hostile, close the laptop and come back tomorrow.

### Step 2 — Run the primary scan

```json
POST /api/v1/scan
{
  "operator": "AND",
  "conditions": [
    { "timeframe": "W1", "indicatorType": "ELDER_IMPULSE", "state": "IMPULSE_GREEN" },
    { "timeframe": "D1", "indicatorType": "ELDER_IMPULSE", "state": "IMPULSE_GREEN" },
    { "timeframe": "D1", "indicatorType": "MARKET_THERMOMETER", "state": "THERMOMETER_QUIET" }
  ]
}
```

**What each condition filters:**
- W1 GREEN → weekly 26-EMA is rising. The macro trend is up.
- D1 GREEN → today's bar had both 13-EMA rising AND MACD-histogram rising. Both engines on.
- D1 QUIET → today's temperature was below its 22-day EMA. The crowd is calm.

From a 200-asset universe this typically returns 10–20 assets on a trending day, and 0–5 on choppy days.

### Step 3 — Review each asset on the shortlist

For each returned asset, check two things:

**a) How fresh is the GREEN signal?**

The `daysSinceChange` field in the scan result tells you how many days ago the D1 Impulse last flipped to GREEN. A signal that appeared 1–2 days ago is more interesting than one that has been green for 3 weeks. Elder's research shows the strongest moves happen in the first few bars after a flip from NEUTRAL or RED.

**b) Is price pulling back or running away?**

The ideal setup is price that has **pulled back toward the 13-EMA** while the Impulse is still GREEN. This is the textbook Elder entry — the trend is intact (GREEN bar), but price has come back to value (EMA), and the crowd is calm (QUIET thermometer).

Price that has extended far above the EMA on a GREEN bar is chasing. It can still work, but the risk/reward is less favourable.

### Step 4 — Calculate your levels

For each trade you decide to take, set three levels **before** placing any order:

| Level | How to calculate |
|-------|-----------------|
| **Entry** | Buy stop just above today's high. You enter only if the market continues up. |
| **Stop loss** | Below the recent swing low, or 1–2 ATRs below the 13-EMA. |
| **Profit target** | `Yesterday's high + temperatureEma` (from the `ThermometerMatch` in the scan response) |

The thermometer EMA gives you a statistically grounded projection of how far the market can reasonably be expected to travel in one day given its current volatility. If the distance from entry to target is less than 2× your stop distance, skip the trade.

### Step 5 — Place orders, sleep, repeat

Set the buy stop and the protective stop. If triggered tomorrow, the position is open. If not triggered, the order expires and you re-evaluate tomorrow evening with fresh data.

---

## Exit Rules

Exits are the hardest part of this system psychologically, but they are the most important.

### The PRIMARY exit rule

**The moment the D1 Impulse turns away from your direction, exit at close of that day.**

- You are long → D1 goes NEUTRAL or RED → close the position that evening
- No waiting for it to "recover"
- No averaging down

This is not optional. The Impulse system is a **permission** system, not a buy-and-hold signal. Once permission is revoked, you exit.

### The SPIKE exit

Run this scan each evening for assets you currently hold:

```json
{ "timeframe": "D1", "indicatorType": "MARKET_THERMOMETER", "state": "THERMOMETER_SPIKE" }
```

A SPIKE while you are already in a profitable position is a **take-profit signal**. The crowd is panicking or euphoric — that is typically near a short-term extreme, not a continuation. Elder specifically calls spikes "gifts from the crowd" to exit into.

---

## The Prohibition Rule

The most powerful and most difficult rule in the system:

| W1 State | D1 State | What you can do |
|----------|----------|-----------------|
| GREEN | GREEN | ✅ Open new longs |
| GREEN | NEUTRAL | ⚠️ Manage existing longs. No new entries. |
| GREEN | RED | 🚫 No new longs. Exit only. |
| RED | RED | ✅ Open new shorts |
| RED | GREEN | 🚫 No new shorts. Exit only. |
| NEUTRAL | Any | ⚠️ No new positions. Manage existing. |

This rule eliminates an entire category of losing trades — buying into momentum that has already reversed, or shorting into a trend that is still rising. The urge to "buy the dip on a red bar" because it "looks cheap" is exactly what this rule prevents.

---

## Weekly Review (Sunday Evening)

Once per week, check for **fresh W1 flips**:

```json
{
  "timeframe": "W1",
  "indicatorType": "ELDER_IMPULSE",
  "state": "IMPULSE_GREEN",
  "maxDaysSinceFlip": 7
}
```

A W1 flip from NEUTRAL (or RED) to GREEN is a high-conviction setup. The weekly engine has just switched on. Look for D1 GREEN + D1 QUIET confirmation to enter with the full weekly tailwind behind you.

W1 flips to RED are also worth monitoring for assets you hold — a fresh weekly red is a warning to tighten stops significantly even if the D1 is still green.

---

## API Reference

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/elder-impulse-market-pulse?timeframe=W1` | W1 breadth: count of GREEN/RED/NEUTRAL across all assets |
| `GET /api/v1/elder-impulse-market-pulse?timeframe=D1` | D1 breadth: same |
| `POST /api/v1/scan` | Primary shortlist scan — combine any indicator conditions |
| `GET /api/v1/signals?indicatorType=ELDER_IMPULSE&timeframe=D1` | Recent D1 Impulse flip events |
| `GET /api/v1/signals?indicatorType=MARKET_THERMOMETER` | Recent thermometer state changes |

### ThermometerMatch response fields

When `MARKET_THERMOMETER` appears in scan results, the `matchedIndicators` array includes a `ThermometerMatch` object with:

```json
{
  "indicatorType": "MARKET_THERMOMETER",
  "timeframe": "D1",
  "state": "THERMOMETER_QUIET",
  "temperature": 1234.56,
  "temperatureEma": 2100.00,
  "closeTime": "2026-05-29T00:00:00Z"
}
```

Use `temperatureEma` directly in the profit target calculation:
```
Long target  = yesterday's high + temperatureEma
Short target = yesterday's low  − temperatureEma
```

---

## Quick Reference Card

```
EVERY EVENING (after daily close):

1. Breadth check     → /elder-impulse-market-pulse W1 + D1
                        Mostly RED? Stop here.

2. Primary scan      → W1 GREEN + D1 GREEN + D1 QUIET
                        Empty result? Nothing to do tonight.

3. Per-asset review  → daysSinceChange low? Price near 13-EMA?
                        No → skip. Yes → go to step 4.

4. Set levels        → Entry: buy stop above today's high
                        Stop:  below recent swing low / 13-EMA
                        Target: yesterday's high + thermometerEma

5. Check holds       → Any open position showing D1 RED? Exit.
                        Any open position showing SPIKE? Take profit.

EVERY SUNDAY:
        → W1 flips this week? (maxDaysSinceFlip: 7)
          Fresh W1 GREEN = highest-conviction setup next week.
          Fresh W1 RED   = tighten stops on existing longs.
```

---

*Based on the Elder Impulse System and Market Thermometer as described in Alexander Elder's trading research. Project Colombo automates the data pipeline; the trading decisions remain the trader's responsibility.*
