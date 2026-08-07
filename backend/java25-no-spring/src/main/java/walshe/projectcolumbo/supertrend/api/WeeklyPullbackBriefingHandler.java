package walshe.projectcolumbo.supertrend.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import walshe.projectcolumbo.supertrend.persistence.MarketBreadthSnapshotDao;
import walshe.projectcolumbo.supertrend.pipeline.PipelineOrchestrator;
import walshe.projectcolumbo.supertrend.pipeline.PipelineRunResult;
import walshe.projectcolumbo.supertrend.pulse.MarketBreadthSnapshot;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.ScanCondition;
import walshe.projectcolumbo.supertrend.signal.ScanOperator;
import walshe.projectcolumbo.supertrend.signal.ScanRequest;
import walshe.projectcolumbo.supertrend.signal.ScanResult;
import walshe.projectcolumbo.supertrend.signal.ScanService;
import walshe.projectcolumbo.supertrend.signal.ScanSort;
import walshe.projectcolumbo.supertrend.signal.SignalQueryService;
import walshe.projectcolumbo.supertrend.signal.SignalSummary;
import walshe.projectcolumbo.supertrend.signal.TrendAlignment;
import walshe.projectcolumbo.supertrend.signal.TrendAlignmentService;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Registers {@code POST /api/v1/weekly-pullback-briefing}: the mean-reversion sibling of
 * {@link WeeklyTrendBriefingHandler}. Runs a full D1 ingestion pipeline to completion, then
 * composes a report headlined by <em>retest</em> candidates - W1 trend intact, D1 recently
 * flipped counter to it - rather than confluence. The opinion this briefing expresses is "buy the
 * dip in an established trend" rather than "follow the already-confirmed move"; confluence data
 * (the trend briefing's headline) isn't rendered here at all.
 * <p>
 * Crypto is reported both directions (bullish pullback and bearish bounce) since BTC alignment -
 * not this endpoint - is the signal for caution. Stocks and ETFs are longs-only, so only bullish
 * pullbacks are scanned/reported for them.
 */
public final class WeeklyPullbackBriefingHandler {

    private static final int MAX_RETEST_AGE_DAYS = 7;
    private static final int SCAN_LIMIT = 15;
    private static final List<AssetClass> BRIEFING_ASSET_CLASSES = WeeklyBriefingFormatting.BRIEFING_ASSET_CLASSES;

    private final PipelineOrchestrator pipelineOrchestrator;
    private final MarketBreadthSnapshotDao marketBreadthSnapshotDao;
    private final SignalQueryService signalQueryService;
    private final TrendAlignmentService trendAlignmentService;
    private final ScanService scanService;
    private final Clock clock;

    public WeeklyPullbackBriefingHandler(
            PipelineOrchestrator pipelineOrchestrator,
            MarketBreadthSnapshotDao marketBreadthSnapshotDao,
            SignalQueryService signalQueryService,
            TrendAlignmentService trendAlignmentService,
            ScanService scanService,
            Clock clock
    ) {
        this.pipelineOrchestrator = Objects.requireNonNull(pipelineOrchestrator, "pipelineOrchestrator must not be null");
        this.marketBreadthSnapshotDao = Objects.requireNonNull(marketBreadthSnapshotDao, "marketBreadthSnapshotDao must not be null");
        this.signalQueryService = Objects.requireNonNull(signalQueryService, "signalQueryService must not be null");
        this.trendAlignmentService = Objects.requireNonNull(trendAlignmentService, "trendAlignmentService must not be null");
        this.scanService = Objects.requireNonNull(scanService, "scanService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void register(Javalin app) {
        app.post("/api/v1/weekly-pullback-briefing", this::runBriefing);
    }

    @OpenApi(
            path = "/api/v1/weekly-pullback-briefing",
            methods = HttpMethod.POST,
            summary = "Runs D1 ingestion to completion, then composes a mean-reversion weekly briefing (retest/pullback candidates, not confluence) into one Markdown report",
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(mimeType = "text/markdown", type = "string")),
                    @OpenApiResponse(status = "409", description = "A BINANCE/D1 ingestion run is already RUNNING")
            }
    )
    private void runBriefing(Context ctx) {
        PipelineRunResult ingestionResult = pipelineOrchestrator.runDaily(Provider.BINANCE, Timeframe.D1);
        WeeklyPullbackBriefingReport report = buildReport(ingestionResult);
        ctx.contentType("text/markdown").result(WeeklyPullbackBriefingFormatter.toMarkdown(report, OffsetDateTime.now(clock)));
    }

    private WeeklyPullbackBriefingReport buildReport(PipelineRunResult ingestionResult) {
        Map<AssetClass, MarketBreadthSnapshot> regimePulses = BRIEFING_ASSET_CLASSES.stream()
                .collect(Collectors.toMap(assetClass -> assetClass,
                        assetClass -> marketBreadthSnapshotDao.findLatest(Timeframe.W1, assetClass).orElse(null)));

        Map<AssetClass, TrendAlignment> alignments = BRIEFING_ASSET_CLASSES.stream()
                .collect(Collectors.toMap(assetClass -> assetClass,
                        assetClass -> trendAlignmentService.computeAlignment(MAX_RETEST_AGE_DAYS, assetClass)));

        Map<AssetClass, List<SignalSummary>> bullishPullbackCandidates = alignments.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().bullishRetest()));
        Map<AssetClass, List<SignalSummary>> bearishPullbackCandidates = Map.of(
                AssetClass.CRYPTO, alignments.get(AssetClass.CRYPTO).bearishRetest());

        Map<AssetClass, List<ScanResult>> bullishScanCandidates = BRIEFING_ASSET_CLASSES.stream()
                .collect(Collectors.toMap(assetClass -> assetClass, assetClass -> pullbackScan(assetClass, TrendState.BULLISH)));
        Map<AssetClass, List<ScanResult>> bearishScanCandidates = Map.of(
                AssetClass.CRYPTO, pullbackScan(AssetClass.CRYPTO, TrendState.BEARISH));

        return new WeeklyPullbackBriefingReport(
                ingestionResult.runId(), ingestionResult.status(),
                regimePulses, WeeklyBriefingSignals.btcState(signalQueryService, Timeframe.W1), WeeklyBriefingSignals.btcState(signalQueryService, Timeframe.D1),
                bullishPullbackCandidates, bearishPullbackCandidates, bullishScanCandidates, bearishScanCandidates);
    }

    /**
     * Liquidity gates which candidates make the cut ({@link ScanSort#LIQUIDITY_DESC}, capped at
     * {@link #SCAN_LIMIT}); depth of the counter-trend move then ranks that already-liquid
     * shortlist - deepest dip first for a bullish pullback (most negative D1 move since its
     * counter-flip), biggest bounce first for a bearish one (most positive D1 move). This is the
     * opposite ranking direction from {@link WeeklyTrendBriefingHandler}, since here the D1 leg
     * is the counter-trend move being evaluated, not a confirming one.
     */
    private List<ScanResult> pullbackScan(AssetClass assetClass, TrendState primaryState) {
        TrendState counterState = primaryState == TrendState.BULLISH ? TrendState.BEARISH : TrendState.BULLISH;
        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.W1, primaryState, null), new ScanCondition(Timeframe.D1, counterState, MAX_RETEST_AGE_DAYS)),
                SCAN_LIMIT, assetClass, ScanSort.LIQUIDITY_DESC);
        List<ScanResult> liquidityGated = scanService.execute(request);

        return liquidityGated.stream()
                .sorted(WeeklyBriefingSignals.byD1Movement(primaryState == TrendState.BEARISH))
                .toList();
    }
}
