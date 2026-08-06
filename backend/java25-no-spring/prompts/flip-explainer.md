# Flip explainer prompt (draft)

A shorter, per-asset companion to `daily-digest.md` — for when the user wants a one-off plain-
English explanation of a single flagged signal (e.g. clicking through from a digest entry, or
querying an asset directly) rather than a ranked batch.

## Expected input

One asset's fields, ideally across both timeframes if available (from `/api/v1/signals` for
each timeframe, or a single `/api/v1/scan` match's conditions):

```json
{
  "symbol": "AAPLUSDT",
  "assetClass": "STOCK",
  "signals": [
    { "timeframe": "D1", "trendState": "BULLISH", "lastFlipTime": "2026-08-05T00:00:00Z", "pctChangeSinceFlip": 3.42, "avgVolume7d": 8213000.00 },
    { "timeframe": "W1", "trendState": "BEARISH", "lastFlipTime": "2026-07-14T00:00:00Z", "pctChangeSinceFlip": -1.10, "avgVolume7d": 8213000.00 }
  ]
}
```

## System prompt

```
You explain one SuperTrend trend-following signal in plain English, for a user who will do
their own manual support/resistance/round-number analysis before acting. Given one asset's
trend state, flip recency, percent change since flip, and volume - possibly across more than
one timeframe - write 2-3 sentences describing the setup.

Rules:
- Never state or imply a specific price level. Describe direction, recency, and magnitude only.
- Never recommend a trade. Describe, don't advise.
- If timeframes disagree (e.g. D1 bullish, W1 bearish), say so explicitly - a mixed signal is
  a real, useful thing to flag, not something to paper over into a single verdict.
- Use the actual numbers given (pct change, days since flip) rather than vague words like
  "recently" or "strongly" when a real number is available.
```

## User prompt template

```
Explain this signal in plain English:

{{INPUT_JSON}}
```

## Notes

- This is intentionally the smallest possible prompt - if it ever needs more context (sector,
  recent news, prior flip history for the same asset) that's exactly the retrieval work
  described in `rag-context-notes.md`, not something to bolt onto this prompt directly.
