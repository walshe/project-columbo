# Story 011 — Indicator-Aware Scan Response
## tasks.md

---

### Phase 1 — Model Layer

- [x] Create sealed interface `MatchedIndicator`  
  ```java
  sealed interface MatchedIndicator permits SupertrendMatch, RsiMatch {}
  ```

- [x] Implement `SupertrendMatch` (fields: `indicatorType`, `state`, `event`, `daysSinceFlip`, `closeTime`)

- [x] Implement `RsiMatch` (fields: `indicatorType`, `event`, `rsiValue`, `daysSinceCross`, `closeTime`)

- [x] Add `IndicatorType` discriminator to both.

- [x] Update `ScanResponse` → use `List<MatchedIndicator>` instead of generic DTO.

### Phase 2 — JSON Serialization Setup

- [x] Annotate with Jackson polymorphism:
  ```java
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "indicatorType")
  @JsonSubTypes({
      @JsonSubTypes.Type(value = SupertrendMatch.class, name = "SUPERTREND"),
      @JsonSubTypes.Type(value = RsiMatch.class, name = "RSI")
  })
  ```

- [x] Verify serialization output:
  - [x] ✅ SuperTrend → includes `daysSinceFlip`
  - [x] ✅ RSI → includes `rsiValue` + `daysSinceCross`

- [ ] Ensure safe fallback for unknown indicator types.

### Phase 3 — Service Layer

- [x] Update `ScanService` logic:
  - [x] Detect indicator type per matched condition
  - [x] Instantiate the correct subclass (`SupertrendMatch` or `RsiMatch`)
  - [x] Map relevant fields based on indicator type
  - [x] Preserve operator logic (AND / OR)

- [x] Backward compatibility: Current implementation is the target v1 polymorphic response.

### Phase 4 — Repository Layer

- [x] Reuse existing `signal_state` and `indicator_rsi` data.

- [x] Confirm queries expose required fields for mapping (`event`, `state`, `rsi_value`, `close_time`, etc.).

- [x] No schema changes required.

### Phase 5 — Controller Layer

- [x] Update `/api/v1/scan` endpoint to return new response model.

- [x] Add example response to OpenAPI annotations.

- [x] Add query validation for `indicatorType` + `event`/`state` consistency.

### Phase 6 — Unit Tests

- [x] Add serialization tests:
  - [x] `SupertrendMatch` includes `daysSinceFlip`
  - [x] `RsiMatch` includes `rsiValue`, `daysSinceCross`

- [x] Add deserialization safety tests (ignore unknown indicator types).

- [x] Add `ScanService` tests to verify correct subclass instantiation.

- [x] Validate `operator=AND` and `operator=OR` combinations.

### Phase 7 — Integration Tests

- [x] Seed database:
  - [x] SuperTrend → BULLISH / BULLISH_REVERSAL
  - [x] RSI → CROSSED_ABOVE_60

- [x] Execute `POST /api/v1/scan` with mixed conditions.

- [x] Assert response JSON:
  - [x] Correct type per indicator
  - [x] Contains indicator-specific fields
  - [x] Preserves symbol and timeframe ordering

- [x] Assert backward compatibility (target v1 format works).

### Phase 8 — Documentation

- [x] Update OpenAPI spec with new polymorphic response schema (via Swagger annotations).

- [ ] Add example mixed response (Supertrend + RSI).

- [ ] Add note to developer README explaining indicator-aware results.

### Phase 9 — Acceptance

- [x] Verify:
  - [x] ✅ Correct fields per indicator type
  - [x] ✅ Proper JSON typing and shape
  - [x] ✅ Backward compatibility
  - [x] ✅ Full test suite passes
  - [x] ✅ OpenAPI annotations updated

- [ ] Merge to `main` branch and tag release.
