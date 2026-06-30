## Why

The `/ingest/run` endpoint executes a sequential pipeline with 6 phases processing market data across all active assets. Several phases process independent per-asset computations (indicators, signals) that are currently executed sequentially, creating a bottleneck. Multi-timeframe strategies need faster ingestion cycles—parallelizing independent computations within phases can reduce total pipeline duration without compromising data consistency.

## What Changes

- Parallelize indicator computation (SuperTrend, RSI, and disabled indicators) to process multiple assets concurrently within Phase 2
- Investigate parallelizing asset-level signal detection in Phase 3
- Research safe parallelization of per-asset W1 indicator processing in Phase 6
- Add synchronization and data safety guardrails to ensure correct behavior under concurrent writes
- Document phase dependencies to guide future parallelization opportunities

## Capabilities

### New Capabilities

- `parallel-indicator-computation`: Process SuperTrend and RSI calculations for multiple assets concurrently in Phase 2, with proper synchronization
- `concurrent-asset-processing`: Safe concurrent execution framework for asset-level data processing operations with transaction isolation
- `phase-dependency-mapping`: Document data flow and dependencies between pipeline phases to identify safe parallelization boundaries

### Modified Capabilities

- `market-ingestion-pipeline`: Enhance to support phase-level parallelization with safety guarantees (no changes to external API, internal sequencing becomes optional)

## Impact

- **Code**: MarketPipelineService, all per-asset computation services (SuperTrendService, RsiComputationService, etc.), transaction handling
- **Performance**: Potential 2-4x speedup on indicator computation (main bottleneck), depending on asset count and concurrency limits
- **Risk**: Requires careful testing of concurrent database writes and transaction isolation to ensure data consistency
- **Dependencies**: May leverage Spring's async/threading capabilities or custom executor pools
