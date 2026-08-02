package walshe.projectcolumbo.supertrend.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import walshe.projectcolumbo.supertrend.freshness.FreshnessMetadata;
import walshe.projectcolumbo.supertrend.freshness.FreshnessService;
import walshe.projectcolumbo.supertrend.freshness.FreshnessStatus;
import walshe.projectcolumbo.supertrend.freshness.StaleDataException;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.TrendAlignment;
import walshe.projectcolumbo.supertrend.signal.TrendAlignmentService;

import java.time.Clock;

/** Registers {@code GET /api/v1/summary/trend-alignment}. */
public final class TrendAlignmentHandler {

    private static final int DEFAULT_MAX_RETEST_AGE_DAYS = 7;

    private final TrendAlignmentService trendAlignmentService;
    private final FreshnessService freshnessService;
    private final Clock clock;

    public TrendAlignmentHandler(TrendAlignmentService trendAlignmentService, FreshnessService freshnessService, Clock clock) {
        this.trendAlignmentService = trendAlignmentService;
        this.freshnessService = freshnessService;
        this.clock = clock;
    }

    public void register(Javalin app) {
        app.get("/api/v1/summary/trend-alignment", this::getTrendAlignment);
    }

    private void getTrendAlignment(Context ctx) {
        SummaryFormat format = ctx.queryParamAsClass("format", SummaryFormat.class).getOrDefault(SummaryFormat.JSON);
        int maxRetestAgeDays = ctx.queryParamAsClass("maxRetestAgeDays", Integer.class).getOrDefault(DEFAULT_MAX_RETEST_AGE_DAYS);
        boolean requireFresh = ctx.queryParamAsClass("requireFresh", Boolean.class).getOrDefault(false);

        // D1 is the driving timeframe for this cross-timeframe report (W1 is rolled up from D1),
        // so freshness is only ever evaluated against D1 - W1's own freshness is never checked here.
        FreshnessStatus status = freshnessService.evaluate(Timeframe.D1);
        if (requireFresh && status.staleBeyondGraceWindow()) {
            throw new StaleDataException(status);
        }

        TrendAlignment alignment = trendAlignmentService.computeAlignment(maxRetestAgeDays);

        switch (format) {
            case MARKDOWN -> ctx.contentType("text/markdown").result(TrendAlignmentFormatter.toMarkdown(alignment, clock));
            case WATCHLIST -> ctx.contentType("text/plain").result(TrendAlignmentFormatter.toWatchlist(alignment));
            case JSON -> ctx.json(buildResponse(alignment, status));
        }
    }

    private TrendAlignmentResponse buildResponse(TrendAlignment alignment, FreshnessStatus status) {
        FreshnessMetadata metadata = freshnessService.metadataFor(Provider.BINANCE, status);
        return new TrendAlignmentResponse(
                alignment.bullishConfluence(), alignment.bullishRetest(), alignment.bearishConfluence(), alignment.bearishRetest(),
                metadata.lastSuccessfulIngestionAt(), metadata.latestCandleDate(), !status.upToDate());
    }
}
