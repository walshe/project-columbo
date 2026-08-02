package walshe.projectcolumbo.supertrend.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
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

/** Registers {@code GET /api/v1/signals} and {@code GET /api/v1/assets/by-state}. */
public final class SignalsHandler {

    private final SignalQueryService signalQueryService;
    private final FreshnessService freshnessService;

    public SignalsHandler(SignalQueryService signalQueryService, FreshnessService freshnessService) {
        this.signalQueryService = signalQueryService;
        this.freshnessService = freshnessService;
    }

    public void register(Javalin app) {
        app.get("/api/v1/signals", this::getSignals);
        app.get("/api/v1/assets/by-state", this::getAssetsByState);
    }

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
