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
import walshe.projectcolumbo.supertrend.signal.ScanConditionMatch;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Registers {@code POST /api/v1/weekly-trend-briefing}: runs a full D1 ingestion pipeline to
 * completion, then composes the weekly regime-read / BTC-alignment / trend-alignment / scan
 * routine (CRYPTO, ETF, and STOCK) into a single Markdown report. A scripted version of the
 * manual weekly checklist this system exists to support - see {@link WeeklyTrendBriefingFormatter}
 * for the rendering and {@link WeeklyTrendBriefingReport} for the composed shape.
 * <p>
 * Crypto is reported both directions (bullish and bearish) since BTC alignment - not this
 * endpoint - is the signal for caution. Stocks and ETFs are longs-only, so only their bullish
 * side is scanned/reported at all.
 */
public final class WeeklyTrendBriefingHandler {

    private static final int MAX_DAYS_SINCE_FLIP = 7;
    private static final int MAX_RETEST_AGE_DAYS = 7;
    private static final int SCAN_LIMIT = 15;
    private static final String BTC_SYMBOL = "BTCUSDT";
    private static final List<AssetClass> BRIEFING_ASSET_CLASSES = List.of(AssetClass.CRYPTO, AssetClass.ETF, AssetClass.STOCK);

    private final PipelineOrchestrator pipelineOrchestrator;
    private final MarketBreadthSnapshotDao marketBreadthSnapshotDao;
    private final SignalQueryService signalQueryService;
    private final TrendAlignmentService trendAlignmentService;
    private final ScanService scanService;
    private final Clock clock;

    public WeeklyTrendBriefingHandler(
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
        app.post("/api/v1/weekly-trend-briefing", this::runBriefing);
    }

    @OpenApi(
            path = "/api/v1/weekly-trend-briefing",
            methods = HttpMethod.POST,
            summary = "Runs D1 ingestion to completion, then composes the weekly regime/BTC-alignment/trend-alignment/scan routine into one Markdown report",
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(mimeType = "text/markdown", type = "string")),
                    @OpenApiResponse(status = "409", description = "A BINANCE/D1 ingestion run is already RUNNING")
            }
    )
    private void runBriefing(Context ctx) {
        PipelineRunResult ingestionResult = pipelineOrchestrator.runDaily(Provider.BINANCE, Timeframe.D1);
        WeeklyTrendBriefingReport report = buildReport(ingestionResult);
        ctx.contentType("text/markdown").result(WeeklyTrendBriefingFormatter.toMarkdown(report, OffsetDateTime.now(clock)));
    }

    private WeeklyTrendBriefingReport buildReport(PipelineRunResult ingestionResult) {
        Map<AssetClass, MarketBreadthSnapshot> regimePulses = BRIEFING_ASSET_CLASSES.stream()
                .collect(Collectors.toMap(assetClass -> assetClass,
                        assetClass -> marketBreadthSnapshotDao.findLatest(Timeframe.W1, assetClass).orElse(null)));

        Map<AssetClass, TrendAlignment> trendAlignments = BRIEFING_ASSET_CLASSES.stream()
                .collect(Collectors.toMap(assetClass -> assetClass,
                        assetClass -> trendAlignmentService.computeAlignment(MAX_RETEST_AGE_DAYS, assetClass)));

        Map<AssetClass, List<ScanResult>> bullishScanCandidates = BRIEFING_ASSET_CLASSES.stream()
                .collect(Collectors.toMap(assetClass -> assetClass, assetClass -> scan(assetClass, TrendState.BULLISH)));
        Map<AssetClass, List<ScanResult>> bearishScanCandidates = Map.of(
                AssetClass.CRYPTO, scan(AssetClass.CRYPTO, TrendState.BEARISH));

        return new WeeklyTrendBriefingReport(
                ingestionResult.runId(), ingestionResult.status(),
                regimePulses, btcState(Timeframe.W1), btcState(Timeframe.D1),
                trendAlignments, bullishScanCandidates, bearishScanCandidates);
    }

    private TrendState btcState(Timeframe timeframe) {
        return signalQueryService.listSignals(timeframe, null, null, AssetClass.CRYPTO).stream()
                .filter(signal -> signal.symbol().equals(BTC_SYMBOL))
                .map(SignalSummary::trendState)
                .findFirst()
                .orElse(null);
    }

    /**
     * Liquidity gates which candidates make the cut ({@link ScanSort#LIQUIDITY_DESC}, capped at
     * {@link #SCAN_LIMIT}); movement since the D1 flip then ranks that already-liquid shortlist -
     * biggest confirming move first for bullish, biggest confirming drop first for bearish (a
     * bearish asset up against its own signal is a failing move, not a favorable one, so it sorts
     * last rather than first).
     */
    private List<ScanResult> scan(AssetClass assetClass, TrendState state) {
        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.W1, state, null), new ScanCondition(Timeframe.D1, state, MAX_DAYS_SINCE_FLIP)),
                SCAN_LIMIT, assetClass, ScanSort.LIQUIDITY_DESC);
        List<ScanResult> liquidityGated = scanService.execute(request);

        Comparator<ScanResult> byD1Movement = Comparator.comparing(
                WeeklyTrendBriefingHandler::d1PctChangeSinceFlip, Comparator.nullsLast(Comparator.naturalOrder()));
        return liquidityGated.stream()
                .sorted(state == TrendState.BULLISH ? byD1Movement.reversed() : byD1Movement)
                .toList();
    }

    private static BigDecimal d1PctChangeSinceFlip(ScanResult result) {
        return result.matchedConditions().stream()
                .filter(match -> match.timeframe() == Timeframe.D1)
                .findFirst()
                .map(ScanConditionMatch::pctChangeSinceFlip)
                .orElse(null);
    }
}
