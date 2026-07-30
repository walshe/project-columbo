## Context

The current MarketPipelineService processes all 6 phases sequentially. Phase 2 (INDICATOR) is the primary bottleneck—it iterates through all active assets and computes SuperTrend and RSI sequentially. With hundreds of assets, this can take several minutes. The subsequent phases (SIGNAL, MARKET_PULSE, W1_PROCESSING) also perform per-asset work. 

Current architecture:
- Phase 1 (INGESTION): Fetches candles from external provider
- Phase 2 (INDICATOR): Computes SuperTrend/RSI per-asset sequentially
- Phase 3 (SIGNAL): Detects signals per-asset sequentially
- Phase 4 (MARKET_PULSE): Aggregates pulse data
- Phase 5 (W1_ROLLUP): Rolls up D1→W1 candles
- Phase 6 (W1_PROCESSING): Processes W1 indicators per-asset sequentially

Critical dependency: Phase 3+ require Phase 2 complete. Phases 5-6 are mostly independent of Phases 2-4 (W1_ROLLUP only needs D1 candles from Phase 1).

## Goals / Non-Goals

**Goals:**
- Parallelize per-asset indicator computation (Phase 2) to reduce pipeline duration by 50-75%
- Ensure thread-safe concurrent database writes with transaction isolation
- Maintain data consistency and deterministic behavior under parallelization
- Design framework extensible to other per-asset phases (SIGNAL, W1_PROCESSING)
- Document phase dependencies for future optimization

**Non-Goals:**
- Distributed processing across multiple nodes
- Real-time streaming ingestion
- Changing external API or ingestion contract
- Async/reactive pipeline (stay with sync/threaded approach)

## Decisions

### 1. Spring @Async + ThreadPoolTaskExecutor for Parallelization
**Decision:** Use Spring's @Async framework with a dedicated ThreadPoolTaskExecutor bean for indicator computation.

**Rationale:** 
- Spring manages thread lifecycle and pools efficiently
- Works with existing transaction management via @Transactional
- Easy to configure pool size and rejection policies
- Well-tested in production Spring applications

**Alternatives considered:**
- Virtual threads (Java 21+): Not available in current JDK version
- ForkJoinPool: Less suitable for I/O-bound database operations
- Raw ExecutorService: Requires more boilerplate transaction handling

### 2. Per-Asset Transaction Isolation
**Decision:** Wrap each asset's indicator computation in its own @Transactional method, executed via @Async.

**Rationale:**
- Isolates writes to prevent cross-asset data corruption
- Each asset writes independently; no cross-asset dependencies in indicator tables
- Simplifies rollback—failed asset computation doesn't invalidate others
- Matches JPA's connection-per-transaction model

**Alternatives considered:**
- Single transaction spanning all assets: Higher deadlock risk, one failure fails entire phase
- No transactions: Data corruption risk under concurrent writes

### 3. Synchronization Barrier Between Phases
**Decision:** Use CompletableFuture.allOf() to synchronously await all parallel tasks before moving to next phase.

**Rationale:**
- Explicit phase boundaries—easy to understand and reason about
- No race conditions between phases
- Simplifies error handling (if any asset fails, entire phase fails)

**Alternatives considered:**
- Reactive streams: Overkill, adds complexity
- Background task coordination: Harder to track and test

### 4. Executor Pool Sizing
**Decision:** Configure ThreadPoolTaskExecutor with corePoolSize = min(asset_count / 10, 8), maxPoolSize = min(asset_count / 5, 16).

**Rationale:**
- Avoids connection pool exhaustion (typical DB pool = 20-30 connections)
- Prevents OS context-switch thrashing
- Respects database connection limits
- Scales with asset count but stays bounded

### 5. Batch per-asset processing at phase boundaries
**Decision:** Phase 2 parallelizes asset iteration; other phases batch/aggregate to reduce thread count overhead.

**Rationale:**
- Indicator computation is CPU/I/O intensive, benefits from parallelization
- Signal detection has lower per-asset cost, overhead might not justify parallelization
- W1 processing can batch by asset ID range

## Risks / Trade-offs

**[Database Connection Pool Exhaustion] → Limit executor pool size and monitor connection usage; add metrics for active connections**

**[Transaction Deadlock] → Use short transaction windows (read-process-write), avoid nested transactions; test with synthetic concurrent load**

**[Testing Complexity] → Requires thread-aware testing; use @Transactional on tests to isolate concurrent state**

**[Debugging Difficulty] → Parallel traces harder to follow; add logging with asset ID and thread name; use structured logging (MDC)**

**[Backward Compatibility] → Phase results must remain identical to sequential execution; validate via integration tests comparing parallel vs. sequential output**

**[Memory Usage] → Parallel tasks hold more in-memory state; monitor heap during peak load**

## Migration Plan

1. **Commit 1**: Create AsyncConfig bean (ThreadPoolTaskExecutor) with conservative sizing
2. **Commit 2**: Extract per-asset indicator methods; wrap in @Async + @Transactional
3. **Commit 3**: Refactor MarketPipelineService Phase 2 to collect CompletableFutures and await
4. **Commit 4**: Add integration tests for parallel vs. sequential consistency
5. **Commit 5**: Add monitoring and metrics for thread pool and database connections
6. **Rollback**: Revert to sequential by disabling @Async bean (spring.task.execution.pool.core-size=0)

## Open Questions

1. Should W1_ROLLUP (Phase 5) run in parallel with Phases 2-4 given it's mostly independent? Requires dependency analysis.
2. Once indicator phases are enabled (EMA, MACD, Thermometer), should they parallelize together or sequentially? Cost-benefit trade-off.
3. Do we need per-asset result caching, or full recomputation each run? Performance vs. correctness trade-off.
