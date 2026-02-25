````markdown
# Story 007 — Tasks  
**Market Pipeline Orchestration**

This task list implements a single deterministic daily market pipeline.

Complete phases in order.

---

# Phase 1 — Preparation & Cleanup

## 1.1 Remove Independent Scheduling

- [ ] Remove `@Scheduled` from `CandleIngestionService`
- [ ] Remove `@Scheduled` from indicator computation service (e.g. `SuperTrendService`)
- [ ] Remove `@Scheduled` from `SignalStateService`
- [ ] Remove `@Scheduled` from `MarketPulseService`
- [ ] Ensure no service directly triggers another phase
- [ ] Remove or deprecate:
  - [ ] `app.ingestion.cron`
  - [ ] `app.signal-state.cron`

**Verification**
- [ ] Application starts without scheduler conflicts
- [ ] No duplicate cron jobs execute

---

# Phase 2 — Create MarketPipelineService

## 2.1 Create Service Skeleton

- [ ] Create `MarketPipelineService`
- [ ] Inject:
  - [ ] `CandleIngestionService`
  - [ ] Indicator computation service
  - [ ] `SignalStateService`
  - [ ] `MarketPulseService`
  - [ ] `IngestionRunService` / orchestrator

## 2.2 Define Entry Method

Implement:

```java
public void runDaily(Provider provider, Timeframe timeframe, RunMode mode)
````

* [ ] Apply defaults if null:

  * Provider → `BINANCE`
  * Timeframe → `D1`
  * Mode → `INCREMENTAL`

---

# Phase 3 — Implement Orchestration Flow

## 3.1 Concurrency Protection

* [ ] Check if a `RUNNING` ingestion exists for `provider + timeframe`
* [ ] If exists:

  * [ ] Throw concurrency exception (manual path)
  * [ ] Log and skip (scheduler path)

## 3.2 Create RUNNING Record

* [ ] Insert `ingestion_run` row:

  * status = `RUNNING`
  * started_at = now

## 3.3 Execute Phases Sequentially

Execution order must be strict:

1. Ingestion
2. Indicator computation
3. Signal detection
4. Market pulse aggregation

### Implementation Tasks

* [ ] Log: `"Starting phase: INGESTION"`

* [ ] Call `CandleIngestionService`

* [ ] Log phase completion + duration

* [ ] Log: `"Starting phase: INDICATOR"`

* [ ] Call indicator computation service

* [ ] Log completion + duration

* [ ] Log: `"Starting phase: SIGNAL"`

* [ ] Call `SignalStateService`

* [ ] Log completion + duration

* [ ] Log: `"Starting phase: MARKET_PULSE"`

* [ ] Call `MarketPulseService`

* [ ] Log completion + duration

## 3.4 Failure Handling

* [ ] Wrap pipeline in try/catch
* [ ] On exception:

  * [ ] Mark run `FAILED`
  * [ ] Set `finished_at`
  * [ ] Compute `duration_ms`
  * [ ] Store `error_sample` (max 1000 chars)
  * [ ] Stop downstream execution

## 3.5 Success Handling

* [ ] On successful completion:

  * [ ] Mark run `SUCCESS`
  * [ ] Set `finished_at`
  * [ ] Compute `duration_ms`
  * [ ] Persist ingestion statistics

---

# Phase 4 — Scheduler

## 4.1 Add New Cron Property

* [ ] Add configuration property:

```
app.market-pipeline.cron
```

## 4.2 Create Scheduler Method

* [ ] Add single `@Scheduled` method
* [ ] Invoke:

```java
marketPipelineService.runDaily(BINANCE, D1, INCREMENTAL);
```

* [ ] Handle concurrency exception:

  * [ ] Log skip
  * [ ] Do not throw

**Verification**

* [ ] Only ONE scheduled job exists in application

---

# Phase 5 — Manual Trigger Refactor

## 5.1 Update Endpoint

* [ ] Modify `POST /internal/ingestion/run`
* [ ] Replace direct ingestion call with pipeline call

## 5.2 Concurrency Response

* [ ] If `RUNNING` exists:

  * [ ] Return HTTP 409
  * [ ] Do not start new run

## 5.3 Response Contract

Return:

```json
{
  "runId": <id>,
  "status": "STARTED"
}
```

---

# Phase 6 — Logging Improvements

* [ ] Log pipeline start
* [ ] Log pipeline completion
* [ ] Log phase durations
* [ ] Log concurrency skips
* [ ] Log failures clearly with phase name

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
