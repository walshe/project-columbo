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

- [ ] Verify serialization output:
  - [ ] ✅ SuperTrend → includes `daysSinceFlip`
  - [ ] ✅ RSI → includes `rsiValue` + `daysSinceCross`

- [ ] Ensure safe fallback for unknown indicator types.

### Phase 3 — Service Layer

- [ ] Update `ScanService` logic:
  - [ ] Detect indicator type per matched condition
  - [ ] Instantiate the correct subclass (`SupertrendMatch` or `RsiMatch`)
  - [ ] Map relevant fields based on indicator type
  - [ ] Preserve operator logic (AND / OR)

- [ ] Maintain backward compatibility for old scan response format.

### Phase 4 — Repository Layer

- [ ] Reuse existing `signal_state` and `indicator_rsi` data.

- [ ] Confirm queries expose required fields for mapping (`event`, `state`, `rsi_value`, `close_time`, etc.).

- [x] No schema changes required.

### Phase 5 — Controller Layer

- [ ] Update `/api/v1/scan` endpoint to return new response model.

- [ ] Add example response to OpenAPI annotations.

- [ ] Add query validation for `indicatorType` + `event`/`state` consistency.

### Phase 6 — Unit Tests

- [ ] Add serialization tests:
  - [ ] `SupertrendMatch` includes `daysSinceFlip`
  - [ ] `RsiMatch` includes `rsiValue`, `daysSinceCross`

- [ ] Add deserialization safety tests (ignore unknown indicator types).

- [ ] Add `ScanService` tests to verify correct subclass instantiation.

- [ ] Validate `operator=AND` and `operator=OR` combinations.

### Phase 7 — Integration Tests

- [ ] Seed database:
  - [ ] SuperTrend → BULLISH / BULLISH_REVERSAL
  - [ ] RSI → CROSSED_ABOVE_60

- [ ] Execute `POST /api/v1/scan` with mixed conditions.

- [ ] Assert response JSON:
  - [ ] Correct type per indicator
  - [ ] Contains indicator-specific fields
  - [ ] Preserves symbol and timeframe ordering

- [ ] Assert backward compatibility (legacy format still works).

### Phase 8 — Documentation

- [ ] Update OpenAPI spec with new polymorphic response schema.

- [ ] Add example mixed response (Supertrend + RSI).

- [ ] Add note to developer README explaining indicator-aware results.

### Phase 9 — Acceptance

- [ ] Verify:
  - [ ] ✅ Correct fields per indicator type
  - [ ] ✅ Proper JSON typing and shape
  - [ ] ✅ Backward compatibility
  - [ ] ✅ Full test suite passes
  - [ ] ✅ OpenAPI and README updates complete

- [ ] Merge to `main` branch and tag release.
