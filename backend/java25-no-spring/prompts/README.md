# LLM digest prompts (draft)

**Status: draft only, not wired up to anything yet.** These are prompt templates written
against the system's *actual current* HTTP API response shapes, kept here so a later
OpenSpec proposal (`/opsx:propose`) for an "LLM triage layer" has concrete prompts to start
from instead of designing from scratch. Nothing in `src/` calls an LLM today.

## Why these exist

Per the conversation that produced them: this system is a triage tool, not a decision-maker.
The user reviews flagged trend signals and does their own discretionary support/resistance/
round-number analysis before acting. The goal of an "AI layer" here is to help him decide
*which* flagged assets are worth that manual chart time — not to replace the manual read, and
explicitly not to have an LLM invent price levels it can't reliably compute from a list of
numbers (see the guardrail in every prompt below).

## What feeds these prompts

Structured JSON already produced by this module's own read endpoints — no chart images, no
raw candle series, no external data:

- `GET /api/v1/scan` — condition matches (trend state, flip time, days since flip, volume once
  added, TradingView link)
- `GET /api/v1/summary?format=JSON` — market-breadth pulse (bullish/bearish/missing counts per
  asset class) for whichever timeframe(s) the caller wants context for
- `GET /api/v1/signals?sort=LIQUIDITY_DESC` — full signal list, usable as a fallback/broader
  input if scan conditions are too narrow on a given day

This keeps token cost low (a few hundred tokens of structured fields per asset, not thousands
of raw OHLCV numbers or image tokens) — see the token-cost discussion this was drafted from.

## Files

- `daily-digest.md` — end-of-day/morning prompt: takes a batch of scan matches + market
  breadth context, ranks which are worth a look and says why.
- `flip-explainer.md` — per-asset prompt: takes one signal's fields, writes a short plain-
  English paragraph explaining the setup (not a recommendation).
- `rag-context-notes.md` — notes on what retrieval sources *could* supplement these prompts
  later (this system has no news/fundamentals ingestion today — these are ideas, not a design).

## Non-goals (deliberately out of scope for these drafts)

- No support/resistance or round-number levels — that stays the user's manual analysis.
- No buy/sell/price-target language — these prompts summarize and rank, they don't advise.
- No chart vision / TradingView MCP — everything here consumes structured JSON this system
  already computes, per the "cheap tokens, no image input" decision from the design discussion.
