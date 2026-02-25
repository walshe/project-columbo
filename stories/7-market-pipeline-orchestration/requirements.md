Perfect. This is the moment your system becomes an engine instead of a collection of jobs.

Below is a clean, production-oriented but still lightweight requirements document.

---

# Story 007 — Market Pipeline Orchestration

## 1. Objective

Introduce a single deterministic market pipeline that orchestrates all daily market computation phases in the correct order.

This story:

* Eliminates phase-level schedulers
* Introduces a unified pipeline orchestrator
* Ensures strict execution ordering
* Guarantees deterministic daily market state
* Integrates with ingestion run tracking

After this story:

> A single execution updates candles, indicators, signals, and market pulse in one coordinated run.

---

## 2. Scope

This story:

* Introduces `MarketPipelineService`
* Removes individual `@Scheduled` annotations from phase services
* Adds a single scheduler entrypoint
* Ensures manual trigger uses the same pipeline
* Integrates with `ingestion_run` tracking
* Enforces phase ordering

This story does NOT:

* Change candle ingestion logic
* Change indicator math
* Change signal detection logic
* Change aggregation rules
* Introduce parallel execution
* Introduce retries/backoff

This is orchestration only.

---

## 3. Functional Requirements

---

## 3.1 Single Execution Entry Point

Create:

```
MarketPipelineService
```

Primary method:

```
runDaily(provider, timeframe)
```

This method must execute phases in strict order:

1. Candle ingestion
2. Indicator computation (SuperTrend, etc.)
3. Signal state detection
4. Market pulse aggregation

Execution must be sequential.

No phase may start before the previous phase completes.

---

## 3.2 Remove Independent Scheduling

The following must no longer contain `@Scheduled`:

* CandleIngestionService
* SuperTrendService
* SignalStateService
* MarketPulseService

Only the pipeline service may be scheduled.

---

## 3.3 Single Scheduler

Add one scheduler:

```
@Scheduled(cron = "${app.market-pipeline.cron}")
```

This scheduler must:

* Invoke MarketPipelineService
* Respect concurrency protection
* Use ingestion run tracking lifecycle

---

## 3.4 Manual Trigger Integration

Manual endpoint:

```
POST /internal/ingestion/run
```

Must now invoke:

```
MarketPipelineService
```

NOT CandleIngestionService directly.

Manual and scheduled execution paths must be identical.

There must be zero duplicated orchestration logic.

---

## 3.5 Phase Failure Handling

Pipeline behavior on failure:

If ingestion fails completely:
→ mark run FAILED
→ do NOT execute subsequent phases

If ingestion succeeds but indicator phase fails:
→ mark run FAILED
→ stop pipeline

If signal detection fails:
→ mark run FAILED
→ stop pipeline

If market pulse fails:
→ mark run FAILED

Pipeline must stop immediately on first unhandled exception.

No partial downstream phase execution allowed after a failure.

---

## 3.6 Determinism

Pipeline execution must be:

* Sequential
* Deterministic
* Idempotent per phase

Re-running the pipeline must:

* Not duplicate candles
* Not duplicate signal_state rows
* Not duplicate market_pulse rows
* Preserve correctness

Each phase remains independently idempotent.

---

## 3.7 Concurrency Control

Concurrency protection remains at orchestration layer.

Rules:

Only one RUNNING pipeline per:

```
provider + timeframe
```

If a pipeline is already running:

* Scheduler must skip
* Manual trigger must return HTTP 409

This must use the existing ingestion_run concurrency logic.

---

## 3.8 Execution Atomicity

Each phase may have its own transaction boundaries.

The pipeline itself does NOT need a single global transaction.

Rationale:

* Phases are independently idempotent
* Full rollback of entire pipeline is not required
* Failures stop forward progression

---

## 3.9 Observability

The ingestion_run record must now represent the entire pipeline execution.

Run counts may aggregate:

* Candle insert/update/skip counts
* Indicator recompute counts
* Signal changes
* Market pulse changes

Minimum requirement:

* Preserve existing ingestion counts
* Mark overall status accurately

Future stories may add phase-level metrics.

---

## 4. Service Architecture

---

### 4.1 MarketPipelineService (New)

Responsibilities:

* Validate concurrency
* Create ingestion_run (RUNNING)
* Execute phases sequentially
* Catch and classify exceptions
* Finalize ingestion_run (SUCCESS / FAILED / PARTIAL)
* Log summary

This service contains no business math.

It only orchestrates services.

---

### 4.2 Existing Services (Unchanged Internally)

* CandleIngestionService
* SuperTrendService
* SignalStateService
* MarketPulseService

These remain:

* Pure
* Deterministic
* Testable independently

They must NOT:

* Trigger other phases
* Manage ingestion_run records
* Contain scheduling annotations

---

## 5. Execution Order Guarantee

Required execution order:

1. ingestion
2. indicator computation
3. signal detection
4. pulse aggregation

This order must never be changed implicitly.

If additional indicators are added later,
they execute in the indicator phase only.

---

## 6. Non-Functional Requirements

* O(n) per asset processing
* No cross-asset locking
* No cross-timeframe coupling
* No parallel phase execution (for now)
* Clear logging of phase start/finish
* No duplicated scheduling logic
* Crash-safe (RUNNING rows remain visible)

---

## 7. Testing Requirements

---

### Unit Tests

* Pipeline calls phases in correct order
* Phase failure stops downstream phases
* Concurrency rejection works
* Manual trigger calls pipeline, not ingestion directly

---

### Integration Tests

* Seed assets
* Execute pipeline
* Verify candles created
* Verify signal_state created
* Verify market_pulse created
* Re-run pipeline → verify idempotency
* Simulate failure in indicator phase → verify stop behavior

---

## 8. Operational Guarantees After This Story

After pipeline execution completes:

* All daily candles are up to date
* All indicators are recalculated
* All signal states are current
* All market pulse states are current
* System is fully consistent

The system now answers:

> "Is today's market state fully computed?"

With a single yes/no.

---

## 9. Out of Scope (Future Enhancements)

* Parallelized phase execution
* Phase-level metrics table
* Retry/backoff per phase
* Distributed cluster coordination
* Multi-timeframe orchestration
* Alerting integration
* Prometheus metrics export
* Pipeline DAG definition

---

If you want, next we can:

* Draft a clean plan.md
* Or sketch the minimal refactor steps so you don’t break anything already working.
