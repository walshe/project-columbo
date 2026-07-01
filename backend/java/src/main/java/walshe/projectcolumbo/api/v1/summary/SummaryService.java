package walshe.projectcolumbo.api.v1.summary;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.api.v1.CandleFreshnessService;
import walshe.projectcolumbo.api.v1.MarketPulseQueryService;
import walshe.projectcolumbo.api.v1.SignalQueryService;
import walshe.projectcolumbo.ingestion.IngestionStatusService;
import walshe.projectcolumbo.api.v1.dto.MarketPulseDto;
import walshe.projectcolumbo.api.v1.dto.SignalSort;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.scan.ScanService;
import walshe.projectcolumbo.api.v1.scan.dto.ScanCondition;
import walshe.projectcolumbo.api.v1.scan.dto.ScanOperator;
import walshe.projectcolumbo.api.v1.scan.dto.ScanRequest;
import walshe.projectcolumbo.api.v1.scan.dto.ScanResult;
import walshe.projectcolumbo.api.v1.summary.dto.SummaryReport;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SummaryService {

    private final SignalQueryService signalQueryService;
    private final MarketPulseQueryService marketPulseQueryService;
    private final ScanService scanService;
    private final IngestionStatusService ingestionStatusService;
    private final CandleFreshnessService freshnessService;

    public SummaryService(SignalQueryService signalQueryService,
                          MarketPulseQueryService marketPulseQueryService,
                          ScanService scanService,
                          IngestionStatusService ingestionStatusService,
                          CandleFreshnessService freshnessService) {
        this.signalQueryService = signalQueryService;
        this.marketPulseQueryService = marketPulseQueryService;
        this.scanService = scanService;
        this.ingestionStatusService = ingestionStatusService;
        this.freshnessService = freshnessService;
    }

    public SummaryReport getSummary(Timeframe timeframe) {
        IndicatorType indicatorType = IndicatorType.SUPERTREND;
        MarketPulseDto pulse = marketPulseQueryService.getLatestPulse(timeframe).orElse(null);

        List<SignalStateDto> bullishSignals = signalQueryService.listSignals(
                timeframe, indicatorType, TrendState.SUPERTREND_BULLISH, SignalSort.LAST_FLIP_DESC);

        List<SignalStateDto> bearishSignals = signalQueryService.listSignals(
                timeframe, indicatorType, TrendState.SUPERTREND_BEARISH, SignalSort.LAST_FLIP_DESC);

        List<ScanResult> bullishRsi = scanService.execute(new ScanRequest(
                ScanOperator.AND,
                List.of(
                        new ScanCondition(timeframe, IndicatorType.SUPERTREND, null, TrendState.SUPERTREND_BULLISH, 10, null),
                        new ScanCondition(timeframe, IndicatorType.RSI, SignalEvent.RSI_CROSSED_ABOVE_60, null, null, 10)
                ),
                null
        )).results();

        List<ScanResult> bearishRsi = scanService.execute(new ScanRequest(
                ScanOperator.AND,
                List.of(
                        new ScanCondition(timeframe, IndicatorType.SUPERTREND, null, TrendState.SUPERTREND_BEARISH, 10, null),
                        new ScanCondition(timeframe, IndicatorType.RSI, SignalEvent.RSI_CROSSED_BELOW_40, null, null, 10)
                ),
                null
        )).results();

        OffsetDateTime lastIngestionAt = ingestionStatusService.lastSuccessfulD1IngestionAt().orElse(null);
        LocalDate candlesThrough = ingestionStatusService.latestCandleDate().orElse(null);
        boolean stale = !freshnessService.isUpToDate(timeframe);

        return new SummaryReport(
                pulse,
                bullishSignals,
                bearishSignals,
                bullishRsi,
                bearishRsi,
                lastIngestionAt,
                candlesThrough,
                stale
        );
    }
}
