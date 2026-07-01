package walshe.projectcolumbo.api.v1;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.api.v1.dto.CandleCoverageDto;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.CandleRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reports, per timeframe, how much candle history is stored and whether it is current.
 *
 * <p>The "current" determination ({@code expectedLatest} / {@code upToDate}) is delegated to
 * {@link CandleFreshnessService} so this endpoint and the read-endpoint {@code stale} flag can
 * never disagree about what counts as a finalized candle.
 */
@Service
@Transactional(readOnly = true)
public class CandleCoverageService {

    private final CandleRepository candleRepository;
    private final CandleFreshnessService freshnessService;

    public CandleCoverageService(CandleRepository candleRepository, CandleFreshnessService freshnessService) {
        this.candleRepository = candleRepository;
        this.freshnessService = freshnessService;
    }

    /** Coverage per timeframe, keyed by timeframe name (e.g. "D1", "W1"). */
    public Map<String, CandleCoverageDto> getCoverage() {
        Map<String, CandleCoverageDto> coverage = new LinkedHashMap<>();
        for (Timeframe timeframe : Timeframe.values()) {
            String tf = timeframe.name();

            OffsetDateTime earliest = candleRepository.findEarliestCloseTimeForTimeframe(tf)
                    .map(this::toOffsetDateTime).orElse(null);
            OffsetDateTime latest = candleRepository.findLatestCloseTimeForTimeframe(tf)
                    .map(this::toOffsetDateTime).orElse(null);
            long assetCount = candleRepository.countDistinctAssetsForTimeframe(tf);

            OffsetDateTime expectedLatest = freshnessService.expectedLatest(timeframe);
            boolean upToDate = freshnessService.isUpToDate(timeframe);

            coverage.put(tf, new CandleCoverageDto(earliest, latest, expectedLatest, upToDate, assetCount));
        }
        return coverage;
    }

    /** Handles the mixed return types PostgreSQL native queries can produce. */
    private OffsetDateTime toOffsetDateTime(Object obj) {
        if (obj instanceof Instant instant) return instant.atOffset(ZoneOffset.UTC);
        if (obj instanceof Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
        return (OffsetDateTime) obj;
    }
}
