# Requirements: Project Colombo — Weekly Timeframe

**Defined:** 2026-05-20
**Core Value:** Give traders a clear, up-to-date view of trend signals across a crypto asset universe — across multiple timeframes

## v1 Requirements

### Candle Derivation

- [x] **CNDL-01**: System derives W1 candles by rolling up D1 candles (Monday open → Sunday close, UTC week boundaries)
- [x] **CNDL-02**: Partial weeks (current incomplete week) are not stored as W1 candles — only complete weeks
- [x] **CNDL-03**: Rollup is incremental — only derives new W1 candles from D1 candles added since last rollup
- [x] **CNDL-04**: Rollup mechanism is timeframe-generic (not hardcoded to D1→W1) so H4→D1 and H4→W1 can reuse it later
- [x] **CNDL-05**: DB schema supports W1 as a valid `Timeframe` value (migration added)

### Indicators

- [ ] **INDC-01**: SuperTrend (ATR 10, multiplier 2.0) is computed on W1 candles for all active assets
- [ ] **INDC-02**: RSI (period 14, Wilder's smoothing) is computed on W1 candles for all active assets
- [ ] **INDC-03**: Indicator computation on W1 is incremental — only computes new candles, not full recalculation

### Signals & Market Pulse

- [ ] **SGNL-01**: Signal state (BULLISH/BEARISH/UNKNOWN + cross events) is detected on W1
- [ ] **SGNL-02**: Market breadth snapshot is computed for W1 (% bullish, % bearish, % unknown)

### Pipeline Integration

- [ ] **PIPE-01**: Daily pipeline (`MarketPipelineService.runDaily()`) includes a W1 derivation + indicator + signal + pulse pass after the D1 pass
- [ ] **PIPE-02**: D1 pipeline pass is unchanged — W1 is additive, not a replacement
- [ ] **PIPE-03**: Pipeline run tracking (`IngestionRun`) reflects W1 processing

### API

- [ ] **API-01**: Market pulse endpoint (`GET /api/v1/market-pulse`) returns W1 data when `timeframe=W1` is requested
- [ ] **API-02**: Signal query endpoint supports `timeframe=W1`
- [ ] **API-03**: Scan endpoint supports `timeframe=W1` in scan conditions

## v2 Requirements

### H4 Base Timeframe

- **H4-01**: System ingests H4 candles from Binance
- **H4-02**: D1 candles derived from H4 rollup (replacing direct Binance fetch)
- **H4-03**: W1 candles derived from H4 rollup via same mechanism

### Additional Indicators on W1

- **IND-V2-01**: Additional indicators added to W1 when introduced for D1

## Out of Scope

| Feature | Reason |
|---------|--------|
| Fetching W1 candles directly from Binance | Rolled-up from D1 for consistency; avoids week-boundary mismatch with exchange |
| Frontend/UI for W1 data | API-only; UI is separate future work |
| Real-time W1 updates | Daily pipeline only |
| H4 timeframe in this milestone | Future work; rollup mechanism designed to accommodate it |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| CNDL-01 | Phase 1 | Complete |
| CNDL-02 | Phase 1 | Complete |
| CNDL-03 | Phase 1 | Complete |
| CNDL-04 | Phase 1 | Complete |
| CNDL-05 | Phase 1 | Complete |
| INDC-01 | Phase 2 | Pending |
| INDC-02 | Phase 2 | Pending |
| INDC-03 | Phase 2 | Pending |
| SGNL-01 | Phase 2 | Pending |
| SGNL-02 | Phase 2 | Pending |
| PIPE-01 | Phase 3 | Pending |
| PIPE-02 | Phase 3 | Pending |
| PIPE-03 | Phase 3 | Pending |
| API-01 | Phase 3 | Pending |
| API-02 | Phase 3 | Pending |
| API-03 | Phase 3 | Pending |

**Coverage:**
- v1 requirements: 16 total
- Mapped to phases: 16
- Unmapped: 0 ✓

---
*Requirements defined: 2026-05-20*
*Last updated: 2026-05-20 after initial definition*
