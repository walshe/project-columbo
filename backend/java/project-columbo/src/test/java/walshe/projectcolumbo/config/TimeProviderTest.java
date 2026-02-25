package walshe.projectcolumbo.config;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import static org.assertj.core.api.Assertions.assertThat;

class TimeProviderTest {

    @Test
    void shouldCalculateDaysBetween() {
        OffsetDateTime now = OffsetDateTime.of(2024, 1, 10, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime lastFlip = OffsetDateTime.of(2024, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC);

        long days = ChronoUnit.DAYS.between(lastFlip, now);
        assertThat(days).isEqualTo(5);
    }

    @Test
    void utcMidnightTodayHelperLogic() {
        OffsetDateTime now = OffsetDateTime.of(2024, 1, 10, 12, 34, 56, 0, ZoneOffset.UTC);
        OffsetDateTime midnight = now.truncatedTo(ChronoUnit.DAYS);
        
        assertThat(midnight.getHour()).isZero();
        assertThat(midnight.getMinute()).isZero();
        assertThat(midnight.getSecond()).isZero();
        assertThat(midnight.getDayOfMonth()).isEqualTo(10);
    }
}
