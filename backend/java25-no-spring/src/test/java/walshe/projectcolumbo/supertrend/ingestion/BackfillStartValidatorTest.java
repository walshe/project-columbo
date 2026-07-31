package walshe.projectcolumbo.supertrend.ingestion;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class BackfillStartValidatorTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private final BackfillStartValidator validator = new BackfillStartValidator(Clock.fixed(Instant.from(NOW), ZoneOffset.UTC));

    @Test
    void rejectsNullBackfillStart() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void rejectsFutureBackfillStart() {
        assertThatThrownBy(() -> validator.validate(NOW.plusDays(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("in the future");
    }

    @Test
    void rejectsBackfillStartWithInsufficientHistory() {
        assertThatThrownBy(() -> validator.validate(NOW.minusDays(100)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only 100 days");
    }

    @Test
    void acceptsBackfillStartAtExactlyTheRequiredBoundary() {
        assertThatNoException().isThrownBy(() -> validator.validate(NOW.minusDays(147)));
    }

    @Test
    void acceptsBackfillStartWellInThePast() {
        assertThatNoException().isThrownBy(() -> validator.validate(NOW.minusDays(365)));
    }
}
