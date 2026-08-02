package walshe.projectcolumbo.supertrend.api;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import walshe.projectcolumbo.supertrend.signal.ScanCondition;
import walshe.projectcolumbo.supertrend.signal.ScanRequest;
import walshe.projectcolumbo.supertrend.signal.ScanResult;
import walshe.projectcolumbo.supertrend.signal.ScanService;

import java.util.List;

/**
 * Registers {@code POST /api/v1/scan}. A standalone capability - not JSON/Markdown/Watchlist
 * (JSON only, unlike {@code summary-api}/{@code trend-alignment-api}) and not freshness-gated,
 * since a request's conditions can span multiple/different timeframes with no single timeframe
 * to check.
 */
public final class ScanHandler {

    private final ScanService scanService;

    public ScanHandler(ScanService scanService) {
        this.scanService = scanService;
    }

    public void register(Javalin app) {
        app.post("/api/v1/scan", this::scan);
    }

    private void scan(Context ctx) {
        ScanRequest request;
        try {
            request = ctx.bodyAsClass(ScanRequest.class);
        } catch (Exception e) {
            throw new BadRequestResponse("Malformed scan request body: " + e.getMessage());
        }
        validate(request);

        List<ScanResult> results = scanService.execute(request);
        ctx.json(new ScanResponse(results));
    }

    private static void validate(ScanRequest request) {
        if (request.operator() == null) {
            throw new BadRequestResponse("operator is required");
        }
        if (request.conditions() == null || request.conditions().isEmpty()) {
            throw new BadRequestResponse("at least one condition is required");
        }
        for (ScanCondition condition : request.conditions()) {
            if (condition == null) {
                throw new BadRequestResponse("conditions must not contain a null entry");
            }
            if (condition.timeframe() == null) {
                throw new BadRequestResponse("every condition requires a timeframe");
            }
            if (condition.state() == null) {
                throw new BadRequestResponse("every condition requires a state");
            }
            if (condition.maxDaysSinceFlip() != null && condition.maxDaysSinceFlip() < 0) {
                throw new BadRequestResponse("maxDaysSinceFlip must not be negative");
            }
        }
        if (request.limit() != null && request.limit() < 0) {
            throw new BadRequestResponse("limit must not be negative");
        }
    }
}
