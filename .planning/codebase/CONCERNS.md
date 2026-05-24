# CONCERNS.md — Technical Debt & Issues
<!-- last_mapped: 2026-05-20 -->

## Tech Debt

| # | Area | Issue | File(s) | Severity |
|---|------|--------|---------|----------|
| 1 | Rate limiting | `Thread.sleep` used for rate limiting — blocks thread, not suitable for production scale | Pipeline/fetch layer | Medium |
| 2 | Architecture | Duplicate pipeline entry points with a circular dependency between components | Pipeline entry points | High |
| 3 | Data model | Incomplete `MarketBreadthSnapshot` revision — partial implementation left in place | `MarketBreadthSnapshot` | Medium |
| 4 | Performance | RSI always performs full recalculation — no incremental/cached path | RSI calculation | Medium |
| 5 | Type safety | Unsafe `Optional<Object>` return from `CandleRepository` — loses type information | `CandleRepository` | Medium |
| 6 | Testing | Wall-clock dependency in `daysSince*` calculations makes tests time-sensitive | Date utility methods | Low |
| 7 | Error handling | `SummaryController` missing from `GlobalExceptionHandler` — exceptions won't be caught uniformly | `SummaryController`, `GlobalExceptionHandler` | Medium |
| 8 | Code quality | Dead null-check present in codebase | Various | Low |

## Known Bugs

| # | Description | Impact | File(s) |
|---|-------------|--------|---------|
| 1 | Silent drop of revised `MarketBreadthSnapshot` — revisions are silently ignored rather than applied or logged | Data integrity — market breadth revisions never persist | `MarketBreadthSnapshot` pipeline |

## Security Issues

| # | Issue | Severity | File(s) |
|---|-------|----------|---------|
| 1 | Hardcoded DB password in `application.yaml` — credentials committed to source control | **Critical** | `src/main/resources/application.yaml` |
| 2 | Unauthenticated pipeline trigger endpoint — anyone can trigger data ingestion | High | Pipeline controller |
| 3 | No authentication on any endpoint — entire API is publicly accessible | High | All controllers |

## Performance Issues

| # | Issue | Impact | File(s) |
|---|-------|--------|---------|
| 1 | N+1 query in scan RSI mapping — one query per asset instead of batch fetch | Slow scans at scale | Scan/RSI mapping layer |
| 2 | 2-query per-asset flip-time computation — not batched | Latency on large asset sets | Flip-time calculation |
| 3 | Unnecessary full-timeframe iteration in signal detection — scans all candles when only recent ones are needed | CPU waste on large datasets | Signal detection |

## Fragile Areas

| # | Issue | File(s) |
|---|-------|---------|
| 1 | Native SQL `CAST` to PostgreSQL enum — breaks if enum values change or DB migrates | Repository SQL |
| 2 | UNKNOWN-excluded ratio denominator — can produce division by zero or misleading ratios | MarketBreadth ratio calculation |
| 3 | Hard-coded scan limit — not configurable, will silently truncate results | Scan service |
| 4 | No cross-phase rollback in Supabase pipeline — partial failures leave data in inconsistent state | Supabase integration layer |

## Test Coverage Gaps

| # | Gap | Risk |
|---|-----|------|
| 1 | `MarketBreadthSnapshot` revision path — not tested | Silent data loss goes undetected |
| 2 | CoinGecko integration test disabled | API contract changes won't be caught |
| 3 | `ScanService` flip-time edge cases — not covered | Edge case bugs in production |
| 4 | `SummaryController` exception handling — not tested | Error responses untested |

## Scaling Limits

| # | Issue | Breaks At |
|---|-------|-----------|
| 1 | In-memory signal aggregation — all signals held in memory | Large asset universe |
| 2 | RSI full-replay growth — recalculation time grows linearly with history depth | Long history windows |

---

*Last mapped: 2026-05-20*
