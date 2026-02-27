📘 **Story 010 — Composable Market Scan API**, fully aligned with your established story structure and conventions.

# Story 010 — Composable Market Scan API

## Phase 1 — API Contract & Request Model

* [ ] Define REST endpoint:
  `POST /api/v1/scan`
* [ ] Create request DTOs:

  * `ScanRequest`

    * `Timeframe timeframe`
    * `ScanOperator operator`
    * `List<ScanCondition> conditions`
  * `ScanCondition`

    * `IndicatorType indicatorType`
    * `Event event`
* [ ] Create response DTOs:

  * `ScanResponse`

    * `Timeframe timeframe`
    * `ScanOperator operator`
    * `List<ScanCondition> conditions`
    * `List<ScanResult> results`
  * `ScanResult`

    * `String assetSymbol`
    * `List<MatchedIndicator>` matchedIndicators
  * `MatchedIndicator`

    * `IndicatorType indicatorType`
    * `Event event`
    * `OffsetDateTime closeTime`
* [ ] Define ENUMs:

  * `ScanOperator` → `AND`, `OR`
* [ ] Implement input validation annotations (e.g. `@NotEmpty` for `conditions`)
* [ ] Add OpenAPI annotations for documentation

---

## Phase 2 — Validation Layer

* [ ] Implement `ScanValidator` service:

  * [ ] Verify `indicatorType` exists in system ENUM
  * [ ] Verify `event` is compatible with the indicator

    * `SUPERTREND` → `BULLISH_REVERSAL`, `BEARISH_REVERSAL`
    * `RSI` → `CROSSED_ABOVE_60`, `CROSSED_BELOW_40`
  * [ ] Validate `timeframe` exists in ENUM
  * [ ] Validate `operator` ∈ {`AND`, `OR`}
  * [ ] Throw `BadRequestException` for invalid inputs
* [ ] Unit test all invalid and valid combinations

---

## Phase 3 — Query Engine

* [ ] Implement `ScanService`

  * [ ] Accept validated `ScanRequest`
  * [ ] For each condition:

    * Query `signal_state` table:

      ```sql
      SELECT asset_id, indicator_type, event, close_time
      FROM signal_state
      WHERE indicator_type = :indicatorType
        AND event = :event
        AND timeframe = :timeframe
        AND close_time = (
          SELECT MAX(close_time)
          FROM signal_state
          WHERE timeframe = :timeframe
        )
      ```
  * [ ] Convert results into sets of `asset_id`
* [ ] Apply combination logic:

  * [ ] `AND` → intersection of all sets
  * [ ] `OR` → union of all sets
* [ ] Resolve `asset.symbol` for matching `asset_id`s
* [ ] Construct `ScanResult` list
* [ ] Return assembled `ScanResponse`

---

## Phase 4 — Controller Integration

* [ ] Create `ScanController`

  * `@PostMapping("/api/v1/scan")`
  * Inject `ScanValidator` and `ScanService`
  * Flow:

    1. Validate request
    2. Execute scan
    3. Return JSON response
* [ ] Add global error handler for `BadRequestException` → HTTP 400
* [ ] Log summary:

  * Operator type
  * Timeframe
  * Number of matched assets
  * Duration

---

## Phase 5 — Testing

### Unit Tests

* [ ] `ScanValidatorTest`

  * Invalid indicatorType → reject
  * Invalid event for indicator → reject
  * Empty conditions → reject
* [ ] `ScanServiceTest`

  * Single condition → returns correct assets
  * Multiple conditions (AND/OR) → correct logical behavior
  * No matches → returns empty results

### Integration Tests (Testcontainers)

* [ ] Seed `signal_state` with mixed SUPERTREND and RSI events
* [ ] POST `/api/v1/scan`:

  * AND → intersection matches
  * OR → union matches
* [ ] Verify response JSON structure
* [ ] Validate correct close_time propagation
* [ ] Validate query duration < 500ms for 1000 assets

---

## Phase 6 — Performance & Extensibility

* [ ] Optimize SQL queries using indexed columns:

  * `(indicator_type, event, timeframe, close_time)`
* [ ] Ensure result deduplication across multiple conditions
* [ ] Verify compatibility with future indicators:

  * MACD, EMA, etc.
* [ ] Confirm system behavior for large result sets (pagination optional)
* [ ] Add lightweight caching for recent scan results (optional)

---

## Deliverables

✅ `/api/v1/scan` endpoint implemented
✅ Input validation with type-safe conditions
✅ Query engine for composable AND / OR logic
✅ Unit and integration test coverage
✅ Extensible design for future indicators
✅ Performance-validated under realistic dataset

