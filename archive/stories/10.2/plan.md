# Story 011 — Indicator-Aware Scan Response
## plan.md

---

### Phase 1 — Model Design & Preparation
- [ ] Review current `ScanResponse` and `MatchedIndicator` models.
- [ ] Confirm serialization strategy (Jackson polymorphism).
- [ ] Introduce `MatchedIndicator` sealed interface:
  ```java
  sealed interface MatchedIndicator permits SupertrendMatch, RsiMatch {}
````

* [ ] Implement specific record classes:

  * `SupertrendMatch`
  * `RsiMatch`
* [ ] Reuse `IndicatorType` enum to drive sub-type selection.

---

### Phase 2 — JSON Serialization Configuration

* [ ] Annotate interface and subtypes:

  ```java
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "indicatorType")
  @JsonSubTypes({
      @JsonSubTypes.Type(value = SupertrendMatch.class, name = "SUPERTREND"),
      @JsonSubTypes.Type(value = RsiMatch.class, name = "RSI")
  })
  ```
* [ ] Verify JSON output is clean:

  * For `SUPERTREND`: shows `daysSinceFlip`
  * For `RSI`: shows `rsiValue` and `daysSinceCross`
* [ ] Ensure unknown indicators still serialize as generic `MatchedIndicator`.

---

### Phase 3 — Query Mapping Enhancements

* [ ] In `ScanService`, update logic building `MatchedIndicator` results:

  * If `indicatorType == SUPERTREND`:

    * Map `state`, `event`, `daysSinceFlip`, `closeTime`
    * Instantiate `SupertrendMatch`
  * If `indicatorType == RSI`:

    * Map `rsiValue`, `event`, `daysSinceCross`, `closeTime`
    * Instantiate `RsiMatch`
* [ ] Keep repository layer untouched — reuse existing columns from `signal_state` and `indicator_rsi`.
* [ ] Confirm that both indicator types can be mixed in one scan result.

---

### Phase 4 — Response Assembly

* [ ] Adjust `ScanResponse` and `ScanResult` DTOs:

  * `matchedIndicators: List<MatchedIndicator>`
  * Ensure order and type preservation.
* [ ] Validate backward compatibility with clients expecting `daysSinceFlip` (Supertrend).
* [ ] Add optional `includeDetails` flag in the future for clients that only want minimal metadata (optional).

---

### Phase 5 — Unit Tests

* [ ] Add tests for:

  * `SupertrendMatch` JSON → `daysSinceFlip` included.
  * `RsiMatch` JSON → includes `rsiValue` and `daysSinceCross`.
  * Mixed result set serialization.
  * Backward compatibility test with old clients.
* [ ] Verify deserialization safety (API only returns these, not consumes).

---

### Phase 6 — Integration Tests

* [ ] Seed database with:

  * `signal_state` rows for SuperTrend (BULLISH/BULLISH_REVERSAL)
  * `indicator_rsi` rows for RSI (CROSSED_ABOVE_60)
* [ ] Execute scan:

  ```json
  {
    "timeframe": "D1",
    "operator": "AND",
    "conditions": [
      { "indicatorType": "SUPERTREND", "state": "BULLISH" },
      { "indicatorType": "RSI", "event": "CROSSED_ABOVE_60" }
    ]
  }
  ```
* [ ] Assert:

  * Supertrend indicator includes `daysSinceFlip`
  * RSI indicator includes `rsiValue` and `daysSinceCross`
* [ ] Assert mixed response serializes correctly in the REST layer.

---

### Phase 7 — Documentation Updates

* [ ] Update `/api/v1/scan` OpenAPI schema:

  * Add indicator-specific example payloads.
  * Clarify that each `matchedIndicator` may have different fields.
* [ ] Add explanation in README:

  * “The Scan API is indicator-aware — each indicator returns fields relevant to its own domain.”
  * Include example of combined RSI + SuperTrend result.

---

### Phase 8 — Acceptance Validation

* [ ] Confirm:

  * ✅ SuperTrend results have `daysSinceFlip`
  * ✅ RSI results have `rsiValue` and `daysSinceCross`
  * ✅ JSON serialization clean and type-safe
  * ✅ No schema breaks for existing consumers
  * ✅ Full test suite passes
* [ ] Merge and version bump for API doc changes.

---

### Phase 9 — Optional Enhancements (Future)

* [ ] Introduce per-indicator metadata schema registry.
* [ ] Add support for `MACD`, `EMA`, etc. without altering `ScanResponse`.
* [ ] Extend `MatchedIndicator` to support optional display name, signal strength, or confidence metrics.
* [ ] Support `minDaysSinceFlip` or `minDaysSinceCross` as additional query filters.

---
