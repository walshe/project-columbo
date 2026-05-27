-- V14__rename_enum_values_with_indicator_prefix.sql
-- Rename trend_state, signal_event, and supertrend_direction enum values
-- to include their indicator prefix for clarity.

-- trend_state
ALTER TYPE trend_state RENAME VALUE 'BULLISH'  TO 'SUPERTREND_BULLISH';
ALTER TYPE trend_state RENAME VALUE 'BEARISH'  TO 'SUPERTREND_BEARISH';
ALTER TYPE trend_state RENAME VALUE 'UNKNOWN'  TO 'SUPERTREND_UNKNOWN';
ALTER TYPE trend_state RENAME VALUE 'ABOVE_60' TO 'RSI_ABOVE_60';
ALTER TYPE trend_state RENAME VALUE 'BELOW_40' TO 'RSI_BELOW_40';
ALTER TYPE trend_state RENAME VALUE 'NEUTRAL'  TO 'RSI_NEUTRAL';

-- signal_event (NONE is shared/indicator-agnostic and stays as-is)
ALTER TYPE signal_event RENAME VALUE 'BULLISH_REVERSAL'  TO 'SUPERTREND_BULLISH_REVERSAL';
ALTER TYPE signal_event RENAME VALUE 'BEARISH_REVERSAL'  TO 'SUPERTREND_BEARISH_REVERSAL';
ALTER TYPE signal_event RENAME VALUE 'CROSSED_ABOVE_60'  TO 'RSI_CROSSED_ABOVE_60';
ALTER TYPE signal_event RENAME VALUE 'CROSSED_BELOW_40'  TO 'RSI_CROSSED_BELOW_40';

-- supertrend_direction
ALTER TYPE supertrend_direction RENAME VALUE 'UP'   TO 'SUPERTREND_UP';
ALTER TYPE supertrend_direction RENAME VALUE 'DOWN' TO 'SUPERTREND_DOWN';
