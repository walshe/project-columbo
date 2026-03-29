# 📊 API Reference Guide (Project Columbo)

This guide provides simple, human-readable examples of how to interact with the Project Columbo API. Whether you are a developer building a dashboard or a trader looking for automated insights, these examples will show you what the data looks like and how to get it.

---

## 📖 Glossary for Human Beings
Before we dive into the data, here are a few simple definitions for the terms you'll see:

*   **Bullish:** The price is likely to go up. Think "Optimistic." (In our system, this state is determined by the **SuperTrend** indicator).
*   **Bearish:** The price is likely to go down. Think "Cautious." (In our system, this state is determined by the **SuperTrend** indicator).
*   **A "Flip":** When the trend changes from one direction to another (e.g., Bearish to Bullish) according to the **SuperTrend** line.
*   **Market Pulse:** A high-level "vibe check" of the entire market. If 80% of assets are Bullish, the pulse is strong.
*   **RSI (Relative Strength Index):** A measurement of how "fast" the price is moving. It helps identify if something is overbought or oversold.
*   **SuperTrend:** The primary indicator that follows the general path of the price to determine the current trend direction. Our Bullish/Bearish states are based on this. We use the [Official SuperTrend Indicator](https://www.tradingview.com/support/solutions/43000634738-supertrend/) with the standard settings of **(10, 2.0)**.
*   **Timeframe:** The window of time each data point represents (e.g., `1D` for one day, `4H` for four hours).

---

## 📈 1. Checking the "Market Health" (Summary)
**Goal:** Get a quick overview of what's happening across all tracked assets.

The `summary` endpoint is designed to be readable by humans or easy to display in an app. You can get it in a neat **Markdown** format (great for Slack/Discord bots) or raw **JSON** (great for apps).

### 📝 How to get this report:
**API Address:** `GET /api/v1/summary?timeframe=1D&format=markdown`

**What it tells you:**
*   How many assets are currently in an uptrend (Bullish) vs. a downtrend (Bearish).
*   A list of assets that just "flipped" their trend recently.
*   Specific "combos" like assets that are in an uptrend AND showing strong momentum.

<details>
<summary><b>Click to see sample report</b></summary>

```markdown
# Market Summary Report

## Market Pulse
- **Bullish:** 8
- **Bearish:** 33
- **Bullish Ratio:** 19.51% (The market is currently leaning Bearish)

## Recent Bullish Flips
- [XLMUSDT](https://www.tradingview.com/chart/?symbol=BINANCE%3AXLMUSDT&interval=1D): Flipped 3 days ago (Vol: 8.3M)
- [QNTUSDT](https://www.tradingview.com/chart/?symbol=BINANCE%3AQNTUSDT&interval=1D): Flipped 11 days ago (Vol: 2.0M)
- [DOGEUSDT](https://www.tradingview.com/chart/?symbol=BINANCE%3ADOGEUSDT&interval=1D): Flipped 12 days ago (Vol: 69.0M)

## Recent Bearish Flips
- [SOLUSDT](https://www.tradingview.com/chart/?symbol=BINANCE%3ASOLUSDT&interval=1D): Flipped 1 day ago (Vol: 258.6M)
- [BTCUSDT](https://www.tradingview.com/chart/?symbol=BINANCE%3ABTCUSDT&interval=1D): Flipped 7 days ago (Vol: 1.4B)

## Bullish Trend + RSI Cross Above 60
- [XLMUSDT](https://www.tradingview.com/chart/?symbol=BINANCE%3AXLMUSDT&interval=1D): Supertrend flipped 3 days ago, RSI crossed 3 days ago (Value: 62.5) (Vol: 8.3M)
- [DOGEUSDT](https://www.tradingview.com/chart/?symbol=BINANCE%3ADOGEUSDT&interval=1D): Supertrend flipped 12 days ago, RSI crossed 5 days ago (Value: 60.2) (Vol: 69.0M)
```
</details>

---

## 🔍 2. Finding Specific Trends (Signals)
**Goal:** Find all assets that are currently in a specific state, like "Show me everything that is currently Bullish on a Daily timeframe."

### 🚦 How to get this list:
**API Address:** `GET /api/v1/signals?timeframe=1D&indicatorType=SUPERTREND&state=BULLISH&sort=LAST_FLIP_DESC`

**What the data means:**
*   `symbol`: The asset name (e.g., `XLMUSDT` is Stellar).
*   `daysSinceFlip`: How many days it has been since the trend started.
*   `avgVolume7d`: How much trading happened on average over the last week (higher volume usually means a more "reliable" trend).
*   `tradingviewUrl`: A direct link to see the chart yourself.

<details>
<summary><b>Click to see sample response data</b></summary>

```json
[
  {
    "symbol": "XLMUSDT",
    "trendState": "BULLISH",
    "lastFlipTime": "2026-03-25T23:59:59.999Z",
    "daysSinceFlip": 3,
    "avgVolume7d": 8331604.50,
    "tradingviewUrl": "https://www.tradingview.com/chart/?symbol=BINANCE%3AXLMUSDT&interval=1D"
  },
  {
    "symbol": "DOGEUSDT",
    "trendState": "BULLISH",
    "lastFlipTime": "2026-03-16T23:59:59.999Z",
    "daysSinceFlip": 12,
    "avgVolume7d": 69030003.25,
    "tradingviewUrl": "https://www.tradingview.com/chart/?symbol=BINANCE%3ADOGEUSDT&interval=1D"
  },
  {
    "symbol": "QNTUSDT",
    "trendState": "BULLISH",
    "lastFlipTime": "2026-03-17T23:59:59.999Z",
    "daysSinceFlip": 11,
    "avgVolume7d": 2012345.10,
    "tradingviewUrl": "https://www.tradingview.com/chart/?symbol=BINANCE%3AQNTUSDT&interval=1D"
  }
]
```
</details>

### 📉 How to get this list:
**API Address:** `GET /api/v1/signals?timeframe=1D&indicatorType=SUPERTREND&state=BEARISH&sort=LAST_FLIP_DESC`

**What the data means:**
*   This shows assets that have recently crossed over into a downtrend.
*   `daysSinceFlip`: If this is `0` or `1`, the trend just changed!

<details>
<summary><b>Click to see sample response data</b></summary>

```json
[
  {
    "symbol": "SOLUSDT",
    "trendState": "BEARISH",
    "lastFlipTime": "2026-03-28T23:59:59.999Z",
    "daysSinceFlip": 1,
    "avgVolume7d": 258612450.00,
    "tradingviewUrl": "https://www.tradingview.com/chart/?symbol=BINANCE%3ASOLUSDT&interval=1D"
  },
  {
    "symbol": "BTCUSDT",
    "trendState": "BEARISH",
    "lastFlipTime": "2026-03-22T23:59:59.999Z",
    "daysSinceFlip": 7,
    "avgVolume7d": 1406321987.00,
    "tradingviewUrl": "https://www.tradingview.com/chart/?symbol=BINANCE%3ABTCUSDT&interval=1D"
  }
]
```
</details>

---

## ⚡ 3. The "Deep Scan" (Advanced Matching)
**Goal:** Ask complex questions like "Show me assets that are in an uptrend AND have just crossed a momentum threshold."

### 🧬 How to run this scan:
**API Address:** `POST /api/v1/scan`

**Instructions:**
We want assets where:
1.  The **SuperTrend** is Bullish (the general path is up).
2.  The trend started in the last **3 days** (we want fresh moves).
3.  The **RSI** is also Bullish (momentum is supporting the move).

**The "Request" (What to send):**
```json
{
  "timeframe": "1D",
  "operator": "AND",
  "conditions": [
    {
      "indicatorType": "SUPERTREND",
      "state": "BULLISH",
      "maxDaysSinceFlip": 3
    },
    {
      "indicatorType": "RSI",
      "state": "BULLISH"
    }
  ],
  "limit": 10
}
```

**The "Response" (What you get back):**
```json
{
  "timeframe": "1D",
  "operator": "AND",
  "conditions": [
    {
      "indicatorType": "SUPERTREND",
      "state": "BULLISH",
      "maxDaysSinceFlip": 3
    },
    {
      "indicatorType": "RSI",
      "state": "BULLISH"
    }
  ],
  "results": [
    {
      "symbol": "SOLUSDT",
      "matched": true,
      "details": "SuperTrend BULLISH (2 days ago), RSI BULLISH"
    },
    {
      "symbol": "LINKUSDT",
      "matched": true,
      "details": "SuperTrend BULLISH (1 day ago), RSI BULLISH"
    }
  ]
}
```

---

## 💓 4. Pulse History (Sentiment Tracking)
**Goal:** See how market sentiment has changed over time.

### 💓 How to get this history:
**API Address:** `GET /api/v1/market-pulse/history?timeframe=1D&indicatorType=SUPERTREND`

**What it tells you:**
*   `bullishRatio`: A percentage from 0 to 1. If it's `0.80`, 80% of the market is Bullish. If it moves from `0.20` to `0.50`, you are seeing a market-wide recovery.

<details>
<summary><b>Click to see Pulse Data</b></summary>

```json
[
  {
    "snapshotCloseTime": "2026-03-28T23:59:59.999Z",
    "bullishCount": 8,
    "bearishCount": 33,
    "missingCount": 2,
    "totalAssets": 43,
    "bullishRatio": 0.1951
  },
  {
    "snapshotCloseTime": "2026-03-27T23:59:59.999Z",
    "bullishCount": 7,
    "bearishCount": 34,
    "missingCount": 2,
    "totalAssets": 43,
    "bullishRatio": 0.1707
  },
  {
    "snapshotCloseTime": "2026-03-26T23:59:59.999Z",
    "bullishCount": 10,
    "bearishCount": 31,
    "missingCount": 2,
    "totalAssets": 43,
    "bullishRatio": 0.2439
  }
]
```
</details>

---

## 📽️ TradingView Integration
Project Columbo is tightly integrated with TradingView to allow you to visualize every signal generated by the API.

1.  **Direct Chart Links:** Every `SignalStateDto` and `ScanResult` contains a `tradingviewUrl`. Following this link will open the correct asset (on Binance) and the correct timeframe.
2.  **Indicator Alignment:** Our backend uses the [Official SuperTrend Algorithm](https://www.tradingview.com/support/solutions/43000634738-supertrend/). If you add the **SuperTrend** indicator to your TradingView chart and set it to **Length: 10** and **Multiplier: 2.0**, the "Bullish/Bearish" colors and "Buy/Sell" arrows will align **perfectly** with our API's `trendState` and `lastFlipTime`.
3.  **Timing Consistency:** Signals are recorded at the **close** of a candle. If the API says a "Bullish Flip" occurred at 00:00 UTC on March 25th, you will see the trend change on that exact candle in TradingView.

---

## 🛠️ Quick Tips for Developers
*   **API Root:** All requests start with `/api/v1`.
*   **Timeframes:** Valid options are typically `1D` (Daily) and `4H` (4-Hour).
*   **TradingView Links:** The `tradingviewUrl` provided in response data will load the chart for that specific asset and timeframe.
*   **Signal Verification:** If you enable the **SuperTrend** indicator on your TradingView chart, the Bullish/Bearish signals from our API will correspond exactly with what you see on the screen.
*   **Interactive Docs:** Visit `http://localhost:8080/swagger-ui/index.html` to try these live on your local machine!
