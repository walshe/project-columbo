# Story 005 — REST API Layer

## 1. Objective

Expose read-only REST endpoints that allow external consumers to query:

* Asset-level indicator state
* Asset-level flip history (latest flip)
* Market pulse (breadth snapshot)
* Time since flip
* Filtered bullish/bearish asset lists

This story introduces the public query layer.

It does NOT introduce:

* Authentication
* Authorization
* WebSockets
* Trading execution
* Strategy definitions
* UI layer

This is a pure read-only API.

---

## 2. Scope

This story:

* Reads from:

    * `signal_state`
    * `market_pulse`
    * `asset`
* Returns structured JSON DTOs
* Supports filtering and sorting
* Is fully deterministic

This story does NOT:

* Trigger recalculation
* Modify data
* Expose write endpoints

---

## 3. Functional Requirements

---

## 3.1 Get Current Signal State per Asset

### Endpoint

```
GET /api/v1/signals
```

### Query Parameters

| Param         | Required | Description                    |
| ------------- | -------- | ------------------------------ |
| timeframe     | YES      | e.g. D1                        |
| indicatorType | YES      | e.g. SUPERTREND                |
| state         | NO       | BULLISH or BEARISH             |
| sort          | NO       | LAST_FLIP_ASC / LAST_FLIP_DESC |

### Behavior

Returns one row per active asset representing:

* Latest finalized signal_state
* Current trend_state
* last_flip_time
* time_since_flip (computed server-side)

Only finalized rows must be returned.

---

### Response Example

```json
[
  {
    "asset": "BTC",
    "timeframe": "D1",
    "indicatorType": "SUPERTREND",
    "trendState": "BULLISH",
    "lastFlipTime": "2026-02-12T00:00:00Z",
    "timeSinceFlipDays": 8
  }
]
```

---

## 3.2 Get Assets by Trend State

### Endpoint

```
GET /api/v1/assets/by-state
```

### Query Parameters

| Param         | Required                |
| ------------- | ----------------------- |
| timeframe     | YES                     |
| indicatorType | YES                     |
| state         | YES (BULLISH / BEARISH) |

### Behavior

Returns list of assets currently in that state.

Must only return:

* Active assets
* Latest finalized state

---

## 3.3 Get Market Pulse Snapshot

### Endpoint

```
GET /api/v1/market-pulse
```

### Query Parameters

| Param         | Required |
| ------------- | -------- |
| timeframe     | YES      |
| indicatorType | YES      |

### Behavior

Returns latest available snapshot from `market_pulse`.

---

### Response Example

```json
{
  "timeframe": "D1",
  "indicatorType": "SUPERTREND",
  "snapshotCloseTime": "2026-02-20T00:00:00Z",
  "bullishCount": 78,
  "bearishCount": 22,
  "totalAssets": 100,
  "bullishRatio": 0.78
}
```

---

## 3.4 Get Historical Market Pulse

### Endpoint

```
GET /api/v1/market-pulse/history
```

### Query Parameters

| Param         | Required |
| ------------- | -------- |
| timeframe     | YES      |
| indicatorType | YES      |
| from          | NO       |
| to            | NO       |

### Behavior

Returns ordered list (oldest → newest).

Must be deterministic and sorted by snapshotCloseTime.

---

## 4. Time Since Flip Logic

`timeSinceFlipDays` is calculated as:

```
(now UTC - last_flip_time) in whole days
```

Rules:

* Must use UTC
* Must not rely on client timezone
* Only for finalized states

---

## 5. Sorting Requirements

Sorting must support:

* lastFlipTime ascending
* lastFlipTime descending
* asset symbol ascending

Sorting must be stable and deterministic.

---

## 6. DTO Layer

Entities must NOT be exposed directly.

Create DTOs:

* SignalStateDto
* MarketPulseDto
* MarketPulseHistoryDto

Map via explicit mapper (no direct entity serialization).

---

## 7. Error Handling

Invalid inputs must return:

```
400 Bad Request
```

Examples:

* Invalid timeframe
* Invalid indicatorType
* Invalid enum values

If no data found:

* Return 200 with empty list
* Never return 500

---

## 8. Performance Requirements

* All endpoints must be O(n)
* No per-asset database queries inside loops
* Use bulk queries only
* Use proper indexing:

    * signal_state (asset_id, timeframe, indicator_type, close_time)
    * market_pulse (timeframe, indicator_type, snapshot_close_time)

---

## 9. Non-Functional Requirements

* Read-only
* Deterministic
* Stateless
* No caching required (future story)
* No pagination required (small universe assumed)
* Ready for additional indicators

---

## 10. Testing Requirements

### Unit Tests

* timeSinceFlip calculation
* DTO mapping
* sorting behavior

### Integration Tests

* Seed assets + signal_state
* Call /signals
* Assert returned state correct
* Call /market-pulse
* Assert snapshot matches DB
* Call history endpoint
* Assert sorted order

---

## 11. Out of Scope (Future Stories)

* Authentication
* API keys
* Rate limiting
* Strategy endpoints
* Composite indicator filtering (e.g. SuperTrend + RSI)
* Confidence scoring
* WebSocket streaming
