````markdown
# Story 007 — Tasks  
**Market Pipeline Orchestration**

This task list implements a single deterministic daily market pipeline.

Complete phases in order.

---

# Phase 1 — Preparation & Cleanup

## 1.1 Remove Independent Scheduling

- [x] Remove `@Scheduled` from `CandleIngestionService`
- [x] Remove `@Scheduled` from indicator computation service (e.g. `SuperTrendService`)
- [x] Remove `@Scheduled` from `SignalStateService`
- [x] Remove `@Scheduled` from `MarketPulseService`
- [x] Ensure no service directly triggers another phase
- [x] Remove or deprecate:
  - [x] `app.ingestion.cron`
  - [x] `app.signal-state.cron`

**Verification**
- [x] Application starts without scheduler conflicts
- [x] No duplicate cron jobs execute

---

# Phase 2 — Create MarketPipelineService

## 2.1 Create Service Skeleton

- [x] Create `MarketPipelineService`
- [x] Inject:
  - [x] `CandleIngestionService`
  - [x] Indicator computation service
  - [x] `SignalStateService`
  - [x] `MarketPulseService`
  - [x] `IngestionRunService` / orchestrator

## 2.2 Define Entry Method

Implement:

```java
public void runDaily(Provider provider, Timeframe timeframe, RunMode mode)
````

* [x] Apply defaults if null:

  * Provider → `BINANCE`
  * Timeframe → `D1`
  * Mode → `INCREMENTAL`

---

# Phase 3 — Implement Orchestration Flow

## 3.1 Concurrency Protection

* [x] Check if a `RUNNING` ingestion exists for `provider + timeframe`
* [x] If exists:

  * [x] Throw concurrency exception (manual path)
  * [x] Log and skip (scheduler path)

## 3.2 Create RUNNING Record

* [x] Insert `ingestion_run` row:

  * status = `RUNNING`
  * started_at = now

## 3.3 Execute Phases Sequentially

Execution order must be strict:

1. Ingestion
2. Indicator computation
3. Signal detection
4. Market pulse aggregation

### Implementation Tasks

* [x] Log: "Starting phase: INGESTION"

* [x] Call `CandleIngestionService`

* [x] Log phase completion + duration

* [x] Log: "Starting phase: INDICATOR"

* [x] Call indicator computation service

* [x] Log completion + duration

* [x] Log: "Starting phase: SIGNAL"

* [x] Call `SignalStateService`

* [x] Log completion + duration

* [x] Log: "Starting phase: MARKET_PULSE"

* [x] Call `MarketPulseService`

* [x] Log completion + duration

## 3.4 Failure Handling

* [x] Wrap pipeline in try/catch
* [x] On exception:

  * [x] Mark run `FAILED`
  * [x] Set `finished_at`
  * [x] Compute `duration_ms`
  * [x] Store `error_sample` (max 1000 chars)
  * [x] Stop downstream execution

## 3.5 Success Handling

* [x] On successful completion:

  * [x] Mark run `SUCCESS`
  * [x] Set `finished_at`
  * [x] Compute `duration_ms`
  * [x] Persist ingestion statistics

---

# Phase 4 — Scheduler

## 4.1 Add New Cron Property

* [x] Add configuration property:

```
app.market-pipeline.cron
```

## 4.2 Create Scheduler Method

* [x] Add single `@Scheduled` method
* [x] Invoke:

```java
marketPipelineService.runDaily(BINANCE, D1, INCREMENTAL);
```

* [x] Handle concurrency exception:

  * [x] Log skip
  * [x] Do not throw

**Verification**

* [x] Only ONE scheduled job exists in application

---

# Phase 5 — Manual Trigger Refactor

## 5.1 Update Endpoint

- [x] Modify `POST /internal/ingestion/run`
- [x] Replace direct ingestion call with pipeline call

## 5.2 Concurrency Response

- [x] If `RUNNING` exists:

  - [x] Return HTTP 409
  - [x] Do not start new run

## 5.3 Response Contract

Return:

```json
{
  "runId": <id>,
  "status": "STARTED"
}
```
- [x] Success response contract matches

---

# Phase 6 — Logging Improvements

- [x] Log pipeline start
- [x] Log pipeline completion
- [x] Log phase durations
- [x] Log concurrency skips
- [x] Log failures clearly with phase name

Logs must make operational diagnosis easy.

---

# Phase 7 — Unit Tests

* [ ] Verify phases execute in correct order
* [ ] Verify downstream phases are NOT executed after failure
* [ ] Verify concurrency rejection logic
* [ ] Verify manual trigger invokes pipeline (not ingestion directly)

---

# Phase 8 — Integration Tests (Testcontainers)

* [ ] Seed assets
* [ ] Run pipeline once:

  * [ ] Candles created
  * [ ] `signal_state` rows created
  * [ ] `market_pulse` rows created
* [ ] Run pipeline again:

  * [ ] Assert idempotency (no duplicates)
* [ ] Simulate indicator failure:

  * [ ] Downstream phases not executed
  * [ ] `ingestion_run.status = FAILED`

---

# Phase 9 — Final Verification Checklist

* [ ] Only one scheduled job exists
* [ ] Manual trigger executes full pipeline
* [ ] `ingestion_run` tracks full pipeline lifecycle
* [ ] All phases respect finalized candle boundary rule
* [ ] No duplicate data on re-run
* [ ] Concurrency protection works
