## 1. Infrastructure & Configuration

- [x] 1.1 Create AsyncConfig spring bean with ThreadPoolTaskExecutor configuration
- [x] 1.2 Add application properties for executor pool sizing (core-pool-size, max-pool-size, queue-capacity)
- [x] 1.3 Create custom annotation @ParallelAssetComputation for marking async indicator methods
- [x] 1.4 Add micrometer metrics bean to track executor pool statistics (active threads, queued tasks, completed tasks)

## 2. Refactor Indicator Services

- [x] 2.1 Extract per-asset indicator method from SuperTrendService (wrap with @Async @Transactional)
- [x] 2.2 Extract per-asset indicator method from RsiComputationService (wrap with @Async @Transactional)
- [x] 2.3 Verify no cross-asset dependencies in indicator computation
- [x] 2.4 Add error handling wrapper for async methods to capture exceptions in worker threads

## 3. Refactor MarketPipelineService Phase 2

- [x] 3.1 Modify Phase 2 INDICATOR section to collect CompletableFuturesfrom async methods
- [x] 3.2 Add synchronization barrier using CompletableFuture.allOf() before Phase 3
- [x] 3.3 Add phase-level error aggregation (partial success with failure tracking)
- [x] 3.4 Enhance logging to include thread ID and asset ID for parallel execution tracing

## 4. Transaction Isolation & Data Safety

- [x] 4.1 Ensure each indicator computation method is wrapped in its own @Transactional scope
- [x] 4.2 Add optimistic locking version field to indicator records (if not already present)
- [x] 4.3 Implement retry logic for optimistic lock conflicts (exponential backoff, max 3 retries)
- [x] 4.4 Test concurrent write behavior with synthetic load

## 5. Testing

- [x] 5.1 Create integration test comparing parallel execution output with sequential baseline
- [x] 5.2 Create concurrent load test with 100+ assets executing simultaneously
- [x] 5.3 Add deadlock detection test (test framework should timeout if deadlock occurs)
- [x] 5.4 Create test for partial failure scenario (N-1 assets succeed, 1 fails)
- [x] 5.5 Add test for executor pool size constraints (verify connections don't exceed pool size)
- [x] 5.6 Create test for configuration overrides (custom pool sizes)

## 6. Documentation & Observability

- [x] 6.1 Create phase dependency diagram documenting data flow between phases
- [x] 6.2 Document per-asset vs. aggregation operations in each phase
- [x] 6.3 Create troubleshooting guide for common parallelization issues (deadlocks, connection pool exhaustion)
- [x] 6.4 Add metrics dashboard configuration (or doc) for monitoring executor pool and database connections
- [x] 6.5 Document configuration options and recommended settings by asset count

## 7. Verification & Cleanup

- [x] 7.1 Run full integration test suite (ingestion, signal, pulse tests) to verify backward compatibility
- [x] 7.2 Verify all disabled indicator phases still work when re-enabled with parallelization
- [x] 7.3 Performance benchmark: measure speedup vs. sequential baseline
- [x] 7.4 Code review: validate thread safety and transaction isolation
- [x] 7.5 Document any breaking changes or migration steps (if any)
