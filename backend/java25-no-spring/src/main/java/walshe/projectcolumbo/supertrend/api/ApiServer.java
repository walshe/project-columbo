package walshe.projectcolumbo.supertrend.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import walshe.projectcolumbo.supertrend.freshness.FreshnessStatus;
import walshe.projectcolumbo.supertrend.freshness.StaleDataException;
import walshe.projectcolumbo.supertrend.pipeline.IngestionAlreadyRunningException;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalSort;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import java.util.Locale;
import java.util.Map;

/**
 * Builds a configured, not-yet-started Javalin app: JSON mapping (shared Jackson
 * {@link JsonSupport#OBJECT_MAPPER}), an OpenAPI spec + Swagger UI, a case-insensitive
 * {@link Timeframe} query-param converter, and exception-to-status-code mapping for the domain
 * exceptions Javalin doesn't already know about. Javalin's own {@code HttpResponseException}
 * subclasses ({@code BadRequestResponse}, {@code NotFoundResponse}, {@code ConflictResponse}, ...)
 * are mapped to the right status automatically - no registration needed for those.
 * <p>
 * Not started or wired into {@code Main} yet; real routes land with openspec tasks.md groups 10+.
 */
public final class ApiServer {

    private static final Logger LOG = LoggerFactory.getLogger(ApiServer.class);
    private static final String OPENAPI_DOCS_PATH = "/openapi";
    // Matches the "type" Javalin's own HttpResponseExceptionMapper falls back to for exception
    // types it has no dedicated docs page for - keeps our hand-mapped responses (409/503/500)
    // in the same {title, status, type, details} envelope as Javalin's built-in 400/404/etc.
    private static final String ERROR_TYPE_URL = "https://javalin.io/documentation#error-responses";

    private ApiServer() {
    }

    public static Javalin create() {
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(JsonSupport.OBJECT_MAPPER, false));
            config.validation.register(Timeframe.class, value -> Timeframe.valueOf(value.toUpperCase(Locale.ROOT)));
            config.validation.register(TrendState.class, value -> TrendState.valueOf(value.toUpperCase(Locale.ROOT)));
            config.validation.register(SignalSort.class, value -> SignalSort.valueOf(value.toUpperCase(Locale.ROOT)));
            config.validation.register(SummaryFormat.class, value -> SummaryFormat.valueOf(value.toUpperCase(Locale.ROOT)));

            config.registerPlugin(new OpenApiPlugin(openApiConfig -> openApiConfig
                    .withDocumentationPath(OPENAPI_DOCS_PATH)
                    .withDefinitionConfiguration((version, builder) -> builder.withInfo(info -> {
                        info.title("SuperTrend Core API");
                        info.version("0.1.0");
                        info.description("SuperTrend-only signals, market-breadth pulse, and ingestion trigger.");
                    }))));
            config.registerPlugin(new SwaggerPlugin(swaggerConfig -> swaggerConfig.setDocumentationPath(OPENAPI_DOCS_PATH)));
        });

        registerErrorMapping(app);
        return app;
    }

    private static void registerErrorMapping(Javalin app) {
        app.exception(IngestionAlreadyRunningException.class, (e, ctx) ->
                writeError(ctx, 409, e.getMessage(), Map.of()));

        app.exception(StaleDataException.class, (e, ctx) -> {
            ctx.header("Retry-After", String.valueOf(e.retryAfterSeconds()));
            FreshnessStatus status = e.status();
            writeError(ctx, 503, e.getMessage(), Map.of(
                    "expectedLatestCloseTime", String.valueOf(status.expectedLatestCloseTime()),
                    "actualLatestCloseTime", String.valueOf(status.actualLatestCloseTime())
            ));
        });

        app.exception(Exception.class, (e, ctx) -> {
            LOG.error("Unhandled error for {} {}", ctx.method(), ctx.path(), e);
            writeError(ctx, 500, "Internal server error", Map.of());
        });
    }

    /** Matches the {title, status, type, details} envelope Javalin's own HttpResponseException mapper produces. */
    private static void writeError(Context ctx, int status, String title, Map<String, String> details) {
        ctx.status(status).json(Map.of(
                "title", title,
                "status", status,
                "type", ERROR_TYPE_URL,
                "details", details
        ));
    }
}
