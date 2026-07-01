package walshe.projectcolumbo.ingestion;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.config.TimeProvider;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackfillStartValidatorTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private final TimeProvider fixedClock = () -> NOW;

    private BackfillStartValidator validatorFor(OffsetDateTime backfillStart) {
        return new BackfillStartValidator(new IngestionProperties(backfillStart), fixedClock);
    }

    @Test
    void passesWhenBackfillStartProvidesEnoughHistory() {
        // ~1 year back — well over the ~20 weekly candle (~147 day) minimum
        BackfillStartValidator validator = validatorFor(NOW.minusDays(365));

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void throwsWhenBackfillStartIsTooRecent() {
        // 10 weeks back — only ~10 weekly candles, below the 20 required
        BackfillStartValidator validator = validatorFor(NOW.minusWeeks(10));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("backfill-start");
    }

    @Test
    void throwsWhenBackfillStartIsNull() {
        BackfillStartValidator validator = validatorFor(null);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void throwsWithClearMessageWhenBackfillStartIsInFuture() {
        BackfillStartValidator validator = validatorFor(NOW.plusDays(30));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("in the future");
    }

    @Test
    void passesExactlyAtTheRequiredBoundary() {
        // 20 weekly candles (140 days) + 7 day buffer = 147 days required
        BackfillStartValidator validator = validatorFor(NOW.minusDays(147));

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
}
