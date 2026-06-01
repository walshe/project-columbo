package walshe.projectcolumbo.api.v1.summary;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.api.v1.MarketPulseQueryService;
import walshe.projectcolumbo.api.v1.dto.MarketPulseDto;
import walshe.projectcolumbo.ingestion.IngestionStatusService;
import walshe.projectcolumbo.api.v1.scan.ScanService;
import walshe.projectcolumbo.api.v1.scan.dto.ScanCondition;
import walshe.projectcolumbo.api.v1.scan.dto.ScanOperator;
import walshe.projectcolumbo.api.v1.scan.dto.ScanRequest;
import walshe.projectcolumbo.api.v1.scan.dto.ScanResult;
import walshe.projectcolumbo.api.v1.summary.dto.ElderSummaryReport;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ElderSummaryService {

    private final MarketPulseQueryService marketPulseQueryService;
    private final ScanService scanService;
    private final IngestionStatusService ingestionStatusService;

    public ElderSummaryService(MarketPulseQueryService marketPulseQueryService,
                                ScanService scanService,
                                IngestionStatusService ingestionStatusService) {
        this.marketPulseQueryService = marketPulseQueryService;
        this.scanService = scanService;
        this.ingestionStatusService = ingestionStatusService;
    }

    public ElderSummaryReport getSummary() {

        // Breadth snapshots — no timeframe param needed by caller; Elder always uses both
        MarketPulseDto w1Pulse = marketPulseQueryService
                .getLatestPulse(Timeframe.W1, IndicatorType.ELDER_IMPULSE)
                .orElse(null);

        MarketPulseDto d1Pulse = marketPulseQueryService
                .getLatestPulse(Timeframe.D1, IndicatorType.ELDER_IMPULSE)
                .orElse(null);

        MarketPulseDto thermPulse = marketPulseQueryService
                .getLatestPulse(Timeframe.D1, IndicatorType.ELDER_THERMOMETER)
                .orElse(null);

        // Cross-timeframe alignment counts — W1+D1 in gear regardless of thermometer
        int w1d1BullAlignedCount = scanService.execute(new ScanRequest(
                ScanOperator.AND,
                List.of(
                        new ScanCondition(Timeframe.W1, IndicatorType.ELDER_IMPULSE, null,
                                TrendState.ELDER_IMPULSE_GREEN, null, null),
                        new ScanCondition(Timeframe.D1, IndicatorType.ELDER_IMPULSE, null,
                                TrendState.ELDER_IMPULSE_GREEN, null, null)
                ),
                null
        )).results().size();

        int w1d1BearAlignedCount = scanService.execute(new ScanRequest(
                ScanOperator.AND,
                List.of(
                        new ScanCondition(Timeframe.W1, IndicatorType.ELDER_IMPULSE, null,
                                TrendState.ELDER_IMPULSE_RED, null, null),
                        new ScanCondition(Timeframe.D1, IndicatorType.ELDER_IMPULSE, null,
                                TrendState.ELDER_IMPULSE_RED, null, null)
                ),
                null
        )).results().size();

        // Primary shortlist: all three Elder entry conditions must align
        List<ScanResult> primaryShortlist = scanService.execute(new ScanRequest(
                ScanOperator.AND,
                List.of(
                        new ScanCondition(Timeframe.W1, IndicatorType.ELDER_IMPULSE, null,
                                TrendState.ELDER_IMPULSE_GREEN, null, null),
                        new ScanCondition(Timeframe.D1, IndicatorType.ELDER_IMPULSE, null,
                                TrendState.ELDER_IMPULSE_GREEN, null, null),
                        new ScanCondition(Timeframe.D1, IndicatorType.ELDER_THERMOMETER, null,
                                TrendState.ELDER_THERMOMETER_QUIET, null, null)
                ),
                null
        )).results();

        // Primary bear shortlist: symmetric short setup — W1 RED + D1 RED + D1 QUIET
        List<ScanResult> primaryBearShortlist = scanService.execute(new ScanRequest(
                ScanOperator.AND,
                List.of(
                        new ScanCondition(Timeframe.W1, IndicatorType.ELDER_IMPULSE, null,
                                TrendState.ELDER_IMPULSE_RED, null, null),
                        new ScanCondition(Timeframe.D1, IndicatorType.ELDER_IMPULSE, null,
                                TrendState.ELDER_IMPULSE_RED, null, null),
                        new ScanCondition(Timeframe.D1, IndicatorType.ELDER_THERMOMETER, null,
                                TrendState.ELDER_THERMOMETER_QUIET, null, null)
                ),
                null
        )).results();

        // Fresh W1 flips to GREEN in the last 7 days — highest-conviction long setups
        List<ScanResult> freshW1GreenFlips = scanService.execute(new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.W1, IndicatorType.ELDER_IMPULSE, null,
                        TrendState.ELDER_IMPULSE_GREEN, 7, null)),
                null
        )).results();

        // Fresh W1 flips to RED in the last 7 days — tighten stops, early short setups
        List<ScanResult> freshW1RedFlips = scanService.execute(new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.W1, IndicatorType.ELDER_IMPULSE, null,
                        TrendState.ELDER_IMPULSE_RED, 7, null)),
                null
        )).results();

        // Spike alerts — assets where D1 temperature > 3× EMA (take profit, not new entries)
        List<ScanResult> spikeAlerts = scanService.execute(new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.D1, IndicatorType.ELDER_THERMOMETER, null,
                        TrendState.ELDER_THERMOMETER_SPIKE, null, null)),
                null
        )).results();

        OffsetDateTime lastIngestionAt = ingestionStatusService.lastSuccessfulD1IngestionAt().orElse(null);
        LocalDate candlesThrough = ingestionStatusService.latestCandleDate().orElse(null);

        return new ElderSummaryReport(
                w1Pulse,
                d1Pulse,
                thermPulse,
                w1d1BullAlignedCount,
                w1d1BearAlignedCount,
                primaryShortlist,
                primaryBearShortlist,
                freshW1GreenFlips,
                freshW1RedFlips,
                spikeAlerts,
                lastIngestionAt,
                candlesThrough
        );
    }
}
