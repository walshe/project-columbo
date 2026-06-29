## ADDED Requirements

### Requirement: Phase dependencies are documented
The system SHALL document data dependencies between pipeline phases to identify safe parallelization opportunities and prevent out-of-order execution.

#### Scenario: Dependency diagram is created
- **WHEN** documentation is generated
- **THEN** a clear diagram shows which phases depend on outputs from other phases

#### Scenario: Phase 1 has no dependencies
- **WHEN** describing Phase 1 (INGESTION)
- **THEN** documentation confirms Phase 1 has no upstream dependencies and outputs D1 candles for all assets

#### Scenario: Phase 2 depends on Phase 1
- **WHEN** describing Phase 2 (INDICATOR)
- **THEN** documentation confirms Phase 2 depends on Phase 1 candles and outputs indicator records per asset

#### Scenario: Phase 3 depends on Phase 2
- **WHEN** describing Phase 3 (SIGNAL)
- **THEN** documentation confirms Phase 3 depends on Phase 2 indicators (SuperTrend, RSI) and outputs signal state

#### Scenario: Phase 4 depends on Phase 3
- **WHEN** describing Phase 4 (MARKET_PULSE)
- **THEN** documentation confirms Phase 4 aggregates Phase 3 signals and outputs pulse state

#### Scenario: Phase 5 is mostly independent
- **WHEN** describing Phase 5 (W1_ROLLUP)
- **THEN** documentation confirms Phase 5 depends only on Phase 1 candles, not on Phases 2-4

#### Scenario: Phase 6 depends on Phase 5
- **WHEN** describing Phase 6 (W1_PROCESSING)
- **THEN** documentation confirms Phase 6 depends on Phase 5 rolled-up candles and outputs W1 indicators/signals

### Requirement: Per-asset vs. aggregation dependencies are distinguished
The system SHALL clarify which operations are per-asset (parallelizable) and which require global aggregation (sequential).

#### Scenario: SuperTrend computation is per-asset
- **WHEN** examining Phase 2
- **THEN** documentation identifies SuperTrend as per-asset parallelizable operation

#### Scenario: Market Pulse aggregation is global
- **WHEN** examining Phase 4
- **THEN** documentation identifies Market Pulse as global aggregation (not per-asset parallelizable)

#### Scenario: Data flow document includes asset counts
- **WHEN** dependency diagram is presented
- **THEN** it shows how asset count affects phase duration (e.g., Phase 2 scales with assets; Phase 4 does not)

### Requirement: Future parallelization opportunities are identified
The system SHALL highlight phases and sub-operations that could benefit from parallelization in future work.

#### Scenario: Potential parallelization candidates are listed
- **WHEN** dependency analysis is complete
- **THEN** a list of candidates for future optimization (e.g., W1_ROLLUP in parallel with Phase 2, per-asset SIGNAL detection) is documented

#### Scenario: Risks for each candidate are noted
- **WHEN** candidates are listed
- **THEN** each includes known risks (e.g., deadlock potential, memory overhead) and estimated effort
