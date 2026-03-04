package walshe.projectcolumbo.api.v1.summary;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.api.v1.MarketPulseQueryService;
import walshe.projectcolumbo.api.v1.SignalQueryService;
import walshe.projectcolumbo.api.v1.dto.MarketPulseDto;
import walshe.projectcolumbo.api.v1.dto.SignalSort;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.scan.ScanService;
import walshe.projectcolumbo.api.v1.scan.dto.ScanCondition;
import walshe.projectcolumbo.api.v1.scan.dto.ScanOperator;
import walshe.projectcolumbo.api.v1.scan.dto.ScanRequest;
import walshe.projectcolumbo.api.v1.scan.dto.ScanResult;
import walshe.projectcolumbo.api.v1.summary.dto.SummaryReport;
import walshe.projectcolumbo.persistence.IndicatorType;
import walshe.projectcolumbo.persistence.SignalEvent;
import walshe.projectcolumbo.persistence.Timeframe;
import walshe.projectcolumbo.persistence.TrendState;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SummaryService {

    private final SignalQueryService signalQueryService;
    private final MarketPulseQueryService marketPulseQueryService;
    private final ScanService scanService;

    public SummaryService(SignalQueryService signalQueryService,
                          MarketPulseQueryService marketPulseQueryService,
                          ScanService scanService) {
        this.signalQueryService = signalQueryService;
        this.marketPulseQueryService = marketPulseQueryService;
        this.scanService = scanService;
    }

    public SummaryReport getSummary(Timeframe timeframe) {
        IndicatorType indicatorType = IndicatorType.SUPERTREND;
        MarketPulseDto pulse = marketPulseQueryService.getLatestPulse(timeframe, indicatorType).orElse(null);

        List<SignalStateDto> bullishSignals = signalQueryService.listSignals(
                timeframe, indicatorType, TrendState.BULLISH, SignalSort.LIQUIDITY_DESC);

        List<SignalStateDto> bearishSignals = signalQueryService.listSignals(
                timeframe, indicatorType, TrendState.BEARISH, SignalSort.LIQUIDITY_DESC);

        List<ScanResult> bullishRsi = scanService.execute(new ScanRequest(
                timeframe,
                ScanOperator.AND,
                List.of(
                        new ScanCondition(IndicatorType.SUPERTREND, null, TrendState.BULLISH, 10, null),
                        new ScanCondition(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, null, null, 10)
                ),
                null
        )).results();

        List<ScanResult> bearishRsi = scanService.execute(new ScanRequest(
                timeframe,
                ScanOperator.AND,
                List.of(
                        new ScanCondition(IndicatorType.SUPERTREND, null, TrendState.BEARISH, 10, null),
                        new ScanCondition(IndicatorType.RSI, SignalEvent.CROSSED_BELOW_40, null, null, 10)
                ),
                null
        )).results();

        return new SummaryReport(
                pulse,
                bullishSignals,
                bearishSignals,
                bullishRsi,
                bearishRsi
        );
    }
}
