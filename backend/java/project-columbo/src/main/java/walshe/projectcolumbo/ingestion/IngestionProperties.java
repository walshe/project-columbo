package walshe.projectcolumbo.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@ConfigurationProperties(prefix = "app.ingestion")
public record IngestionProperties(
    OffsetDateTime backfillStart
) {
    public Long backfillStartEpochMs() {
        return backfillStart != null ? backfillStart.toInstant().toEpochMilli() : null;
    }
}
