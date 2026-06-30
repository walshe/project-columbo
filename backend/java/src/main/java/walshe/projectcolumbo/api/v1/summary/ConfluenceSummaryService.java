package walshe.projectcolumbo.api.v1.summary;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.api.v1.SignalQueryService;
import walshe.projectcolumbo.api.v1.dto.SignalSort;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.summary.dto.ConfluenceSummaryReport;
import walshe.projectcolumbo.ingestion.IngestionStatusService;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ConfluenceSummaryService {

    private final SignalQueryService signalQueryService;
    private final IngestionStatusService ingestionStatusService;

    public ConfluenceSummaryService(SignalQueryService signalQueryService,
                                    IngestionStatusService ingestionStatusService) {
        this.signalQueryService = signalQueryService;
        this.ingestionStatusService = ingestionStatusService;
    }

    public ConfluenceSummaryReport getConfluence(int maxRetestAgeDays) {
        List<SignalStateDto> bullishConfluence = intersect(
                TrendState.SUPERTREND_BULLISH, TrendState.SUPERTREND_BULLISH);
        List<SignalStateDto> bullishRetest = retest(
                TrendState.SUPERTREND_BULLISH, TrendState.SUPERTREND_BEARISH, maxRetestAgeDays);

        List<SignalStateDto> bearishConfluence = intersect(
                TrendState.SUPERTREND_BEARISH, TrendState.SUPERTREND_BEARISH);
        List<SignalStateDto> bearishRetest = retest(
                TrendState.SUPERTREND_BEARISH, TrendState.SUPERTREND_BULLISH, maxRetestAgeDays);

        OffsetDateTime lastIngestionAt = ingestionStatusService.lastSuccessfulD1IngestionAt().orElse(null);
        LocalDate candlesThrough = ingestionStatusService.latestCandleDate().orElse(null);

        return new ConfluenceSummaryReport(
                bullishConfluence, bullishRetest,
                bearishConfluence, bearishRetest,
                lastIngestionAt, candlesThrough);
    }

    // Returns D1 signals whose symbol also appears in the W1 list, ordered by D1 flip date descending.
    private List<SignalStateDto> intersect(TrendState w1State, TrendState d1State) {
        List<SignalStateDto> w1Signals = signalQueryService.listSignals(
                Timeframe.W1, IndicatorType.SUPERTREND, w1State, null);
        List<SignalStateDto> d1Signals = signalQueryService.listSignals(
                Timeframe.D1, IndicatorType.SUPERTREND, d1State, SignalSort.LAST_FLIP_DESC);

        Set<String> w1Symbols = w1Signals.stream()
                .map(SignalStateDto::symbol)
                .collect(Collectors.toSet());

        return d1Signals.stream()
                .filter(d1 -> w1Symbols.contains(d1.symbol()))
                .collect(Collectors.toList());
    }

    // Returns D1 counter-trend signals whose symbol appears in the W1 aligned list,
    // filtered to assets whose D1 flipped within maxRetestAgeDays, ordered by D1 flip date descending.
    private List<SignalStateDto> retest(TrendState w1State, TrendState d1CounterState, int maxRetestAgeDays) {
        List<SignalStateDto> w1Signals = signalQueryService.listSignals(
                Timeframe.W1, IndicatorType.SUPERTREND, w1State, null);
        List<SignalStateDto> d1Signals = signalQueryService.listSignals(
                Timeframe.D1, IndicatorType.SUPERTREND, d1CounterState, SignalSort.LAST_FLIP_DESC);

        Set<String> w1Symbols = w1Signals.stream()
                .map(SignalStateDto::symbol)
                .collect(Collectors.toSet());

        return d1Signals.stream()
                .filter(d1 -> w1Symbols.contains(d1.symbol()))
                .filter(d1 -> d1.daysSinceFlip() != null && d1.daysSinceFlip() <= maxRetestAgeDays)
                .collect(Collectors.toList());
    }
}
