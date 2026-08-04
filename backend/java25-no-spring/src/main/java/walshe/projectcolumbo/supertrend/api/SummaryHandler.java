package walshe.projectcolumbo.supertrend.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import walshe.projectcolumbo.supertrend.freshness.FreshnessMetadata;
import walshe.projectcolumbo.supertrend.freshness.FreshnessService;
import walshe.projectcolumbo.supertrend.freshness.FreshnessStatus;
import walshe.projectcolumbo.supertrend.freshness.StaleDataException;
import walshe.projectcolumbo.supertrend.persistence.MarketBreadthSnapshotDao;
import walshe.projectcolumbo.supertrend.pulse.MarketBreadthSnapshot;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalQueryService;
import walshe.projectcolumbo.supertrend.signal.SignalSort;
import walshe.projectcolumbo.supertrend.signal.SignalSummary;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Registers {@code GET /api/v1/summary}: SuperTrend-only bullish/bearish signal lists (a direct
 * {@link SignalQueryService} read, no scan-engine involved - unlike the old app, which combined
 * this with RSI scan conditions) plus the latest market-breadth pulse for the requested timeframe.
 */
public final class SummaryHandler {

    private final SignalQueryService signalQueryService;
    private final MarketBreadthSnapshotDao marketBreadthSnapshotDao;
    private final FreshnessService freshnessService;
    private final Clock clock;

    public SummaryHandler(SignalQueryService signalQueryService, MarketBreadthSnapshotDao marketBreadthSnapshotDao,
                           FreshnessService freshnessService, Clock clock) {
        this.signalQueryService = Objects.requireNonNull(signalQueryService, "signalQueryService must not be null");
        this.marketBreadthSnapshotDao = Objects.requireNonNull(marketBreadthSnapshotDao, "marketBreadthSnapshotDao must not be null");
        this.freshnessService = Objects.requireNonNull(freshnessService, "freshnessService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void register(Javalin app) {
        app.get("/api/v1/summary", this::getSummary);
    }

    @OpenApi(
            path = "/api/v1/summary",
            methods = HttpMethod.GET,
            summary = "SuperTrend-only bullish/bearish signal lists plus the latest market-breadth pulse for a timeframe",
            queryParams = {
                    @OpenApiParam(name = "timeframe", type = Timeframe.class, required = true),
                    @OpenApiParam(name = "format", type = SummaryFormat.class, description = "Defaults to JSON"),
                    @OpenApiParam(name = "requireFresh", type = Boolean.class, description = "Reject with 503 if the timeframe's data is stale beyond the grace window")
            },
            responses = {
                    @OpenApiResponse(status = "200", content = {
                            @OpenApiContent(from = SummaryResponse.class),
                            @OpenApiContent(mimeType = "text/markdown", type = "string"),
                            @OpenApiContent(mimeType = "text/plain", type = "string")
                    }),
                    @OpenApiResponse(status = "503", description = "Data is stale and requireFresh=true")
            }
    )
    private void getSummary(Context ctx) {
        Timeframe timeframe = ctx.queryParamAsClass("timeframe", Timeframe.class).get();
        SummaryFormat format = ctx.queryParamAsClass("format", SummaryFormat.class).getOrDefault(SummaryFormat.JSON);
        boolean requireFresh = ctx.queryParamAsClass("requireFresh", Boolean.class).getOrDefault(false);

        FreshnessStatus status = freshnessService.evaluate(timeframe);
        if (requireFresh && status.staleBeyondGraceWindow()) {
            throw new StaleDataException(status);
        }

        SummaryResponse response = buildResponse(timeframe, status);

        switch (format) {
            case MARKDOWN -> ctx.contentType("text/markdown").result(SummaryFormatter.toMarkdown(response, clock));
            case WATCHLIST -> ctx.contentType("text/plain").result(SummaryFormatter.toWatchlist(response));
            case JSON -> ctx.json(response);
        }
    }

    private SummaryResponse buildResponse(Timeframe timeframe, FreshnessStatus status) {
        // One unfiltered, pre-sorted fetch rather than one call per state - listSignals' state
        // filter is applied in-memory downstream of its DAO calls anyway, so fetching once and
        // splitting here avoids re-running the same ~6 queries twice (see TrendAlignmentService,
        // which hit the same pattern for W1+D1 x bullish+bearish).
        List<SignalSummary> allByFlipDesc = signalQueryService.listSignals(timeframe, null, SignalSort.LAST_FLIP_DESC);
        List<SignalSummary> bullishSignals = allByFlipDesc.stream().filter(s -> s.trendState() == TrendState.BULLISH).toList();
        List<SignalSummary> bearishSignals = allByFlipDesc.stream().filter(s -> s.trendState() == TrendState.BEARISH).toList();
        MarketBreadthSnapshot pulse = marketBreadthSnapshotDao.findLatest(timeframe).orElse(null);
        FreshnessMetadata metadata = freshnessService.metadataFor(Provider.BINANCE, status);

        return new SummaryResponse(
                timeframe, pulse, bullishSignals, bearishSignals, metadata.lastSuccessfulIngestionAt(), metadata.latestCandleDate(), !status.upToDate());
    }
}
