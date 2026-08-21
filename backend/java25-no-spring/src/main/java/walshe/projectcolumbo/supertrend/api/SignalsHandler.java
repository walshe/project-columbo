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
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalQueryService;
import walshe.projectcolumbo.supertrend.signal.SignalSort;
import walshe.projectcolumbo.supertrend.signal.SignalSummary;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
            summary = "List the latest SuperTrend signal state for every active asset on a timeframe, or narrow it to one or more specific assets",
            description = "Returns one entry per matching active asset: its current SuperTrend trend state, when it last flipped, "
                    + "7-day average volume, percent change since that flip, and a ready-to-open TradingView chart link. "
                    + "Filters compose: pass any combination of state/assetClass/symbols together. To look up a specific asset's "
                    + "current trend (the common case for a single-symbol query), use symbols=BTCUSDT rather than fetching the "
                    + "full list and searching client-side.",
            queryParams = {
                    @OpenApiParam(name = "timeframe", type = Timeframe.class, required = true,
                            description = "Which candle timeframe to read the trend from. D1 = daily close-to-close trend, W1 = weekly (Monday-close) trend."),
                    @OpenApiParam(name = "state", type = TrendState.class,
                            description = "Only return assets currently in this trend state (BULLISH or BEARISH). Omit to return both."),
                    @OpenApiParam(name = "sort", type = SignalSort.class,
                            description = "Result ordering. ASSET_ASC (default, symbol A-Z) | LAST_FLIP_ASC/DESC (oldest/most-recent flip first, nulls last) | "
                                    + "TREND_STATE_ASC (bullish before bearish) | LIQUIDITY_DESC (highest 7-day avg volume first) | "
                                    + "PCT_CHANGE_ASC/DESC (smallest/largest move since flip first, nulls last)."),
                    @OpenApiParam(name = "assetClass", type = AssetClass.class,
                            description = "Only return assets in this category: CRYPTO, STOCK, or ETF. Omit to include all categories."),
                    @OpenApiParam(name = "symbols", type = String.class,
                            description = "Comma-separated list of exact, case-sensitive symbols to look up, e.g. symbols=BTCUSDT or "
                                    + "symbols=BTCUSDT,ETHUSDT. A symbol that doesn't match any active asset is silently omitted from the "
                                    + "response, not an error - if a queried symbol is simply missing from the results, double-check the "
                                    + "exact spelling/case rather than assuming a server error. Composes with state/assetClass/sort. Omit to return all active assets."),
                    @OpenApiParam(name = "requireFresh", type = Boolean.class,
                            description = "When true, respond 503 instead of data if this timeframe's ingested data is stale beyond the "
                                    + "configured grace window. Defaults to false (stale data is still returned, with staleness flagged in the response's freshness metadata).")
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = SignalListResponse.class),
                            description = "Zero or more matching signals plus freshness metadata. An empty list is a valid, non-error result "
                                    + "(e.g. no assets currently match the filters, or a requested symbol doesn't exist)."),
                    @OpenApiResponse(status = "503", description = "requireFresh=true and the timeframe's data is stale beyond the grace window; no signal data is returned")
            }
    )
    private void getSignals(Context ctx) {
        Timeframe timeframe = ctx.queryParamAsClass("timeframe", Timeframe.class).get();
        TrendState state = ctx.queryParamAsClass("state", TrendState.class).allowNullable().get();
        SignalSort sort = ctx.queryParamAsClass("sort", SignalSort.class).allowNullable().get();
        AssetClass assetClass = ctx.queryParamAsClass("assetClass", AssetClass.class).allowNullable().get();
        Set<String> symbols = parseSymbols(ctx.queryParam("symbols"));
        boolean requireFresh = ctx.queryParamAsClass("requireFresh", Boolean.class).getOrDefault(false);

        // Computed once and reused below (buildResponse) rather than calling FreshnessService.evaluate
        // again - it issues the same underlying candle query each time it's called.
        FreshnessStatus status = freshnessService.evaluate(timeframe);
        if (requireFresh && status.staleBeyondGraceWindow()) {
            throw new StaleDataException(status);
        }

        List<SignalSummary> signals = signalQueryService.listSignals(timeframe, state, sort, assetClass, symbols);
        ctx.json(buildResponse(signals, status));
    }

    /** {@code null} when the query param is absent/blank, matching how every other optional filter here behaves (null = unfiltered). */
    private static Set<String> parseSymbols(String rawCommaSeparated) {
        if (rawCommaSeparated == null || rawCommaSeparated.isBlank()) {
            return null;
        }
        return Arrays.stream(rawCommaSeparated.split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isEmpty())
                .collect(Collectors.toSet());
    }

    @OpenApi(
            path = "/api/v1/assets/by-state",
            methods = HttpMethod.GET,
            summary = "List every active asset currently in a given trend state on a timeframe - no freshness gating",
            description = "A browse-by-state endpoint, not a symbol lookup - unlike GET /api/v1/signals, there is no symbols "
                    + "filter here and staleness is never checked (no requireFresh/503 path). Use this to answer \"which assets "
                    + "are bullish right now\", not \"what is BTCUSDT's trend\" (use /api/v1/signals?symbols=... for that instead).",
            queryParams = {
                    @OpenApiParam(name = "timeframe", type = Timeframe.class, required = true,
                            description = "Which candle timeframe to read the trend from. D1 = daily, W1 = weekly (Monday-close)."),
                    @OpenApiParam(name = "state", type = TrendState.class, required = true,
                            description = "Only return assets currently in this trend state: BULLISH or BEARISH."),
                    @OpenApiParam(name = "assetClass", type = AssetClass.class,
                            description = "Only include assets in this category: CRYPTO, STOCK, or ETF. Omit to include all categories.")
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = SignalListResponse.class),
                            description = "Zero or more matching assets; an empty list is a valid, non-error result")
            }
    )
    private void getAssetsByState(Context ctx) {
        Timeframe timeframe = ctx.queryParamAsClass("timeframe", Timeframe.class).get();
        TrendState state = ctx.queryParamAsClass("state", TrendState.class).get();
        AssetClass assetClass = ctx.queryParamAsClass("assetClass", AssetClass.class).allowNullable().get();

        FreshnessStatus status = freshnessService.evaluate(timeframe);
        List<SignalSummary> signals = signalQueryService.listSignals(timeframe, state, null, assetClass);
        ctx.json(buildResponse(signals, status));
    }

    private SignalListResponse buildResponse(List<SignalSummary> signals, FreshnessStatus status) {
        FreshnessMetadata metadata = freshnessService.metadataFor(Provider.BINANCE, status);
        return new SignalListResponse(signals, metadata.lastSuccessfulIngestionAt(), metadata.latestCandleDate(), !status.upToDate());
    }
}
