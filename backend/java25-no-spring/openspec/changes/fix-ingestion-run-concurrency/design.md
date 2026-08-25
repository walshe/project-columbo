## Context

`add-tiingo-provider` made a single `ingestDaily()`/`computeForAllActiveAssets()` call process every active asset regardless of provider — a deliberate, correct decision (see that change's design.md: "no scheduler/orchestrator changes needed"). But `IngestionRunDao`'s single-flight lock (`V9`: unique index on `(provider, timeframe) WHERE status = 'RUNNING'`) was never revisited, and `POST /api/v1/internal/ingestion/run` still accepts a `provider` field that actually does nothing except pick which lock bucket a run occupies. That change's design.md flagged this exact staleness as "purely cosmetic" — it wasn't: two trigger calls with different provider labels for the same timeframe don't collide on the lock, so both proceed, each processing the full asset universe concurrently.

`CandleDao.upsert`/`SuperTrendIndicatorDao.upsert` were written as `SELECT` (find existing) → `INSERT` or `UPDATE`, two-to-three separate statements per call, no explicit row locking between the check and the act. Fine under the assumption that only one process ever writes to a given `(asset_id, timeframe, close_time)` at a time — an assumption the run-lock bug above violates in practice, but one this DAO code shouldn't have been relying on implicitly either.

## Goals / Non-Goals

**Goals:**
- A pipeline run for a given timeframe can never overlap with another one, full stop — no per-provider carve-out, since there's no such thing as a provider-scoped run anymore.
- `CandleDao.upsert`/`SuperTrendIndicatorDao.upsert` are safe under real concurrent writes to the same key, not just safe under the assumption that callers never do that.
- A provider returning a valid empty response during an expected fetch is visible in logs, not indistinguishable from "nothing to do."

**Non-Goals:**
- No change to how an asset's candles are routed to a provider (`AssetVenue`/`providersByVenue`) — untouched.
- No retry/backoff logic for the empty-response case — logging visibility is the ask, not new fetch behavior.
- No attempt to keep `provider` as an optional/ignored field on the trigger request for backwards compatibility — this is an internal ops endpoint with no external consumers to break, and keeping a silently-ignored field around is exactly the kind of misleading surface that caused this bug.

## Decisions

**1. Remove `provider` from `ingestion_run` and the trigger endpoint entirely, rather than just re-keying the lock to `timeframe` while keeping the field.**
Considered keeping `POST /api/v1/internal/ingestion/run`'s `provider` field but ignoring it (only locking on `timeframe`) — rejected on explicit direction: a parameter that looks like it scopes behavior but doesn't is the exact shape of bug this is. Removing it is more honest and prevents a future caller from reasonably assuming per-provider triggering is possible.
- *Alternative considered:* keep `provider` on `ingestion_run` for historical/audit purposes (which provider triggered a run) even though it no longer means "which assets were processed." Rejected — a label that no longer describes what happened is worse than no label; every current caller already hardcodes `Provider.BINANCE` into it, meaning the existing data is already misleading for any run actually triggered to test Tiingo specifically.

**2. Single-flight lock re-keyed to `timeframe` alone.**
`CREATE UNIQUE INDEX ... ON ingestion_run (timeframe) WHERE status = 'RUNNING'` replaces the `(provider, timeframe)` version. Matches reality: exactly one D1 run and one W1 run may be in flight at a time, independent of any provider concept.

**3. Atomic upsert via `INSERT ... ON CONFLICT ... DO UPDATE ... WHERE <differs> RETURNING (xmax = 0)`.**
`xmax = 0` is Postgres's standard way to distinguish "this row was just inserted" from "this row was just updated via the conflict path" in a single `RETURNING` clause — no second query needed. The `WHERE <differs>` clause on the `DO UPDATE` means a conflict with identical values updates nothing and returns no row, which maps directly to the existing `UNCHANGED` outcome (no separate equality check needed either). The existing "log a WARN when a revision occurs" behavior is preserved, but without the previously-stored values in the log message (they're no longer read separately before the write) — the new value and the fact that a revision happened is what actually matters for someone investigating data drift; the tradeoff of a slightly less detailed log line is worth removing the race entirely.
- *Alternative considered:* keep the two-statement shape but wrap it in `SELECT ... FOR UPDATE` to lock the row (or its absence) for the duration. Rejected — `SELECT ... FOR UPDATE` can't lock a row that doesn't exist yet, so it doesn't actually close the race for the insert case, which is the case that was actually failing.

**4. `CandleIngestionService.ingestForAsset` logs at WARN (not ERROR) when a fetch attempt returns zero candles.**
WARN, not ERROR, because this isn't necessarily a failure — a provider can legitimately have no new data for reasons short of a broken symbol (e.g. a market holiday). It's still worth a human's attention if it persists, which WARN visibility supports without treating every occurrence as an ingestion failure requiring the same response as an actual exception.

## Risks / Trade-offs

- **[Risk] Removing `provider` from the trigger request is a breaking API change for any external caller that was passing it.** → Accepted: this is `/api/v1/internal/...`, not a documented public integration surface, and the field never did anything the caller could rely on other than misleadingly implying per-provider scoping.
- **[Trade-off] The atomic-upsert fix drops the previously-logged "stored=... new=..." value comparison in the revision-warning log line.** → Accepted per Decision 3 — the alternative (a non-atomic pre-read purely for logging) would reintroduce a narrow, log-line-only staleness window; not worth it for a cosmetic log detail.

## Migration Plan

1. `V20__drop_ingestion_run_provider.sql`: drop the old `(provider, timeframe)` unique/lookup indexes, drop the `provider` column, create the new `(timeframe) WHERE status = 'RUNNING'` unique index and `(timeframe, started_at DESC)` lookup index.
2. Code deploy: all the signature changes below. No transactional-order constraint like `add-tiingo-provider`'s enum additions — a column drop plus new indexes is compatible with either deploy order in practice, but code should deploy promptly after since old code referencing the dropped column would fail immediately.
3. `mvn test`/`mvn verify -Pe2e` must pass before merge — the run-lock and upsert changes are exactly the kind of concurrency-shaped bug that's easy to "fix" in a way that looks right but isn't; the full existing `PipelineOrchestratorTest`/`IngestionTriggerHandlerIntegrationTest` suites already exercise the lock behavior and must be updated and re-verified, not just recompiled.
- **Rollback:** re-add the column (nullable, since old rows have no meaningful value to backfill) and the old indexes if ever needed; not expected to be necessary since this only removes a field, it doesn't repurpose one.
