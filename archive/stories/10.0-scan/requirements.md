Perfect — this is a powerful next step.
Here’s a **complete and production-grade `requirements.md`** for
📘 **Story 010 — Composable Market Scan API**, written in your usual clear and modular format.

---

# Story 010 — Composable Market Scan API

## 1. Objective

Introduce a flexible `/api/v1/scan` endpoint that allows clients to **query assets matching dynamic indicator event conditions** — e.g.
"SuperTrend just flipped bullish **AND** RSI crossed above 60."

The scan API must be composable, extensible, and type-safe — supporting arbitrary combinations of indicators and events.

This feature enables user-defined strategies via query composition rather than hard-coded logic.

---

## 2. Scope

This story:

* Adds a `/api/v1/scan` REST endpoint
* Defines a composable JSON request format
* Supports multi-indicator AND / OR logic
* Returns matched assets and associated indicator states
* Validates event–indicator compatibility

This story does **not**:

* Execute trades or alerts
* Persist scan definitions
* Evaluate indicators historically
* Support custom indicator formulas

It is purely a **read-only logical filter** over recent indicator and signal_state data.

---

## 3. Functional Requirements

### 3.1 Endpoint Definition

```
POST /api/v1/scan
Content-Type: application/json
```

Example request:

```json
{
  "timeframe": "D1",
  "operator": "AND",
  "conditions": [
    { "indicatorType": "SUPERTREND", "event": "BULLISH_REVERSAL" },
    { "indicatorType": "RSI", "event": "CROSSED_ABOVE_60" }
  ]
}
```

### 3.2 Response Format

```json
{
  "timeframe": "D1",
  "operator": "AND",
  "conditions": [
    { "indicatorType": "SUPERTREND", "event": "BULLISH_REVERSAL" },
    { "indicatorType": "RSI", "event": "CROSSED_ABOVE_60" }
  ],
  "results": [
    {
      "assetSymbol": "BTCUSDT",
      "matchedIndicators": [
        { "indicatorType": "SUPERTREND", "event": "BULLISH_REVERSAL", "closeTime": "2026-02-26T00:00:00Z" },
        { "indicatorType": "RSI", "event": "CROSSED_ABOVE_60", "closeTime": "2026-02-26T00:00:00Z" }
      ]
    },
    ...
  ]
}
```

---

## 4. Condition Semantics

### 4.1 Operators

Supported operators:

* `AND` — asset must match **all** conditions
* `OR` — asset must match **at least one** condition

Future support: nested operator trees (e.g. `AND` of multiple `OR` groups).

### 4.2 Condition Fields

| Field           | Type  | Description                                            |
| --------------- | ----- | ------------------------------------------------------ |
| `indicatorType` | ENUM  | Must exist in system ENUM (`SUPERTREND`, `RSI`, etc.)  |
| `event`         | ENUM  | Must be valid for given `indicatorType`                |
| `timeframe`     | ENUM  | e.g. `D1`, `H4` — applies to all conditions in request |
| `operator`      | ENUM  | `AND` / `OR`                                           |
| `conditions`    | Array | One or more condition objects                          |

---

## 5. Validation Rules

* All requested `indicatorType` values must exist in the `indicator_type` ENUM.
* Each `event` must be applicable to its `indicatorType`.

  * Example: `RSI` supports only `CROSSED_ABOVE_60`, `CROSSED_BELOW_40`, `NONE`
  * Example: `SUPERTREND` supports `BULLISH_REVERSAL`, `BEARISH_REVERSAL`
* `timeframe` must be a valid ENUM value.
* `operator` must be one of `AND` or `OR`.
* Empty condition arrays must return HTTP 400.

---

## 6. Query Resolution Logic

### 6.1 Base Principle

For each condition:

* Query `signal_state` table where

  * `indicator_type = ?`
  * `event = ?`
  * `timeframe = ?`
  * `close_time` = latest finalized candle (D1 boundary)

### 6.2 Evaluation

* Collect sets of matching asset_ids for each condition.
* Combine sets according to the operator:

  * `AND` → intersection
  * `OR` → union
* Fetch `asset.symbol` for matched IDs.

### 6.3 Optional Enhancements (Future)

* `since` parameter to filter by `close_time > X`
* `limit` parameter for pagination
* Include latest `rsi_value` / `supertrend_value` snapshot

---

## 7. Response Semantics

The API should return:

* Timeframe evaluated
* Operator used
* Echo of conditions evaluated
* Array of matched assets
* For each asset:

  * Matching indicator events (with close_time)

If no matches → return empty `results` array with HTTP 200.

---

## 8. Data Sources

* `signal_state` — primary source for indicator event state
* `asset` — for symbol and metadata resolution
* `market_pulse` (optional future) — if cross-indicator weighting or trend aggregation needed

---

## 9. Non-Functional Requirements

* Must execute < 500ms for 1000 assets
* Must be read-only and safe to cache
* SQL must be deterministic and idempotent
* Must gracefully handle unknown indicators (HTTP 400)
* Must be composable — new indicators should not require code changes

---

## 10. Testing Requirements

### Unit Tests

* [ ] Single condition — returns assets with matching event
* [ ] Multiple conditions with `AND` — intersection logic verified
* [ ] Multiple conditions with `OR` — union logic verified
* [ ] Invalid indicatorType → HTTP 400
* [ ] Invalid event for indicator → HTTP 400

### Integration Tests

* [ ] Seed `signal_state` with mock SUPERTREND + RSI events
* [ ] `/scan` → returns correct assets and timestamps
* [ ] Combination queries (AND / OR)
* [ ] Empty result case
* [ ] Invalid payload validation

---

## 11. Out of Scope (Future Stories)

* Strategy persistence or user-defined saved scans
* WebSocket / streaming scan results
* Historical scan over multiple days
* Weight-based scoring or confidence ranking
* AI-driven strategy generation (OpenClaw integration)

---

## 12. Example Use Cases

* “Find all assets that flipped bullish on SuperTrend today AND have RSI above 60.”
* “Find assets that are either bearish on SuperTrend OR below 40 RSI.”
* “Combine MACD crossovers with SuperTrend reversals once MACD is added.”

---

Would you like me to now generate the **matching `plan.md`** for this story (broken into phases: API contract, validation, query engine, integration, and tests)?
