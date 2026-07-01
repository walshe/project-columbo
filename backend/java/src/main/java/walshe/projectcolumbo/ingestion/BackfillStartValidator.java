package walshe.projectcolumbo.ingestion;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import walshe.projectcolumbo.config.TimeProvider;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Fails application startup fast if {@code app.ingestion.backfill-start} does not provide
 * enough daily history for the enabled weekly (W1) indicators to warm up.
 *
 * <p>Weekly candles are rolled up from daily candles, so the daily backfill window bounds how
 * many weekly candles an indicator can see. If the window is too short the weekly indicators
 * emit unreliable values silently — this validator turns that into a loud boot-time failure.
 *
 * <p>Throwing from {@link PostConstruct} aborts context initialisation, so the app will not start
 * with an under-provisioned backfill window.
 */
@Component
class BackfillStartValidator {

    private static final Logger log = LoggerFactory.getLogger(BackfillStartValidator.class);

    /**
     * Weekly candles the most demanding <em>enabled</em> W1 indicator needs to warm up.
     *
     * <p>Currently this is W1 SuperTrend, whose ATR needs ~20 weekly candles to stabilise.
     * This constant tracks only the indicators that are actually enabled: if Elder Impulse /
     * Market Thermometer are reinstated, their W1 EMA-13 / MACD need ~100 weekly candles
     * (~2 years) of Wilder smoothing — raise this value and move {@code backfill-start} back
     * accordingly (see application.yaml).
     */
    private static final int MIN_WEEKLY_CANDLES_REQUIRED = 20;

    /** Small buffer so a start that lands mid-week still clears the requirement. */
    private static final int BUFFER_DAYS = 7;

    private static final long REQUIRED_LOOKBACK_DAYS =
            (long) MIN_WEEKLY_CANDLES_REQUIRED * 7 + BUFFER_DAYS;

    private final IngestionProperties ingestionProperties;
    private final TimeProvider timeProvider;

    BackfillStartValidator(IngestionProperties ingestionProperties, TimeProvider timeProvider) {
        this.ingestionProperties = ingestionProperties;
        this.timeProvider = timeProvider;
    }

    @PostConstruct
    void validate() {
        OffsetDateTime backfillStart = ingestionProperties.backfillStart();
        if (backfillStart == null) {
            throw new IllegalStateException(
                    "app.ingestion.backfill-start is not configured. It must be set far enough back to "
                            + "provide at least " + MIN_WEEKLY_CANDLES_REQUIRED + " weekly candles ("
                            + REQUIRED_LOOKBACK_DAYS + " days) for the enabled W1 indicators to warm up.");
        }

        OffsetDateTime now = timeProvider.now();
        long availableDays = ChronoUnit.DAYS.between(backfillStart, now);
        if (availableDays < REQUIRED_LOOKBACK_DAYS) {
            throw new IllegalStateException(String.format(
                    "app.ingestion.backfill-start=%s provides only %d days of history, but the enabled W1 "
                            + "indicators need at least %d weekly candles (~%d days) to warm up. "
                            + "Move backfill-start earlier.",
                    backfillStart, availableDays, MIN_WEEKLY_CANDLES_REQUIRED, REQUIRED_LOOKBACK_DAYS));
        }

        log.info("Backfill coverage OK: backfill-start={} gives {} days (>= required {} days for {} weekly candles)",
                backfillStart, availableDays, REQUIRED_LOOKBACK_DAYS, MIN_WEEKLY_CANDLES_REQUIRED);
    }
}
