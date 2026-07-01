package walshe.projectcolumbo.api.v1;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.config.TimeProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.service.CandleFilters;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Single source of truth for "does the system have the most recent finalized candle for timeframe X?".
 *
 * <p>Used both by the candle coverage endpoint and by the {@code stale} flag / {@code requireFresh}
 * gate on the read endpoints, so all three can never disagree about what "current" means. "Finalized"
 * is defined by the same {@link CandleFilters#utcMidnightToday} boundary the ingestion pipeline uses.
 */
@Service
@Transactional(readOnly = true)
public class CandleFreshnessService {

    /**
     * How long after a period boundary {@code requireFresh} stays lenient. The daily pipeline runs at
     * 00:05 UTC, so right after midnight the just-finalized candle legitimately isn't ingested yet;
     * without this grace, {@code requireFresh} would 503 every day during that window. Sized to
     * comfortably absorb a delayed run without masking a genuinely dead pipeline. Note this only
     * affects {@code requireFresh} enforcement — the {@code stale} flag stays literal.
     */
    private static final Duration REQUIRE_FRESH_GRACE = Duration.ofHours(6);

    private final CandleRepository candleRepository;
    private final TimeProvider timeProvider;

    public CandleFreshnessService(CandleRepository candleRepository, TimeProvider timeProvider) {
        this.candleRepository = candleRepository;
        this.timeProvider = timeProvider;
    }

    /** Start of the most recent finalized period for this timeframe as of now. */
    public OffsetDateTime expectedLatest(Timeframe timeframe) {
        OffsetDateTime boundary = CandleFilters.utcMidnightToday(timeProvider.now());
        return boundary.minusDays(periodDays(timeframe));
    }

    /** True when the latest stored candle has reached the most recent finalized period. */
    public boolean isUpToDate(Timeframe timeframe) {
        OffsetDateTime latest = latestClose(timeframe);
        return latest != null && !latest.isBefore(expectedLatest(timeframe));
    }

    /**
     * True when the timeframe is stale AND we are past the grace window since the period boundary —
     * i.e. genuinely behind rather than merely awaiting the normal post-boundary ingestion run.
     * This is the condition {@code requireFresh} enforces (503).
     */
    public boolean isStaleBeyondGrace(Timeframe timeframe) {
        if (isUpToDate(timeframe)) {
            return false;
        }
        OffsetDateTime boundary = CandleFilters.utcMidnightToday(timeProvider.now());
        return timeProvider.now().isAfter(boundary.plus(REQUIRE_FRESH_GRACE));
    }

    private OffsetDateTime latestClose(Timeframe timeframe) {
        return candleRepository.findLatestCloseTimeForTimeframe(timeframe.name())
                .map(this::toOffsetDateTime)
                .orElse(null);
    }

    private long periodDays(Timeframe timeframe) {
        return switch (timeframe) {
            case D1 -> 1;
            case W1 -> 7;
        };
    }

    /** Handles the mixed return types PostgreSQL native queries can produce. */
    private OffsetDateTime toOffsetDateTime(Object obj) {
        if (obj instanceof Instant instant) return instant.atOffset(ZoneOffset.UTC);
        if (obj instanceof Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
        return (OffsetDateTime) obj;
    }
}
