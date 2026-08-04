package walshe.projectcolumbo.supertrend.api;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import walshe.projectcolumbo.supertrend.signal.ScanCondition;
import walshe.projectcolumbo.supertrend.signal.ScanRequest;
import walshe.projectcolumbo.supertrend.signal.ScanResult;
import walshe.projectcolumbo.supertrend.signal.ScanService;

import java.util.List;
import java.util.Objects;

/**
 * Registers {@code POST /api/v1/scan}. A standalone capability - not JSON/Markdown/Watchlist
 * (JSON only, unlike {@code summary-api}/{@code trend-alignment-api}) and not freshness-gated,
 * since a request's conditions can span multiple/different timeframes with no single timeframe
 * to check.
 */
public final class ScanHandler {

    private final ScanService scanService;

    public ScanHandler(ScanService scanService) {
        this.scanService = Objects.requireNonNull(scanService, "scanService must not be null");
    }

    public void register(Javalin app) {
        app.post("/api/v1/scan", this::scan);
    }

    @OpenApi(
            path = "/api/v1/scan",
            methods = HttpMethod.POST,
            summary = "Match assets against one or more SuperTrend conditions combined by AND/OR, optionally spanning different timeframes",
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ScanRequest.class), required = true),
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = ScanResponse.class)),
                    @OpenApiResponse(status = "400", description = "Missing/invalid operator, conditions, or limit")
            }
    )
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
