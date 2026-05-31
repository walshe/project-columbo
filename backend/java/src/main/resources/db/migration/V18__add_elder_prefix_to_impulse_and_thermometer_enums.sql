-- V18: Add ELDER_ prefix to Elder Impulse and Market Thermometer enum values.
--
-- Rationale: API consumers see these values in filter dropdowns and need to
-- know at a glance which indicator they belong to.  The prefix makes the
-- relationship unambiguous (e.g. ELDER_IMPULSE_GREEN vs SUPERTREND_BULLISH).
--
-- Affects three PostgreSQL enum types:
--   trend_state   — 6 values (3 impulse + 3 thermometer)
--   indicator_type — 1 value (MARKET_THERMOMETER → ELDER_THERMOMETER)
--   signal_event  — 6 values (3 impulse events + 3 thermometer events)
--
-- All existing rows in signal_state and market_breadth_snapshot that carry
-- these values are automatically updated by PostgreSQL when the enum label
-- is renamed — no UPDATE statements required.

-- trend_state
ALTER TYPE trend_state RENAME VALUE 'IMPULSE_GREEN'           TO 'ELDER_IMPULSE_GREEN';
ALTER TYPE trend_state RENAME VALUE 'IMPULSE_RED'             TO 'ELDER_IMPULSE_RED';
ALTER TYPE trend_state RENAME VALUE 'IMPULSE_NEUTRAL'         TO 'ELDER_IMPULSE_NEUTRAL';
ALTER TYPE trend_state RENAME VALUE 'THERMOMETER_QUIET'       TO 'ELDER_THERMOMETER_QUIET';
ALTER TYPE trend_state RENAME VALUE 'THERMOMETER_HOT'         TO 'ELDER_THERMOMETER_HOT';
ALTER TYPE trend_state RENAME VALUE 'THERMOMETER_SPIKE'       TO 'ELDER_THERMOMETER_SPIKE';

-- indicator_type
ALTER TYPE indicator_type RENAME VALUE 'MARKET_THERMOMETER'   TO 'ELDER_THERMOMETER';

-- signal_event
ALTER TYPE signal_event RENAME VALUE 'IMPULSE_TURNED_GREEN'          TO 'ELDER_IMPULSE_TURNED_GREEN';
ALTER TYPE signal_event RENAME VALUE 'IMPULSE_TURNED_RED'            TO 'ELDER_IMPULSE_TURNED_RED';
ALTER TYPE signal_event RENAME VALUE 'IMPULSE_TURNED_NEUTRAL'        TO 'ELDER_IMPULSE_TURNED_NEUTRAL';
ALTER TYPE signal_event RENAME VALUE 'THERMOMETER_CROSSED_ABOVE_EMA' TO 'ELDER_THERMOMETER_CROSSED_ABOVE_EMA';
ALTER TYPE signal_event RENAME VALUE 'THERMOMETER_CROSSED_BELOW_EMA' TO 'ELDER_THERMOMETER_CROSSED_BELOW_EMA';
ALTER TYPE signal_event RENAME VALUE 'THERMOMETER_TRIPLE_SPIKE'      TO 'ELDER_THERMOMETER_TRIPLE_SPIKE';
