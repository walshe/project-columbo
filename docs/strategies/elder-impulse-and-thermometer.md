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

> **Shortcut:** The elder summary endpoint runs all of the steps below in a single call and returns a pre-formatted markdown brief:
> ```
> GET /api/v1/elder-summary
> ```
> The individual API calls documented in Steps 1–3 are shown for reference — they are what the summary aggregates under the hood. In normal use, the summary is all you need.

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
    { "timeframe": "W1", "indicatorType": "ELDER_IMPULSE", "state": "ELDER_IMPULSE_GREEN" },
    { "timeframe": "D1", "indicatorType": "ELDER_IMPULSE", "state": "ELDER_IMPULSE_GREEN" },
    { "timeframe": "D1", "indicatorType": "ELDER_THERMOMETER", "state": "ELDER_THERMOMETER_QUIET" }
  ]
}
```

For the bear side, flip all three states:

```json
POST /api/v1/scan
{
  "operator": "AND",
  "conditions": [
    { "timeframe": "W1", "indicatorType": "ELDER_IMPULSE", "state": "ELDER_IMPULSE_RED" },
    { "timeframe": "D1", "indicatorType": "ELDER_IMPULSE", "state": "ELDER_IMPULSE_RED" },
    { "timeframe": "D1", "indicatorType": "ELDER_THERMOMETER", "state": "ELDER_THERMOMETER_QUIET" }
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
{ "timeframe": "D1", "indicatorType": "ELDER_THERMOMETER", "state": "ELDER_THERMOMETER_SPIKE" }
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
POST /api/v1/scan
{
  "operator": "AND",
  "conditions": [
    { "timeframe": "W1", "indicatorType": "ELDER_IMPULSE", "state": "ELDER_IMPULSE_GREEN", "maxDaysSinceFlip": 7 }
  ]
}
```

A W1 flip from NEUTRAL (or RED) to GREEN is a high-conviction setup. The weekly engine has just switched on. Look for D1 GREEN + D1 QUIET confirmation to enter with the full weekly tailwind behind you.

W1 flips to RED are equally important — tighten stops aggressively on any longs in those names, and watch for D1 RED + QUIET alignment to build a short:

```json
POST /api/v1/scan
{
  "operator": "AND",
  "conditions": [
    { "timeframe": "W1", "indicatorType": "ELDER_IMPULSE", "state": "ELDER_IMPULSE_RED", "maxDaysSinceFlip": 7 }
  ]
}
```

The summary endpoint surfaces both flip lists automatically — the manual scan is shown here for reference.

---

## API Reference

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/elder-summary` | **Complete daily brief** — breadth, shortlists, flips, spike alerts in one call |
| `GET /api/v1/elder-impulse-market-pulse?timeframe=W1` | W1 breadth: count of GREEN/RED/NEUTRAL across all assets |
| `GET /api/v1/elder-impulse-market-pulse?timeframe=D1` | D1 breadth: same |
| `POST /api/v1/scan` | Custom scan — combine any indicator conditions |
| `GET /api/v1/signals?indicatorType=ELDER_IMPULSE&timeframe=D1` | Recent D1 Impulse flip events |
| `GET /api/v1/signals?indicatorType=ELDER_THERMOMETER` | Recent thermometer state changes |

### ThermometerMatch response fields

When `ELDER_THERMOMETER` appears in scan results, the `matchedIndicators` array includes a `ThermometerMatch` object with:

```json
{
  "indicatorType": "ELDER_THERMOMETER",
  "timeframe": "D1",
  "state": "ELDER_THERMOMETER_QUIET",
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

## Sample Report Walkthrough

*Report generated after the 30 May 2026 (Friday) daily close. To be acted on during the 1 June 2026 (Monday) session.*

```
# Elder Impulse System — Daily Brief

Data through 30 May 2026 — pipeline ran 31 May 2026 23:05 UTC

## Market Breadth
- W1 Impulse (26-week EMA):     9 GREEN / 32 RED / 4 NEUTRAL  (22% GREEN / 71% RED)
- D1 Impulse (13-EMA + MACD-H): 9 GREEN /  7 RED / 29 NEUTRAL  (56% GREEN / 16% RED)
- D1 Thermometer (22-day EMA): 30 QUIET / 13 HOT/SPIKE / 2 no data  (70% QUIET / 29% HOT/SPIKE)
- Cross-timeframe alignment: 1 bull-aligned (W1+D1 GREEN) / 2 bear-aligned (W1+D1 RED)
  (6% of universe trending with conviction in either direction)

> Macro environment is bearish — bias to the short side or stand aside.
  Entry conditions are calm across most of the universe tonight.

## Primary Bear Shortlist — W1 RED + D1 RED + D1 QUIET
- ATOMUSDT  W1 RED for 14 day(s)  D1 RED for 3 day(s) ⚡ fresh
            temp=0 (inside bar — indecision, not low volatility. Valid entry but size conservatively.)
            → target: yesterday low - 0.0428 (EMA = expected next-bar range)  (Vol: 3.8M)
- DOTUSDT   W1 RED for 14 day(s)  D1 RED for 3 day(s) ⚡ fresh
            temp=0 (inside bar)
            → target: yesterday low - 0.0188  (Vol: 8.3M)

## Primary Bull Shortlist — W1 GREEN + D1 GREEN + D1 QUIET
- RENDERUSDT  W1 GREEN for 14 day(s)  D1 GREEN for 2 day(s) ⚡ fresh
              temp=0.055 ema=0.0692 (today quieter than average — confirms calm entry)
              → target: yesterday high + 0.0692  (Vol: 21.2M)

⚠️ All shortlist entries are fresh signals — market just beginning to move, not mid-trend.
   Size conservatively until signals age past day 3.

## Fresh W1 Green Flips (last 7 days)
No fresh W1 green flips this week.

## Fresh W1 Red Flips (last 7 days)
No fresh W1 red flips this week.

## Spike Alerts — Take Profit

W1 RED — short-covering rally into a downtrend. Consider selling into this strength.
- ALGOUSDT:  temp=0.0164, ema=0.00437 (3.8× normal)  (Vol:  6.3M)
- ASTERUSDT: temp=0.096,  ema=0.0202  (4.8× normal)  (Vol: 12.6M)
- BNBUSDT:   temp=84,     ema=13.4    (6.3× normal)  (Vol: 130.6M)
- HBARUSDT:  temp=0.0101, ema=0.00267 (3.8× normal)  (Vol: 33.0M)
- WLDUSDT:   temp=0.0663, ema=0.0212  (3.1× normal)  (Vol: 57.3M)
```

---

### Report cadence — what to do daily vs weekly

The brief is generated **every evening after the daily close**. The report contains both daily and weekly information. Know which sections demand action every night and which are a Sunday-only concern:

| Section | Cadence | What to do |
|---------|---------|------------|
| **Market Breadth** | Every evening | Read before anything else. Hostile breadth = stop here. |
| **Primary Bear Shortlist** | Every evening | Short candidates for tomorrow if macro is bearish. |
| **Primary Bull Shortlist** | Every evening | Long candidates for tomorrow if macro is bullish. |
| **Spike Alerts** | Every evening | Check against any open positions. Take profit if holding. |
| **Fresh W1 Green Flips** | Sunday only | Bull watchlist. Note names; wait for D1 confirmation during the week. |
| **Fresh W1 Red Flips** | Sunday only | Bear watchlist + stop-tightening alert for existing longs. |

The report surfaces the primary opportunity first: on a bearish macro night (W1 >50% RED), the bear shortlist leads. On a bullish night, the bull shortlist leads. This means the most relevant section is always at the top.

On Sunday the W1 weekly candle has just closed, so the flip lists are at their most actionable — this is the one evening per week where you review them thoroughly. On weekday evenings, glance at the flips only to see if any name has now also cleared its daily condition and graduated to a shortlist.

---

### How to read it — section by section

#### 1. Market Breadth — set your risk appetite before looking at names

**W1: 22% GREEN / 71% RED.** The majority of assets are in weekly downtrends. This is a bearish macro environment — the report's primary opportunity section will be the bear shortlist tonight, and the breadth conclusion confirms it: *"bias to the short side or stand aside."*

**Cross-timeframe alignment: 6% with conviction.** Only 3 of 45 assets have both W1 and D1 in gear in the same direction. This is a low-conviction environment — the broad market has not yet committed. Be selective and size conservatively across the board.

**D1 Thermometer: 70% QUIET.** Entry conditions are calm. This is good news for both sides — slippage will be low and the crowd is not overexcited. The combination of *bearish W1 + calm thermometer* is a classic Elder setup: quiet distribution in a downtrend, with well-timed entries available.

**Decision at this point:** Macro is bearish but not catastrophically so (not 100% RED). Work the bear shortlist primarily. If a bull setup exists, it can be taken at reduced size — but the macro tailwind is with the shorts tonight.

---

#### 2. Primary Shortlists — your order candidates for Monday

**The bear shortlist leads because macro is 71% RED.** Two assets qualify: ATOM and DOT.

Both share the same profile:

| Field | Value | What it means |
|-------|-------|---------------|
| W1 RED for 14 days | 2 weeks | Weekly engine turned down and has held. EMA is falling. |
| D1 RED for 3 days | ⚡ fresh | Daily impulse just flipped. Highest-conviction short window. |
| temp=0 (inside bar) | indecision | Today's range was inside yesterday's — no range extension. Valid QUIET entry, but size conservatively: inside bars sometimes resolve in either direction. |
| Target: yesterday low − EMA | | EMA gives the expected next-bar range extension downward. |

The **⚠️ All entries are fresh signals** warning is important: both ATOM and DOT are only 3 days into their D1 RED signal. The market is just beginning to move — signals have not yet proven they will hold. Do not size as if this is a confirmed trend; treat these as early-stage entries with the exit rule active from day one.

**RENDERUSDT** qualifies on the bull side despite the bearish macro, because its W1 is GREEN. This is not a contradiction — Elder's rules allow long entries when W1 is GREEN regardless of what the rest of the universe is doing. Treat it as a reduced-size opportunity given the macro headwind.

**Monday morning action for ATOM/DOT (short side):**
1. Look up the 30 May low on your broker
2. Place a **sell stop** just below that low — you enter only if Monday continues downward
3. Place a **protective stop** above the recent swing high (or 1–2 ATR above the 13-EMA)
4. Set a **limit buy** at (30 May low − thermometerEma)
5. Check that distance from entry to target is at least 2× your stop distance — if not, skip

---

#### 3. Fresh W1 Flips — your watchlist for the week

Both flip sections are empty this week — no W1 state changes occurred in the last 7 days. Nothing to add to the watchlist. Come back Sunday.

When the flip lists are populated (as they were in earlier reports), prioritise by volume: the highest-volume names are the most tradeable. Low-volume names may show the signal but have insufficient liquidity for meaningful position sizes.

---

#### 4. Spike Alerts — what to do with existing positions

All five spiking assets are **W1 RED** — the label "short-covering rally into a downtrend" tells you exactly what this is. These assets bounced strongly on May 30, but the weekly engine is still pointed down. This is not a reversal — it is the crowd covering shorts and creating temporary euphoria. Elder's rule: sell into this strength, not after it.

| Asset | Multiple | Action |
|-------|----------|--------|
| BNBUSDT | 6.3× | Most extreme spike. If short, Friday was a partial cover point. If long (against W1 trend), exit. |
| ASTERUSDT | 4.8× | Second most extreme. Same rule. |
| ALGOUSDT, HBARUSDT | 3.8× | At the threshold. Do not open new positions Monday. |
| WLDUSDT | 3.1× | Just above threshold. Tighten any stop aggressively if holding. |

Note that spike context depends on W1 direction. A spike in a W1 GREEN asset would be labelled differently ("take profits on longs") — the same temperature reading has opposite implications depending on the strategic direction. The report groups spikes by W1 state precisely for this reason.

---

## Quick Reference Card

```
EVERY EVENING (after daily close):

1. GET /api/v1/elder-summary   ← one call covers everything below

   OR manually:

1. Breadth check     → /elder-impulse-market-pulse W1 + D1
                        W1 >50% RED? Bear bias. W1 >50% GREEN? Bull bias.
                        Cross-timeframe alignment low (<10%)? Size conservatively.

2. Primary shortlists
   Bull side → W1 GREEN + D1 GREEN + D1 QUIET
   Bear side → W1 RED   + D1 RED   + D1 QUIET
   (summary leads with the side matching macro bias)
   Empty result? Nothing actionable tonight — stop here.

3. Per-asset review  → daysSinceChange ≤ 3? Fresh signal — size conservatively.
                        All entries fresh? Reduce size further across the board.
                        Inside bar (temp=0)? Valid entry, but indecision — size conservatively.
                        Price near 13-EMA? Ideal. Far extended? Skip or reduce.

4. Set levels (longs)  → Entry: buy stop above today's high
                          Stop:  below recent swing low / 13-EMA
                          Target: yesterday's high + thermometerEma

   Set levels (shorts) → Entry: sell stop below today's low
                          Stop:  above recent swing high / 13-EMA
                          Target: yesterday's low − thermometerEma

5. Check open positions
   → D1 turned against you? Exit at close. No exceptions.
   → SPIKE alert on a held asset?
       W1 GREEN: take profits on longs.
       W1 RED:   short-covering rally — consider selling into strength.

EVERY SUNDAY:
   → Fresh W1 GREEN flips? Note names. Wait for D1 GREEN + QUIET to enter.
   → Fresh W1 RED flips?  Tighten stops on any longs. Watch for D1 RED + QUIET to short.
```

---

*Based on the Elder Impulse System and Market Thermometer as described in Alexander Elder's trading research. Project Colombo automates the data pipeline; the trading decisions remain the trader's responsibility.*
