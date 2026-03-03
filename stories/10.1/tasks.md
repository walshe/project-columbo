# Story 010 — Composable Market Scan API v2 (State + Event Logic)
## Tasks

---

### Phase 1 — DTOs & API Contract
- [ ] Create DTOs:
  - `ScanRequest`
  - `ScanCondition`
  - `ScanResponse`
  - `ScanResult`
  - `MatchedIndicator`
- [ ] Add enum `ScanOperator` with values `AND`, `OR`
- [ ] Reuse existing enums: `IndicatorType`, `Event`, `TrendState`, `Timeframe`
- [ ] Update OpenAPI/Swagger documentation with example payloads for:
  - Event-only
  - State-only
  - Event + State + maxDaysSinceFlip

---

### Phase 2 — Validation Layer
- [ ] Implement `ScanValidator` component
- [ ] Validation rules:
  - `conditions` must be non-empty
  - `indicatorType` must be valid
  - `event` and `state` must belong to valid enum values
  - At least one of `event` or `state` must be present
  - If `maxDaysSinceFlip` is provided, `state` must also be provided
  - `operator` must be `AND` or `OR`
- [ ] Maintain a mapping: `indicatorType → allowed events/states`
- [ ] Throw `BadRequestException` with clear structured error body on invalid input
- [ ] Unit tests:
  - Invalid indicator/event/state combos
  - Missing required fields
  - Valid permutations accepted

---

### Phase 3 — Repository Layer
- [ ] Extend or create `SignalStateRepositoryCustom`
- [ ] Add:
  - `findEventMatches(indicatorType, event, timeframe, latestCloseTime)`
  - `findStateMatches(indicatorType, state, timeframe, maxDaysSinceFlip)`
- [ ] Implement SQL queries:
  - Event match → filter on latest finalized candle
  - State match → latest trend_state per asset (optionally with recency filter)
- [ ] Return columns:
  - `asset_id`
  - `indicator_type`
  - `trend_state` or `event`
  - `close_time`
- [ ] Add repository-level tests (H2 or PostgreSQL Testcontainers)

---

### Phase 4 — Service Layer
- [ ] Create or extend `ScanService`
- [ ] Implement method `execute(ScanRequest request)`:
  1. Validate via `ScanValidator`
  2. Execute queries for each condition
  3. Build map `asset_id -> list<MatchedIndicator>`
  4. Apply AND/OR logic for intersection or union
  5. Compute `flippedDaysAgo` for state results
  6. Resolve `asset_symbol` using `AssetRepository`
  7. Construct and return `ScanResponse`
- [ ] Implement `limit` handling with deterministic ordering (`flippedDaysAgo` or `close_time`)
- [ ] Log:
  - operator
  - timeframe
  - condition summary
  - result count
  - elapsed_ms
- [ ] Unit tests:
  - Intersection/union logic
  - Mixed event+state conditions
  - Limit enforcement

---

### Phase 5 — Controller
- [ ] Create `ScanController` with:
  - `@RestController`
  - `@PostMapping("/api/v1/scan")`
- [ ] Inject `ScanService`
- [ ] Return `ResponseEntity<ScanResponse>`
- [ ] Implement exception handling:
  - `BadRequestException` → HTTP 400
  - Other exceptions → HTTP 500
- [ ] Logging for:
  - Request summary
  - Result count
- [ ] Integration tests (MockMvc):
  - Valid request → 200 OK
  - Invalid request → 400 Bad Request
  - Empty results → 200 with empty results array

---

### Phase 6 — Integration Tests (End-to-End)
- [ ] Use PostgreSQL Testcontainers
- [ ] Seed tables:
  - `asset`
  - `signal_state`
- [ ] Scenarios:
  - **Asset A**: SuperTrend flipped bullish 3 days ago → state = BULLISH  
  - **Asset B**: RSI crossed above 60 today → event = CROSSED_ABOVE_60  
  - **Asset C**: SuperTrend flipped bullish 10 days ago → state = BULLISH
- [ ] Execute request:
  ```json
  {
    "timeframe": "D1",
    "operator": "AND",
    "conditions": [
      { "indicatorType": "SUPERTREND", "state": "BULLISH", "maxDaysSinceFlip": 5 },
      { "indicatorType": "RSI", "event": "CROSSED_ABOVE_60" }
    ]
  }
  ```
- [ ] Expected: Only Asset A is returned.
- [ ] Test variants:
  - AND vs OR logic
  - maxDaysSinceFlip omitted
  - Event-only and state-only conditions
- [ ] Validate:
  - Correct flippedDaysAgo calculation
  - Correct close_time propagation
  - Consistent deterministic ordering

---

### Phase 7 — Performance & Optimization
- [ ] Ensure DB indexes:
  - (indicator_type, event, timeframe, close_time)
  - (indicator_type, trend_state, timeframe, close_time)
- [ ] Add caching for latest finalized close_time per timeframe (optional)
- [ ] Verify query count (avoid N+1)
- [ ] Profile performance with 1000+ signal_state rows
- [ ] Target: < 500 ms per scan request

---

### Phase 8 — Documentation
- [ ] Update API documentation:
  - /api/v1/scan request/response examples
  - Explain difference between event, state, maxDaysSinceFlip
  - Describe AND/OR semantics
- [ ] Add OpenAPI schema annotations
- [ ] Add example strategy definitions (e.g. SuperTrend + RSI)
- [ ] Update README or API reference section

---

### Phase 9 — Acceptance Criteria
- [ ] /api/v1/scan accepts both state and event conditions
- [ ] Logical combination (AND/OR) behaves correctly
- [ ] maxDaysSinceFlip respected when provided
- [ ] Validation prevents invalid indicator/event/state combos
- [ ] Full unit and integration test suite passes
- [ ] Performance target met (< 500 ms on realistic data)
- [ ] Backward-compatible with event-only clients
- [ ] Documentation published and verified

---

### Optional Enhancements (Future)
- [ ] Add minDaysSinceFlip for flexible look-back windows
- [ ] Add NOT operator for negation
- [ ] Add nested condition groups (logical trees)
- [ ] Cache frequent scan results by request hash
- [ ] Add async scan execution for large datasets