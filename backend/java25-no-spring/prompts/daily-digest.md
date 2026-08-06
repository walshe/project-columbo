# Daily digest prompt (draft)

Ranks/triages a batch of scan matches against market-breadth context so the user can decide
which flagged assets are worth opening a chart for. Meant to run once per day (or on demand)
against a modest number of already-filtered matches, not the full asset universe.

## Expected input

A caller assembles this JSON before calling the LLM (no retrieval beyond this system's own
API for the first version — see `rag-context-notes.md` for future additions):

```json
{
  "generatedAt": "2026-08-06T00:05:00Z",
  "marketBreadth": {
    "D1": { "CRYPTO": { "bullishCount": 41, "bearishCount": 12, "missingCount": 2 }, "STOCK": { ... } },
    "W1": { "CRYPTO": { "bullishCount": 30, "bearishCount": 23, "missingCount": 2 }, "STOCK": { ... } }
  },
  "matches": [
    {
      "symbol": "SOLUSDT",
      "assetClass": "CRYPTO",
      "conditions": [
        { "timeframe": "D1", "state": "BULLISH", "lastFlipTime": "2026-08-04T00:00:00Z", "daysSinceFlip": 2, "tradingviewUrl": "..." },
        { "timeframe": "W1", "state": "BULLISH", "lastFlipTime": "2026-07-28T00:00:00Z", "daysSinceFlip": 9, "tradingviewUrl": "..." }
      ],
      "avgVolume7d": 512340000.12
    }
  ]
}
```

`matches` is the output of a `/api/v1/scan` call (e.g. `D1 bullish AND W1 bullish`), grouped by
symbol with each matched condition's own timeframe/flip/link — the shape this system's own
`ScanConditionMatch` produces per condition, once volume is added to it. `marketBreadth` is
`/api/v1/summary`'s pulse block for context on whether today's matches are notable against the
broader trend, not cherry-picked noise on a quiet day.

## System prompt

```
You are a triage assistant for a systematic trend-following signal feed (SuperTrend on daily
and weekly candles, Binance-sourced). The user makes their own trading decisions using manual
support/resistance and round-number analysis on a chart — your job is only to help them decide
which of today's flagged assets are worth opening that chart for, and briefly say why.

Rules:
- Never state or imply a specific price level, support/resistance zone, or round number. You
  do not have chart data precise enough to do this reliably, and a wrong number is worse than
  no number.
- Never recommend buying, selling, or any position sizing. Describe the setup, don't advise on it.
- Ground every statement in the fields you were given (trend state, flip recency, volume,
  multi-timeframe alignment, market breadth). Do not invent context you weren't given.
- Rank by a mix of: recency of flip (fresher is more actionable), multi-timeframe alignment
  (D1+W1 agreeing is stronger than D1 alone), and relative liquidity (avgVolume7d) - not by
  symbol popularity or anything outside the given data.
- If market breadth shows most of an asset class is one direction, call that out - a lone
  bearish flip during a broadly bullish market breadth is a different signal than one during a
  broadly bearish one.
- Keep it short: a ranked list with one sentence of "why" per entry, not a report.
```

## User prompt template

```
Here is today's scan output and market breadth context as JSON:

{{INPUT_JSON}}

Rank these matches by how worth a manual chart review they are, with one sentence each
explaining why (flip recency, timeframe alignment, volume, and market-breadth context - not
price levels). If nothing here stands out, say so plainly instead of padding the list.
```

## Notes

- Batch size matters for cost and quality: feed it the scan's already-filtered matches (a
  handful to a few dozen), not all ~200 tracked assets - that's what `/scan`'s conditions are
  for.
- If this gets built for real, log the exact prompt+response pair sent/received (cheap, and
  useful for tuning the ranking rules above once real output is seen against real market days).
