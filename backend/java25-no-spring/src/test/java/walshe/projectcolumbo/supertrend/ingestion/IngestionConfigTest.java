package walshe.projectcolumbo.supertrend.ingestion;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ClearEnvironmentVariable;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spike: {@link IngestionConfig#fromEnvironment()} reads {@code System.getenv()} directly, which
 * has no supported mutation API - JUnit Pioneer's environment-variable annotations use reflection
 * to set/clear it for the duration of a test instead of refactoring the production code to accept
 * an injected env-lookup just for testability.
 */
class IngestionConfigTest {

    private static final String ENV_VAR = "SUPERTREND_BACKFILL_START";

    @Test
    @SetEnvironmentVariable(key = ENV_VAR, value = "2026-01-01T00:00:00Z")
    void parsesBackfillStartFromEnvironment() {
        IngestionConfig config = IngestionConfig.fromEnvironment();

        assertThat(config.backfillStart()).isEqualTo(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    @ClearEnvironmentVariable(key = ENV_VAR)
    void backfillStartIsNullWhenEnvironmentVariableIsUnset() {
        IngestionConfig config = IngestionConfig.fromEnvironment();

        assertThat(config.backfillStart()).isNull();
    }

    @Test
    @SetEnvironmentVariable(key = ENV_VAR, value = "   ")
    void backfillStartIsNullWhenEnvironmentVariableIsBlank() {
        IngestionConfig config = IngestionConfig.fromEnvironment();

        assertThat(config.backfillStart()).isNull();
    }
}
