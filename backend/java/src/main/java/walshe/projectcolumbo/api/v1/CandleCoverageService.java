package walshe.projectcolumbo.api.v1;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.api.v1.dto.CandleCoverageDto;
import walshe.projectcolumbo.config.TimeProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.service.CandleFilters;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reports, per timeframe, how much candle history is stored and whether it is current.
 *
 * <p>"Current" is defined against the same finalized boundary the ingestion pipeline uses
 * ({@link CandleFilters#utcMidnightToday}), so this endpoint cannot disagree with ingestion
 * about what counts as a finalized candle.
 */
@Service
@Transactional(readOnly = true)
public class CandleCoverageService {

    private final CandleRepository candleRepository;
    private final TimeProvider timeProvider;

    public CandleCoverageService(CandleRepository candleRepository, TimeProvider timeProvider) {
        this.candleRepository = candleRepository;
        this.timeProvider = timeProvider;
    }

    /** Coverage per timeframe, keyed by timeframe name (e.g. "D1", "W1"). */
    public Map<String, CandleCoverageDto> getCoverage() {
        OffsetDateTime boundary = CandleFilters.utcMidnightToday(timeProvider.now());

        Map<String, CandleCoverageDto> coverage = new LinkedHashMap<>();
        for (Timeframe timeframe : Timeframe.values()) {
            String tf = timeframe.name();

            OffsetDateTime earliest = candleRepository.findEarliestCloseTimeForTimeframe(tf)
                    .map(this::toOffsetDateTime).orElse(null);
            OffsetDateTime latest = candleRepository.findLatestCloseTimeForTimeframe(tf)
                    .map(this::toOffsetDateTime).orElse(null);
            long assetCount = candleRepository.countDistinctAssetsForTimeframe(tf);

            // Start of the most recent finalized period. latest at or after this means the most
            // recently finalized candle is present; the period-start threshold avoids fragile
            // equality against the exact stored close time (e.g. 23:59:59.999).
            OffsetDateTime expectedLatest = boundary.minusDays(periodDays(timeframe));
            boolean upToDate = latest != null && !latest.isBefore(expectedLatest);

            coverage.put(tf, new CandleCoverageDto(earliest, latest, expectedLatest, upToDate, assetCount));
        }
        return coverage;
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
