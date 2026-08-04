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
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalQueryService;
import walshe.projectcolumbo.supertrend.signal.SignalSort;
import walshe.projectcolumbo.supertrend.signal.SignalSummary;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import java.util.List;
import java.util.Objects;

/** Registers {@code GET /api/v1/signals} and {@code GET /api/v1/assets/by-state}. */
public final class SignalsHandler {

    private final SignalQueryService signalQueryService;
    private final FreshnessService freshnessService;

    public SignalsHandler(SignalQueryService signalQueryService, FreshnessService freshnessService) {
        this.signalQueryService = Objects.requireNonNull(signalQueryService, "signalQueryService must not be null");
        this.freshnessService = Objects.requireNonNull(freshnessService, "freshnessService must not be null");
    }

    public void register(Javalin app) {
        app.get("/api/v1/signals", this::getSignals);
        app.get("/api/v1/assets/by-state", this::getAssetsByState);
    }

    @OpenApi(
            path = "/api/v1/signals",
            methods = HttpMethod.GET,
            summary = "List the latest SuperTrend signal state for every active asset on a timeframe",
            queryParams = {
                    @OpenApiParam(name = "timeframe", type = Timeframe.class, required = true),
                    @OpenApiParam(name = "state", type = TrendState.class, description = "Filter to this trend state only"),
                    @OpenApiParam(name = "sort", type = SignalSort.class, description = "Defaults to ASSET_ASC"),
                    @OpenApiParam(name = "requireFresh", type = Boolean.class, description = "Reject with 503 if the timeframe's data is stale beyond the grace window")
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = SignalListResponse.class)),
                    @OpenApiResponse(status = "503", description = "Data is stale and requireFresh=true")
            }
    )
    private void getSignals(Context ctx) {
        Timeframe timeframe = ctx.queryParamAsClass("timeframe", Timeframe.class).get();
        TrendState state = ctx.queryParamAsClass("state", TrendState.class).allowNullable().get();
        SignalSort sort = ctx.queryParamAsClass("sort", SignalSort.class).allowNullable().get();
        boolean requireFresh = ctx.queryParamAsClass("requireFresh", Boolean.class).getOrDefault(false);

        // Computed once and reused below (buildResponse) rather than calling FreshnessService.evaluate
        // again - it issues the same underlying candle query each time it's called.
        FreshnessStatus status = freshnessService.evaluate(timeframe);
        if (requireFresh && status.staleBeyondGraceWindow()) {
            throw new StaleDataException(status);
        }

        List<SignalSummary> signals = signalQueryService.listSignals(timeframe, state, sort);
        ctx.json(buildResponse(signals, status));
    }

    @OpenApi(
            path = "/api/v1/assets/by-state",
            methods = HttpMethod.GET,
            summary = "List every active asset currently in a given trend state on a timeframe - no freshness gating",
            queryParams = {
                    @OpenApiParam(name = "timeframe", type = Timeframe.class, required = true),
                    @OpenApiParam(name = "state", type = TrendState.class, required = true)
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = SignalListResponse.class))
            }
    )
    private void getAssetsByState(Context ctx) {
        Timeframe timeframe = ctx.queryParamAsClass("timeframe", Timeframe.class).get();
        TrendState state = ctx.queryParamAsClass("state", TrendState.class).get();

        FreshnessStatus status = freshnessService.evaluate(timeframe);
        List<SignalSummary> signals = signalQueryService.listSignals(timeframe, state, null);
        ctx.json(buildResponse(signals, status));
    }

    private SignalListResponse buildResponse(List<SignalSummary> signals, FreshnessStatus status) {
        FreshnessMetadata metadata = freshnessService.metadataFor(Provider.BINANCE, status);
        return new SignalListResponse(signals, metadata.lastSuccessfulIngestionAt(), metadata.latestCandleDate(), !status.upToDate());
    }
}
