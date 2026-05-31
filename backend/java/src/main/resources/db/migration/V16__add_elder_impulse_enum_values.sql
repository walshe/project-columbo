-- V16__add_elder_impulse_enum_values.sql
-- Additive enum extensions for Elder Impulse indicator (Phase 6).
-- No table changes needed: signal_state already carries indicator_type + trend_state + signal_event columns.

-- New indicator type
ALTER TYPE indicator_type ADD VALUE 'ELDER_IMPULSE';

-- Elder Impulse permission states
-- GREEN: 13-EMA rising AND MACD-H rising (D1) or 26-EMA rising (W1) — permission to enter long
-- RED: both falling — permission to enter short
-- NEUTRAL: diverging — stay out / manage existing positions
ALTER TYPE trend_state ADD VALUE 'IMPULSE_GREEN';
ALTER TYPE trend_state ADD VALUE 'IMPULSE_RED';
ALTER TYPE trend_state ADD VALUE 'IMPULSE_NEUTRAL';

-- Elder Impulse state transition events (prefixed IMPULSE_ per project convention)
ALTER TYPE signal_event ADD VALUE 'IMPULSE_TURNED_GREEN';
ALTER TYPE signal_event ADD VALUE 'IMPULSE_TURNED_RED';
ALTER TYPE signal_event ADD VALUE 'IMPULSE_TURNED_NEUTRAL';
