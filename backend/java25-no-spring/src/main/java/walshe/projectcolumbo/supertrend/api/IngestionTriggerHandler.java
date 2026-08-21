package walshe.projectcolumbo.supertrend.api;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import walshe.projectcolumbo.supertrend.pipeline.PipelineOrchestrator;

import java.util.Objects;

/**
 * Registers {@code POST /api/v1/internal/ingestion/run}. Returns 202 as soon as the run is
 * recorded RUNNING (the actual pipeline runs in the background - see
 * {@link PipelineOrchestrator#triggerAsync}); 409 (via {@code IngestionAlreadyRunningException},
 * mapped globally by {@link ApiServer}) if one is already running for the same provider+timeframe.
 * Manual and scheduled ({@code DailyScheduler}) triggers both go through the same orchestrator
 * methods, so the resulting {@code ingestion_run} row is identical in shape either way.
 */
public final class IngestionTriggerHandler {

    private final PipelineOrchestrator pipelineOrchestrator;

    public IngestionTriggerHandler(PipelineOrchestrator pipelineOrchestrator) {
        this.pipelineOrchestrator = Objects.requireNonNull(pipelineOrchestrator, "pipelineOrchestrator must not be null");
    }

    public void register(Javalin app) {
        app.post("/api/v1/internal/ingestion/run", this::triggerRun);
    }

    @OpenApi(
            path = "/api/v1/internal/ingestion/run",
            methods = HttpMethod.POST,
            summary = "Trigger a manual ingestion pipeline run - defaults to BINANCE/D1 when the body is omitted or fields are absent",
            description = "Asynchronous: the 202 response confirms the run was recorded and started, not that it finished - "
                    + "the actual ingest/compute work continues in the background after this call returns. There is currently "
                    + "no dedicated endpoint to poll a specific run's completion by runId; to check whether new data has landed, "
                    + "poll GET /api/v1/candles/coverage or the freshness metadata (lastIngestionAt/candlesThrough/stale) on any "
                    + "read endpoint for the same provider/timeframe instead.",
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = IngestionTriggerRequest.class), required = false,
                    description = "Both fields optional; omit the body entirely, or omit either field, to default to BINANCE/D1"),
            responses = {
                    @OpenApiResponse(status = "202", content = @OpenApiContent(from = IngestionTriggerResponse.class),
                            description = "Run accepted and started; runId identifies the ingestion_run row, status is always \"STARTED\" at this point"),
                    @OpenApiResponse(status = "409", description = "A run is already RUNNING for this exact provider+timeframe combination - wait for it to finish before retrying")
            }
    )
    private void triggerRun(Context ctx) {
        IngestionTriggerRequest request = parseRequest(ctx);
        long runId = pipelineOrchestrator.triggerAsync(request.provider(), request.timeframe());
        ctx.status(202).json(new IngestionTriggerResponse(runId, "STARTED"));
    }

    private static IngestionTriggerRequest parseRequest(Context ctx) {
        String body = ctx.body();
        if (body == null || body.isBlank()) {
            return new IngestionTriggerRequest(null, null);
        }
        try {
            return ctx.bodyAsClass(IngestionTriggerRequest.class);
        } catch (Exception e) {
            throw new BadRequestResponse("Malformed ingestion trigger request body: " + e.getMessage());
        }
    }
}
