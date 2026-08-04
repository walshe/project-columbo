package walshe.projectcolumbo.supertrend.ingestion;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Fails fast at startup if the configured backfill-start date doesn't provide enough daily
 * history for W1 (weekly) SuperTrend's ATR to warm up on rolled-up weekly candles.
 * <p>
 * W1 SuperTrend needs ~20 weekly candles to stabilize; a backfill can start mid-week, and that
 * partial first week doesn't roll up into a usable weekly candle, so the required lookback is
 * {@code 20 * 7 + 7 = 147} days, not 140.
 */
public final class BackfillStartValidator {

    private static final int MIN_WEEKLY_CANDLES_REQUIRED = 20;
    private static final int BUFFER_DAYS = 7;
    private static final long REQUIRED_LOOKBACK_DAYS = (long) MIN_WEEKLY_CANDLES_REQUIRED * 7 + BUFFER_DAYS;

    private final Clock clock;

    public BackfillStartValidator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void validate(OffsetDateTime backfillStart) {
        if (backfillStart == null) {
            throw new IllegalStateException(
                    "SUPERTREND_BACKFILL_START is not configured. It must be set far enough back to provide "
                            + "at least " + MIN_WEEKLY_CANDLES_REQUIRED + " weekly candles (" + REQUIRED_LOOKBACK_DAYS
                            + " days) for W1 SuperTrend to warm up.");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (backfillStart.isAfter(now)) {
            throw new IllegalStateException(String.format(
                    "SUPERTREND_BACKFILL_START=%s is in the future (now=%s). Set it at least %d weekly candles "
                            + "(~%d days) in the past.",
                    backfillStart, now, MIN_WEEKLY_CANDLES_REQUIRED, REQUIRED_LOOKBACK_DAYS));
        }

        long availableDays = ChronoUnit.DAYS.between(backfillStart, now);
        if (availableDays < REQUIRED_LOOKBACK_DAYS) {
            throw new IllegalStateException(String.format(
                    "SUPERTREND_BACKFILL_START=%s provides only %d days of history, but W1 SuperTrend needs at "
                            + "least %d weekly candles (~%d days) to warm up. Move it earlier.",
                    backfillStart, availableDays, MIN_WEEKLY_CANDLES_REQUIRED, REQUIRED_LOOKBACK_DAYS));
        }
    }
}
