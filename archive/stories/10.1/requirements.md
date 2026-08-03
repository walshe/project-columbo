Story 010 — Composable Market Scan API v2 (State + Event Logic)
1. Objective

Extend the composable scan system to support indicator state (ongoing condition) in addition to indicator events (new transitions).
This enables realistic confluence scans such as:

“SuperTrend is BULLISH and RSI just CROSSED_ABOVE_60”

The goal is to allow strategy definitions that combine current market context with recent triggers while retaining composability and deterministic query logic.

2. Scope

This story:

Extends the /api/v1/scan endpoint

Adds optional state and maxDaysSinceFlip fields to each condition

Updates query logic to support both state and event

Adds awareness of current trend_state from signal_state

Adds recency filters (via maxDaysSinceFlip)

Preserves existing behavior for backward compatibility

This story does NOT:

Change indicator computations (SuperTrend, RSI, etc.)

Modify ingestion or signal generation

Introduce new indicators

Persist saved scans or scheduled strategies (future story)

3. Functional Requirements
   3.1 API Definition

POST /api/v1/scan

Example request:

{
"timeframe": "D1",
"operator": "AND",
"conditions": [
{
"indicatorType": "SUPERTREND",
"state": "BULLISH",
"maxDaysSinceFlip": 5
},
{
"indicatorType": "RSI",
"event": "CROSSED_ABOVE_60"
}
]
}

Meaning:

"Find all assets where SuperTrend is currently BULLISH (flipped in the last 5 days), and RSI just crossed above 60 today."

3.2 Condition Parameters
Field	Type	Description
indicatorType	ENUM	SUPERTREND, RSI, etc.
event	ENUM (optional)	Transition that occurred on latest finalized candle
state	ENUM (optional)	Ongoing trend or indicator condition
maxDaysSinceFlip	INT (optional)	Restrict how long ago state was established
operator	ENUM	Logical operator: AND / OR
timeframe	ENUM	Candle timeframe (e.g., D1, H4, H1)

Validation:

At least one of event or state must be present.

If both are present, both must apply for a match.

maxDaysSinceFlip requires state.

3.3 Matching Logic

For each condition:

State-based condition

Match rows in signal_state where:

trend_state = :state
AND timeframe = :timeframe

If maxDaysSinceFlip provided:

AND close_time >= (current_utc_day - interval ':maxDaysSinceFlip day')

Event-based condition

Match rows in signal_state where:

event = :event
AND close_time = latest finalized candle

Combine results via operator:

AND → intersection of matching asset_ids

OR → union of matching asset_ids

Return list of assets meeting all composite criteria.

3.4 Response Format
{
"timeframe": "D1",
"operator": "AND",
"conditions": [
{ "indicatorType": "SUPERTREND", "state": "BULLISH", "maxDaysSinceFlip": 5 },
{ "indicatorType": "RSI", "event": "CROSSED_ABOVE_60" }
],
"results": [
{
"assetSymbol": "BTCUSDT",
"matchedIndicators": [
{ "indicatorType": "SUPERTREND", "state": "BULLISH", "flippedDaysAgo": 3 },
{ "indicatorType": "RSI", "event": "CROSSED_ABOVE_60", "closeTime": "2026-03-02T00:00:00Z" }
]
}
]
}
4. Query Semantics
   Combination	Meaning
   event only	Trigger occurred on latest candle
   state only	Indicator currently in given state
   event + state	Both current state and recent trigger
   state + maxDaysSinceFlip	Ongoing condition, but limited recency
   operator=AND	All conditions must match same asset
   operator=OR	Any condition may match asset
5. Validation Rules

Ensure all provided indicatorType and event combinations are valid

Disallow maxDaysSinceFlip without state

Disallow empty conditions

Validate timeframe and operator

Reject invalid state/event ENUM combinations (e.g. RSI + BULLISH)

6. Backward Compatibility

Existing clients that only use event-based scan payloads remain valid

Default behavior unchanged for simple event-only queries

state and maxDaysSinceFlip are optional additions

7. Testing Requirements
   Unit Tests

Condition validation

State + event filtering logic

maxDaysSinceFlip boundary correctness

Operator (AND/OR) intersection logic

Invalid combinations rejected

Integration Tests

Seed signal_state with:

SUPERTREND flips 3 days ago (BULLISH)

RSI crosses today (CROSSED_ABOVE_60)

POST scan request with above conditions

Expect BTCUSDT in results

Flip dates beyond maxDaysSinceFlip → expect exclusion

Multiple assets → expect intersection filtering

8. Future Extensions

Support minDaysSinceFlip for decay-window strategies

Add relativeTo field (e.g. “event within 2 candles of another event”)

Persist “saved scans” or “named strategies”

Scheduled re-evaluation (e.g. hourly scans)

9. Summary

This enhancement transforms the /scan API from a static event filter into a context-aware strategy engine, capable of expressing:

“RSI just fired while SuperTrend still bullish”

“SuperTrend flipped within last 3 days and MACD still negative”

“Any bullish momentum event where the trend hasn’t broken yet”

It bridges signal state and event, enabling the next layer of confluence-based strategy logic.