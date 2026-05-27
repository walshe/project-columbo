# Requirements: Project Colombo — v2.0 Multi-Timeframe Scan

**Defined:** 2026-05-24
**Core Value:** Give traders a clear, up-to-date view of trend signals across a crypto asset universe — across multiple timeframes — so they can make faster, better-informed decisions.

---

## v1.0 Requirements (Complete)

All 16 v1.0 requirements satisfied — see `.planning/phases/` for per-phase verification records.

| ID | Requirement | Phase | Status |
|----|-------------|-------|--------|
| CNDL-01 | Derive W1 candles from D1 rollup (Mon open → Sun close, UTC) | 1 | ✓ Complete |
| CNDL-02 | Partial weeks not stored as W1 candles | 1 | ✓ Complete |
| CNDL-03 | Rollup is incremental | 1 | ✓ Complete |
| CNDL-04 | Rollup mechanism is timeframe-generic | 1 | ✓ Complete |
| CNDL-05 | DB schema supports W1 as valid Timeframe | 1 | ✓ Complete |
| INDC-01 | SuperTrend computed on W1 | 2 | ✓ Complete |
| INDC-02 | RSI computed on W1 | 2 | ✓ Complete |
| INDC-03 | W1 indicator computation is incremental | 2 | ✓ Complete |
| SGNL-01 | Signal state detection on W1 | 2 | ✓ Complete |
| SGNL-02 | Market breadth snapshot for W1 | 2 | ✓ Complete |
| PIPE-01 | Daily pipeline includes W1 derivation + indicator + signal + pulse pass | 3 | ✓ Complete |
| PIPE-02 | D1 pipeline pass unchanged | 3 | ✓ Complete |
| PIPE-03 | Pipeline run tracking reflects W1 processing | 3 | ✓ Complete |
| API-01 | Market pulse endpoint returns W1 data | 3 | ✓ Complete |
| API-02 | Signal query endpoint supports W1 | 3 | ✓ Complete |
| API-03 | Scan endpoint supports W1 in scan conditions | 3 | ✓ Complete |

---

## v2.0 Requirements — Multi-Timeframe Cross-Condition Scan (Complete)

### Scan Condition Model

- [x] **SCAN-01**: `ScanCondition` gains an optional `timeframe` field (first record component; nullable). — Phase 4, Plan 04-01
- [x] **SCAN-02**: `ScanRequest.timeframe` becomes optional. If present, it acts as a default timeframe for conditions without their own. If absent, every condition must carry its own `timeframe`. — Phase 4, Plan 04-01
- [x] **SCAN-03**: Validation rejects any request where a condition has no resolvable timeframe. — Phase 4, Plan 04-02
- [x] **SCAN-04**: Backward compatibility — existing clients sending `ScanRequest.timeframe` with conditions that carry no per-condition `timeframe` work exactly as before. — Phase 4, Plan 04-03

### Scan Execution

- [x] **SCAN-05**: AND logic applies across all conditions regardless of which timeframe each targets. — Phase 4, Plan 04-02
- [x] **SCAN-06**: Each condition evaluated against the latest finalized close time for its own timeframe. — Phase 4, Plan 04-02
- [x] **SCAN-07**: Asset intersection (AND pruning) operates on timeframe-agnostic asset IDs. — Phase 4, Plan 04-02

### Scan Response

- [x] **SCAN-08**: `MatchedIndicator` subtypes (`SupertrendMatch`, `RsiMatch`) include a `timeframe` field. — Phase 4, Plan 04-02
- [x] **SCAN-09**: `ScanResponse` echoes back the conditions including their resolved timeframes; top-level `timeframe` is null when omitted from the request. — Phase 4, Plan 04-02
- [x] **SCAN-10**: TradingView chart URL uses the highest-granularity timeframe present among matched conditions (W1 > D1). — Phase 4, Plan 04-02

### Test Coverage

- [x] **SCAN-11**: `ScanValidatorTest` covers: condition with per-condition timeframe, fallback to request-level, rejection when neither. — Phase 4, Plan 04-03
- [x] **SCAN-12**: Integration test `shouldExecuteMultiTimeframeScan` — W1 BULLISH AND D1 BULLISH scan; correct assets returned with per-timeframe matched indicators. — Phase 4, Plan 04-03
- [x] **SCAN-13**: All pre-existing single-timeframe integration tests pass without modification. — Phase 4, Plan 04-03

---

## Out of Scope (v2.0)

| Feature | Reason |
|---------|--------|
| Summary endpoint multi-timeframe support | Excluded by design for v2.0; different response structure, lower priority |
| H4 base timeframe | Future milestone; rollup mechanism already designed to accommodate it |
| OR logic across timeframes | AND-only for now; OR within a single timeframe already works |
| Webhook / alert delivery | API-only for v2.0 |
| Frontend / UI | API-only |

---

## Traceability

| Requirement | Phase | Plan | Status |
|-------------|-------|------|--------|
| SCAN-01 | 4 | 04-01 | ✓ Complete |
| SCAN-02 | 4 | 04-01 | ✓ Complete |
| SCAN-03 | 4 | 04-02 | ✓ Complete |
| SCAN-04 | 4 | 04-03 | ✓ Complete |
| SCAN-05 | 4 | 04-02 | ✓ Complete |
| SCAN-06 | 4 | 04-02 | ✓ Complete |
| SCAN-07 | 4 | 04-02 | ✓ Complete |
| SCAN-08 | 4 | 04-02 | ✓ Complete |
| SCAN-09 | 4 | 04-02 | ✓ Complete |
| SCAN-10 | 4 | 04-02 | ✓ Complete |
| SCAN-11 | 4 | 04-03 | ✓ Complete |
| SCAN-12 | 4 | 04-03 | ✓ Complete |
| SCAN-13 | 4 | 04-03 | ✓ Complete |

**Coverage:** 13/13 v2.0 requirements complete ✓

---
*Requirements defined: 2026-05-24*
*All requirements satisfied: 2026-05-24*
