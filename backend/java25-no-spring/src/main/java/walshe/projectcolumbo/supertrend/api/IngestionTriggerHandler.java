package walshe.projectcolumbo.supertrend.api;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import walshe.projectcolumbo.supertrend.pipeline.PipelineOrchestrator;

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
        this.pipelineOrchestrator = pipelineOrchestrator;
    }

    public void register(Javalin app) {
        app.post("/api/v1/internal/ingestion/run", this::triggerRun);
    }

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
