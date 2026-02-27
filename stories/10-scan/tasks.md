# Story 010 — Composable Market Scan API — Tasks

## 🧩 Phase 1 — API Contract & DTOs

* [ ] Define REST endpoint:
  `POST /api/v1/scan`
* [ ] Create request DTOs in `walshe.projectcolumbo.scan.dto`:

  * [ ] `ScanRequest`

    * `Timeframe timeframe`
    * `ScanOperator operator`
    * `List<ScanCondition> conditions`
  * [ ] `ScanCondition`

    * `IndicatorType indicatorType`
    * `Event event`
* [ ] Create response DTOs:

  * [ ] `ScanResponse`

    * `Timeframe timeframe`
    * `ScanOperator operator`
    * `List<ScanCondition> conditions`
    * `List<ScanResult> results`
  * [ ] `ScanResult`

    * `String assetSymbol`
    * `List<MatchedIndicator> matchedIndicators`
  * [ ] `MatchedIndicator`

    * `IndicatorType indicatorType`
    * `Event event`
    * `OffsetDateTime closeTime`
* [ ] Define ENUM `ScanOperator`:

  ```java
  public enum ScanOperator { AND, OR }
  ```
* [ ] Annotate request DTOs with `@Valid`, `@NotEmpty` where appropriate
* [ ] Add OpenAPI annotations for endpoint documentation

---

## ⚙️ Phase 2 — Validation Layer

* [ ] Implement `ScanValidator` component:

  * [ ] Validate non-empty `conditions`
  * [ ] Verify each `indicatorType` exists in `indicator_type` ENUM
  * [ ] Validate that `event` is applicable to `indicatorType`

    * SUPERTREND → `BULLISH_REVERSAL`, `BEARISH_REVERSAL`
    * RSI → `CROSSED_ABOVE_60`, `CROSSED_BELOW_40`
  * [ ] Ensure `timeframe` is valid ENUM
  * [ ] Ensure `operator` ∈ {`AND`, `OR`}
  * [ ] Throw `BadRequestException` for invalid payloads
* [ ] Unit test combinations:

  * [ ] Invalid indicatorType → 400
  * [ ] Invalid event → 400
  * [ ] Empty conditions → 400

---

## 🧮 Phase 3 — Query Engine

* [ ] Implement `ScanService`:

  * [ ] Inject `SignalStateRepository` and `AssetRepository`
  * [ ] For each condition:

    * [ ] Query latest finalized `signal_state` rows:

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
  * [ ] Build map of asset_id → list of matched indicator events
  * [ ] Apply combination logic:

    * `AND` → intersection of asset_id sets
    * `OR` → union of asset_id sets
  * [ ] Resolve asset symbols for final result set
  * [ ] Construct `ScanResponse`
* [ ] Add SQL index if missing:

  * `(indicator_type, event, timeframe, close_time)`
* [ ] Unit test:

  * [ ] One condition → correct results
  * [ ] Two conditions, AND → intersection
  * [ ] Two conditions, OR → union
  * [ ] No matches → empty list

---

## 🧱 Phase 4 — Controller Integration

* [ ] Create `ScanController`:

  ```java
  @RestController
  @RequestMapping("/api/v1/scan")
  public class ScanController {
      private final ScanValidator validator;
      private final ScanService service;

      @PostMapping
      public ResponseEntity<ScanResponse> scan(@Valid @RequestBody ScanRequest request) {
          validator.validate(request);
          return ResponseEntity.ok(service.execute(request));
      }
  }
  ```
* [ ] Add logging of:

  * Operator used
  * Timeframe
  * Conditions count
  * Result count
  * Execution duration
* [ ] Add `@ControllerAdvice` to translate `BadRequestException` → 400 JSON response

---

## 🧪 Phase 5 — Testing

### ✅ Unit Tests

* [ ] `ScanValidatorTest`

  * Valid combinations accepted
  * Invalid combinations rejected
* [ ] `ScanServiceTest`

  * Simulate multiple `signal_state` entries and verify filtering
  * Verify AND/OR combination logic
  * Verify latest close_time logic

### ✅ Integration Tests (Testcontainers)

* [ ] Seed `signal_state` with mock SUPERTREND and RSI events
* [ ] POST `/api/v1/scan`:

  * Single condition → returns expected assets
  * Multiple conditions → AND/OR logic correct
* [ ] Validate:

  * HTTP 200 structure matches schema
  * Execution time < 500ms for 1000 assets
  * Empty results handled cleanly

---

## 🚀 Phase 6 — Performance & Extensibility

* [ ] Confirm SQL plan uses indexes efficiently
* [ ] Add deduplication of assets across indicators
* [ ] Confirm compatibility with future indicators (MACD, EMA, etc.)
* [ ] Verify that `ScanOperator` and `conditions` logic remains data-driven (no hardcoding)
* [ ] Optional: introduce basic caching of last scan result (by payload hash)

---

## ✅ Deliverables

* [ ] `/api/v1/scan` endpoint (JSON POST)
* [ ] DTOs + ENUMs for composable scan queries
* [ ] Validation layer for event–indicator compatibility
* [ ] Query engine with AND/OR logic
* [ ] Integration-tested controller
* [ ] Performance-tested and extensible design

---

Would you like me to include a short **follow-up design note** explaining how this scan layer can be extended later into **“saved scans”** or **“strategy definitions”** (for persistence and scheduling)? It fits naturally as Story 011.
