## Context

`CandleIngestionService` fetches a single Binance klines window per asset from `backfill-start` (or `lastStoredClose+1`) to now, with no `limit` param — so Binance's 500-candle default cap applies and there is no pagination. `backfill-start` is `2025-01-01` (~547 days), which exceeds that cap, so a fresh backfill truncates to the earliest 500 daily candles and drops the most recent ~47 days until a second incremental run heals it. Because SuperTrend's bands are path-dependent, an incomplete series yields wrong flip points on a fresh DB. Weekly (W1) candles are rolled up from daily; the only enabled W1 indicator is SuperTrend (10, 2.0), which needs ~20 weekly candles to stabilise its ATR. Nothing validates the configured start today. There are no existing startup validators/`ApplicationRunner`s in the codebase.

## Goals / Non-Goals

**Goals:**
- Size `backfill-start` to what the enabled weekly indicators actually need, and keep the fresh-backfill window under Binance's 500 default
- Fail fast at startup if `backfill-start` cannot supply enough weekly warm-up, rather than emitting unreliable signals
- Make the coverage floor explicit and self-documenting so it moves with the enabled indicator set

**Non-Goals:**
- The candle coverage/freshness endpoint (separate change B)
- Paginating the Binance fetch or raising its `limit` (a valid alternative fix, deferred; tightening the window sidesteps it at current scale)
- Any change to SuperTrend or other indicator math
- Auto-correcting/clamping the configured date (explicitly rejected — see Decisions)

## Decisions

**Move `backfill-start` to `2025-07-01` (~52 weekly candles)**
Gives >2x the ~20-week W1 SuperTrend warm-up as buffer, and keeps a fresh full backfill (~1 year of daily candles) under Binance's 500 default so it completes in a single pass. *Alternative*: keep `2025-01-01` and pass `limit=1000` — correct, but pulls ~1.5 years we don't use while Elder is disabled; tightening the window is leaner and encodes intent.

**Fail-fast validator via `@PostConstruct`, not warn or clamp**
A dedicated `BackfillStartValidator` component validates in `@PostConstruct`; throwing there aborts context initialization so the app will not start with a data-config that can't produce valid weekly signals. *Alternatives rejected*: (a) warn-and-continue — easy to miss, and the failure mode (silently wrong flips) is exactly what we're guarding against; (b) auto-clamp the date — silently overrides an operator's explicit config.

**Coverage floor as a named constant tied to enabled indicators**
Define e.g. `MIN_WEEKLY_CANDLES_REQUIRED = 20` with a comment: this reflects only the currently enabled W1 indicator (SuperTrend ATR warm-up); re-enabling Elder Impulse / Thermometer (W1 EMA-13 / MACD) needs ~100 weekly candles (~2 years), so raise the constant and move `backfill-start` back. Convert to a lookback as `weeks × 7 days` (with a small buffer) and compare against `now − backfill-start`. Using weekly candles as the unit keeps the check expressed in the same terms as the indicator requirement.

## Risks / Trade-offs

[A future indicator needs more history but the constant isn't updated] → The comment on the constant plus the fail-fast behaviour make the dependency explicit; if a re-enabled indicator's requirement exceeds coverage, the validator surfaces it at startup rather than in bad signals.

[Clock/`now`-relative check makes the required calendar start drift over time] → Acceptable and correct: the check is "enough history as of today", which is what warm-up actually depends on. `backfill-start` only needs to stay far enough back; the generous 52-week setting leaves ample margin.

[Fail-fast could block boot in an environment with an intentionally short history] → The exception message states the exact shortfall and remedy; the floor is intentionally the minimum for correctness, so blocking is the desired behaviour.
