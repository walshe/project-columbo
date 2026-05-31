package walshe.projectcolumbo.ingestion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.CandleRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Provides data-freshness metadata for API responses.
 *
 * <p>Every API response that returns indicator or signal data includes two
 * freshness fields so consumers know exactly what they are looking at:</p>
 * <ul>
 *   <li><b>lastIngestionAt</b> — the {@code finishedAt} timestamp of the most
 *       recent successful pipeline run (tells you <i>when the pipeline ran</i>).</li>
 *   <li><b>candlesThrough</b> — the calendar date of the most recent D1 candle
 *       in the database (tells you <i>what date the data covers up to</i>).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class IngestionStatusService {

    private final IngestionRunRepository ingestionRunRepository;
    private final CandleRepository candleRepository;

    public IngestionStatusService(IngestionRunRepository ingestionRunRepository,
                                   CandleRepository candleRepository) {
        this.ingestionRunRepository = ingestionRunRepository;
        this.candleRepository = candleRepository;
    }

    /**
     * Returns the {@code finishedAt} timestamp of the most recent successful
     * ingestion run for the given provider/timeframe, or empty if none exists yet.
     */
    public Optional<OffsetDateTime> lastSuccessfulIngestionAt(MarketProvider provider, Timeframe timeframe) {
        return ingestionRunRepository
                .findFirstByProviderAndTimeframeAndStatusOrderByStartedAtDesc(provider, timeframe, IngestionRunStatus.SUCCESS)
                .map(IngestionRun::getFinishedAt);
    }

    /**
     * Convenience overload — Binance D1 is the primary candle source that all
     * daily-candle-based indicators ultimately derive from.
     */
    public Optional<OffsetDateTime> lastSuccessfulD1IngestionAt() {
        return lastSuccessfulIngestionAt(MarketProvider.BINANCE, Timeframe.D1);
    }

    /**
     * Returns the calendar date (UTC) of the most recent D1 candle stored in the
     * database, or empty if no candles have been ingested yet.
     *
     * <p>This is the "data through" date — the latest trading day whose close
     * price and derived indicators are available in any API response.</p>
     */
    public Optional<LocalDate> latestCandleDate() {
        return candleRepository.findLatestCloseTimeForTimeframe(Timeframe.D1.name())
                .map(this::toOffsetDateTime)
                .map(odt -> odt.atZoneSameInstant(ZoneOffset.UTC).toLocalDate());
    }

    /** Handles the mixed return types that PostgreSQL native queries can produce. */
    private OffsetDateTime toOffsetDateTime(Object obj) {
        if (obj instanceof Instant instant) return instant.atOffset(ZoneOffset.UTC);
        if (obj instanceof Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
        return (OffsetDateTime) obj;
    }
}
