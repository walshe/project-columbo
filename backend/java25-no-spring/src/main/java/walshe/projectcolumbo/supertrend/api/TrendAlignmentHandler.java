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
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.TrendAlignment;
import walshe.projectcolumbo.supertrend.signal.TrendAlignmentService;

import java.time.Clock;
import java.util.Objects;

/** Registers {@code GET /api/v1/summary/trend-alignment}. */
public final class TrendAlignmentHandler {

    private static final int DEFAULT_MAX_RETEST_AGE_DAYS = 7;

    private final TrendAlignmentService trendAlignmentService;
    private final FreshnessService freshnessService;
    private final Clock clock;

    public TrendAlignmentHandler(TrendAlignmentService trendAlignmentService, FreshnessService freshnessService, Clock clock) {
        this.trendAlignmentService = Objects.requireNonNull(trendAlignmentService, "trendAlignmentService must not be null");
        this.freshnessService = Objects.requireNonNull(freshnessService, "freshnessService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void register(Javalin app) {
        app.get("/api/v1/summary/trend-alignment", this::getTrendAlignment);
    }

    @OpenApi(
            path = "/api/v1/summary/trend-alignment",
            methods = HttpMethod.GET,
            summary = "Cross-timeframe (W1+D1) SuperTrend confluence and retest, driven by D1 freshness",
            description = "Buckets every active asset (per the assetClass filter) into up to four lists: bullish/bearish confluence "
                    + "(W1 and D1 currently agree - the strongest signal) and bullish/bearish retest (W1 still intact, but D1 "
                    + "recently flipped counter to it within maxRetestAgeDays - a pullback/bounce, not a reversal). An asset "
                    + "appears in at most one list. Freshness is always evaluated against D1 (the driving timeframe here), never W1.",
            queryParams = {
                    @OpenApiParam(name = "format", type = SummaryFormat.class,
                            description = "Response shape. JSON (default) = structured TrendAlignmentResponse. MARKDOWN = "
                                    + "text/markdown human-readable report. WATCHLIST = text/plain, one symbol per line, "
                                    + "suitable for pasting into a TradingView watchlist import."),
                    @OpenApiParam(name = "maxRetestAgeDays", type = Integer.class,
                            description = "How many days back a D1 flip can be and still count as a \"recent\" retest. Defaults to 7. "
                                    + "A D1 flip older than this is treated as the new trend, not a retest of the old one."),
                    @OpenApiParam(name = "assetClass", type = AssetClass.class,
                            description = "Only include assets in this category: CRYPTO, STOCK, or ETF. Omit to include all categories."),
                    @OpenApiParam(name = "requireFresh", type = Boolean.class,
                            description = "When true, respond 503 instead of data if D1 is stale beyond the grace window. Defaults to false.")
            },
            responses = {
                    @OpenApiResponse(status = "200", content = {
                            @OpenApiContent(from = TrendAlignmentResponse.class),
                            @OpenApiContent(mimeType = "text/markdown", type = "string"),
                            @OpenApiContent(mimeType = "text/plain", type = "string")
                    }, description = "Exactly one of the three content types is returned, selected by the format param"),
                    @OpenApiResponse(status = "503", description = "D1 data is stale and requireFresh=true; no alignment data is returned")
            }
    )
    private void getTrendAlignment(Context ctx) {
        SummaryFormat format = ctx.queryParamAsClass("format", SummaryFormat.class).getOrDefault(SummaryFormat.JSON);
        int maxRetestAgeDays = ctx.queryParamAsClass("maxRetestAgeDays", Integer.class).getOrDefault(DEFAULT_MAX_RETEST_AGE_DAYS);
        AssetClass assetClass = ctx.queryParamAsClass("assetClass", AssetClass.class).allowNullable().get();
        boolean requireFresh = ctx.queryParamAsClass("requireFresh", Boolean.class).getOrDefault(false);

        // D1 is the driving timeframe for this cross-timeframe report (W1 is rolled up from D1),
        // so freshness is only ever evaluated against D1 - W1's own freshness is never checked here.
        FreshnessStatus status = freshnessService.evaluate(Timeframe.D1);
        if (requireFresh && status.staleBeyondGraceWindow()) {
            throw new StaleDataException(status);
        }

        TrendAlignment alignment = trendAlignmentService.computeAlignment(maxRetestAgeDays, assetClass);

        switch (format) {
            case MARKDOWN -> ctx.contentType("text/markdown").result(TrendAlignmentFormatter.toMarkdown(alignment, maxRetestAgeDays, assetClass, clock));
            case WATCHLIST -> ctx.contentType("text/plain").result(TrendAlignmentFormatter.toWatchlist(alignment));
            case JSON -> ctx.json(buildResponse(alignment, maxRetestAgeDays, assetClass, status));
        }
    }

    private TrendAlignmentResponse buildResponse(TrendAlignment alignment, int maxRetestAgeDays, AssetClass assetClass, FreshnessStatus status) {
        FreshnessMetadata metadata = freshnessService.metadataFor(status);
        return new TrendAlignmentResponse(
                maxRetestAgeDays, assetClass,
                alignment.bullishConfluence(), alignment.bullishRetest(), alignment.bearishConfluence(), alignment.bearishRetest(),
                metadata.lastSuccessfulIngestionAt(), metadata.latestCandleDate(), !status.upToDate());
    }
}
