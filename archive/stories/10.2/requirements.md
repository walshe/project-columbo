# Story 011 — Indicator-Aware Scan Response

## 1. Objective
Refine the `/api/v1/scan` response structure to return **indicator-specific metadata** rather than forcing a generic shape across all indicators.

The goal is to make each indicator’s matched result semantically meaningful —  
e.g., SuperTrend exposes *daysSinceFlip*, while RSI exposes *rsiValue* and *daysSinceCross*.

This improves clarity for client systems like OpenClaw and enables future extensibility (MACD, EMA, etc.) without breaking the schema.

---

## 2. Scope
This story:
- Adjusts the `MatchedIndicator` response model to be **indicator-aware**
- Updates query mappings to supply indicator-specific fields
- Keeps the `/api/v1/scan` endpoint contract stable (no breaking changes)
- Ensures backward compatibility for existing SuperTrend-only consumers

This story does **not**:
- Change scan logic or filtering
- Modify event/state detection
- Introduce new indicators or metrics

---

## 3. Functional Requirements

### 3.1 Unified Top-Level Format
`ScanResponse` remains unchanged:
```json
{
  "timeframe": "1D",
  "operator": "AND",
  "conditions": [...],
  "results": [ ... ],
  "generatedAt": "2026-03-03T00:00:00Z"
}

Each result:

{
  "symbol": "BTC",
  "matchedIndicators": [
    { ... }, { ... }
  ]
}
3.2 Indicator-Aware Payloads
a. SuperTrend
{
  "indicatorType": "SUPERTREND",
  "state": "BULLISH",
  "event": "BULLISH_REVERSAL",
  "daysSinceFlip": 3,
  "closeTime": "2026-03-02T00:00:00Z"
}

Fields:

state → current trend state

event → most recent flip event (if any)

daysSinceFlip → integer days since last flip

closeTime → timestamp of last state change

b. RSI
{
  "indicatorType": "RSI",
  "event": "CROSSED_ABOVE_60",
  "rsiValue": 62.4,
  "daysSinceCross": 0,
  "closeTime": "2026-03-02T00:00:00Z"
}

Fields:

event → latest cross event

rsiValue → last computed RSI value for timeframe

daysSinceCross → integer days since last cross (0 = today)

closeTime → last update time

c. Future Indicators (pattern)

Other indicators can add their own fields — e.g.:

MACD → macdValue, signalValue, histogram

EMA → emaShort, emaLong, crossType

Each indicator maps to its own schema section.

4. Implementation Plan
4.1 Model Changes

Replace generic MatchedIndicator with an interface-like sealed class or discriminated type:

sealed interface MatchedIndicator permits SupertrendMatch, RsiMatch { }

Implement:

record SupertrendMatch(IndicatorType indicatorType, TrendState state, Event event, int daysSinceFlip, OffsetDateTime closeTime) implements MatchedIndicator {}
record RsiMatch(IndicatorType indicatorType, Event event, double rsiValue, int daysSinceCross, OffsetDateTime closeTime) implements MatchedIndicator {}
4.2 Query & Mapping Updates

ScanService will still combine conditions generically,
but the MatchedIndicator construction will now branch on indicatorType:

For SUPERTREND → build SupertrendMatch

For RSI → build RsiMatch

DB queries already fetch the required source columns:

RSI’s rsi_value from indicator_rsi

SuperTrend’s state and event from signal_state

4.3 Response Assembly

ScanResponse keeps a polymorphic matchedIndicators: List<Object> (serialized by Jackson)

Use @JsonTypeInfo and @JsonSubTypes for clean API output:

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "indicatorType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = SupertrendMatch.class, name = "SUPERTREND"),
  @JsonSubTypes.Type(value = RsiMatch.class, name = "RSI")
})
5. Testing Requirements
Unit Tests

Validate correct JSON serialization per indicator

Validate correct mapping logic in ScanService

Validate that daysSinceFlip only applies to SUPERTREND

Validate that rsiValue and daysSinceCross appear for RSI

Integration Tests

Seed indicator_rsi and signal_state with both RSI and SuperTrend data

Run a combined scan

Verify API response returns:

SuperTrend indicators with daysSinceFlip

RSI indicators with rsiValue and daysSinceCross

Verify legacy client expecting daysSinceFlip field still works for SuperTrend

6. Backward Compatibility

No breaking schema changes — daysSinceFlip continues for SuperTrend

RSI and future indicators introduce new optional fields only

Existing scan consumers remain functional

7. Acceptance Criteria

 Scan results now return indicator-specific fields

 SuperTrend includes daysSinceFlip

 RSI includes rsiValue and daysSinceCross

 Unified structure preserved at top level

 Backward-compatible and validated through integration tests

 Documented in OpenAPI spec

8. Out of Scope (Future Work)

Normalizing all indicator schemas into a generic interface (not needed now)

Introducing weighted or composite scan metrics

GraphQL endpoint variant (possible future enhancement)