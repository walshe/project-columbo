# Story 007 — Market Pipeline Orchestration

## 1. Objective

Introduce a single deterministic market pipeline that orchestrates all daily
market computation phases in strict order.

After this story:

- Only one scheduled job exists
- Manual trigger uses the same execution path
- All phases execute sequentially
- The system guarantees a fully consistent daily market state
- `ingestion_run` represents the entire pipeline execution

This story transforms the system from independent jobs into a coordinated market engine.

---

## 2. Scope

This story:

- Introduces `MarketPipelineService`
- Removes phase-level `@Scheduled` annotations
- Adds a single scheduler entrypoint
- Ensures manual trigger invokes the full pipeline
- Integrates with ingestion run tracking
- Enforces strict phase ordering
- Defines failure semantics

This story does NOT:

- Modify candle ingestion logic
- Modify indicator math
- Modify signal detection rules
- Modify market pulse rules
- Introduce retries/backoff
- Introduce parallel execution
- Introduce distributed coordination

This is orchestration only.

---

## 3. Functional Requirements

---

## 3.1 Single Execution Entry Point

Create:

    MarketPipelineService

Primary method:

    runDaily(Provider provider, Timeframe timeframe, RunMode mode)

Default values (if not provided):

    provider = BINANCE
    timeframe = D1
    mode = INCREMENTAL

The method must execute phases in strict sequential order:

1. Candle ingestion
2. Indicator computation
3. Signal state detection
4. Market pulse aggregation

No phase may start before the previous one completes.

---

## 3.2 Remove Independent Scheduling

The following services must no longer contain `@Scheduled`:

- CandleIngestionService
- SuperTrendService (or indicator computation service)
- SignalStateService
- MarketPulseService

Only the pipeline service may be scheduled.

---

## 3.3 Single Scheduler

Add one scheduled method:

    @Scheduled(cron = "${app.market-pipeline.cron}")

This method must:

- Invoke MarketPipelineService
- Respect concurrency protection
- Use ingestion run tracking lifecycle
- Skip execution if a run is already active

Old cron properties may be deprecated:

- app.ingestion.cron
- app.signal-state.cron

---

## 3.4 Manual Trigger Integration

Manual endpoint:

    POST /internal/ingestion/run

Must now invoke:

    MarketPipelineService

It must NOT call CandleIngestionService directly.

Manual and scheduled execution must share identical orchestration logic.

If a run is already RUNNING for the same provider + timeframe:

- Return HTTP 409
- Do not start a new run

---

## 3.5 Phase Failure Handling

Pipeline behavior:

- If ingestion fails completely → mark run FAILED → stop
- If indicator phase fails → mark run FAILED → stop
- If signal detection fails → mark run FAILED → stop
- If market pulse fails → mark run FAILED

Pipeline must stop immediately on first unhandled exception.

No downstream phase may execute after a failure.

---

## 3.6 Determinism & Idempotency

Pipeline execution must be:

- Sequential
- Deterministic
- Idempotent per phase

Re-running the pipeline must:

- Not duplicate candles
- Not duplicate signal_state rows
- Not duplicate market_pulse rows
- Produce identical results for finalized candles

Each phase remains independently idempotent.

---

## 3.7 Concurrency Control

Concurrency protection remains enforced at orchestration level.

Rule:

Only one RUNNING pipeline per:

    provider + timeframe

If a RUNNING pipeline exists:

- Scheduler must skip execution
- Manual trigger must reject execution (HTTP 409)

Concurrency check must be atomic and reuse ingestion_run logic.

---

## 3.8 Finalization Boundary Consistency

All phases must respect the same finalized candle rule:

    close_time < current UTC day boundary

No phase may operate on the current open daily candle.

Boundary logic must be consistent across:

- Ingestion
- Indicator computation
- Signal detection
- Market pulse aggregation

---

## 3.9 Execution Atomicity

Each phase may use its own transaction boundary.

The pipeline does NOT require a single global transaction.

Rationale:

- Phases are independently idempotent
- Full rollback of entire pipeline is unnecessary
- Failures prevent forward progression

---

## 3.10 Observability

The `ingestion_run` record must now represent the entire pipeline execution.

It must:

- Record start and finish time
- Record status (RUNNING / SUCCESS / PARTIAL / FAILED)
- Record candle ingestion stats (minimum requirement)
- Store error_sample if failure occurs

Minimum requirement:

Preserve existing ingestion statistics and correct status classification.

---

## 4. Service Architecture

---

### 4.1 MarketPipelineService (New)

Responsibilities:

- Validate concurrency
- Create ingestion_run row (RUNNING)
- Execute phases sequentially
- Catch and classify exceptions
- Finalize ingestion_run row
- Log phase start/finish
- Release concurrency lock

This service must contain no business math.

It only coordinates services.

---

### 4.2 Existing Services (Unchanged Internally)

The following services remain:

- CandleIngestionService
- SuperTrendService
- SignalStateService
- MarketPulseService

They must remain:

- Pure
- Deterministic
- Testable independently

They must NOT:

- Trigger other phases
- Manage ingestion_run rows
- Contain scheduling annotations

---

## 5. Execution Order Guarantee

Required execution order:

1. Ingestion
2. Indicator computation
3. Signal detection
4. Market pulse aggregation

This order must never change implicitly.

If new indicators are added later,
they execute within the indicator computation phase only.

---

## 6. Non-Functional Requirements

- O(n) per asset processing
- No cross-asset locking
- No cross-timeframe coupling
- No parallel phase execution
- Clear logging of phase boundaries
- No duplicated scheduling logic
- Crash-safe (RUNNING rows remain visible)
- Re-runnable without data corruption

---

## 7. Testing Requirements

---

### 7.1 Unit Tests

- Pipeline calls phases in correct order
- Failure in any phase stops downstream phases
- Concurrency rejection works
- Manual trigger calls pipeline (not ingestion directly)

---

### 7.2 Integration Tests (Testcontainers)

- Seed assets
- Run pipeline
- Verify candles exist
- Verify signal_state exists
- Verify market_pulse exists
- Re-run pipeline → verify idempotency
- Simulate phase failure → verify stop behavior and FAILED status

---

## 8. Operational Guarantees After Implementation

After successful pipeline execution:

- All finalized daily candles are current
- All indicators are computed
- All signal states are up to date
- All market pulse states are up to date
- The system represents a fully consistent daily market state

The system can answer:

- Is the daily market state fully computed?
- Did today’s pipeline succeed?
- Is a pipeline currently running?

---

## 9. Out of Scope (Future Enhancements)

- Parallelized phase execution
- Phase-level metrics table
- Retry/backoff per phase
- Distributed cluster coordination
- Multi-timeframe orchestration
- Alerting integration
- Prometheus/OpenTelemetry metrics
- Pipeline DAG configuration
- Stale RUNNING row auto-recovery