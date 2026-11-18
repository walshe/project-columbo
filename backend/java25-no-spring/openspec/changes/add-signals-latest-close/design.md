## Context

`SignalQueryService.listSignals` already calls `candleDao.findLatestCloseByAssetForTimeframe(timeframe)` and stores the result in `latestCloseByAssetId`. This map is passed into the `Enrichment` record and then forwarded to `toSummary` as the `latestClose` local variable — it's used to compute `pctChangeSinceFlip` but is then discarded rather than stored on `SignalSummary`.

The only missing step is threading the value into the record.

## Goals / Non-Goals

**Goals:**
- Expose `latestClose` on every `SignalSummary` in the `/api/v1/signals` response.
- Zero additional DB round-trips per request.

**Non-Goals:**
- Adding `latestClose` to any other endpoint (e.g. `/api/v1/assets/by-state`, briefings, scan).
- Any change to ingestion, candle storage, or how `findLatestCloseByAssetForTimeframe` works.

## Decisions

**Add `latestClose` directly to `SignalSummary`** rather than a wrapper or separate response field. `SignalSummary` already holds `pctChangeSinceFlip` (derived from the same close), so this is a natural home and consistent with how other per-asset values are carried. Consumers that only needed `pctChangeSinceFlip` before don't need to change behaviour.

**Nullable, no `requireNonNull`** — consistent with `pctChangeSinceFlip` and `lastFlipTime`, which are null when the underlying data is absent. Callers are already prepared to handle nulls on `SignalSummary`.

## Risks / Trade-offs

**Existing `SignalSummary` constructors in tests** — every direct constructor call gains a new required parameter. The compiler will flag them all; no silent failures. → Mitigation: simple mechanical update, flagged by the task list.

**`SignalSummary` is used by briefing and scan paths** — those paths don't expose `latestClose` to consumers today, but they do call `toSummary`. The field will be populated there too (data is already fetched), which is harmless. No downstream formatter reads unknown fields.
