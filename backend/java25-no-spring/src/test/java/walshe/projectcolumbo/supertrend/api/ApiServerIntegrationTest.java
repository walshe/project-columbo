package walshe.projectcolumbo.supertrend.api;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.freshness.FreshnessStatus;
import walshe.projectcolumbo.supertrend.freshness.StaleDataException;
import walshe.projectcolumbo.supertrend.pipeline.IngestionAlreadyRunningException;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link ApiServer} end-to-end against a real Javalin instance ({@link JavalinTest}
 * starts it on a random localhost port) - routing, query-param validation, JSON responses,
 * OpenAPI/Swagger UI, and exception-to-status-code mapping (both Javalin's built-ins and this
 * codebase's domain exceptions) all in one pass, the way a real client would see them. Routes
 * registered here are throwaway test scaffolding - real endpoints land with groups 10+.
 */
class ApiServerIntegrationTest {

    private record Reading(String symbol, Timeframe timeframe, OffsetDateTime closeTime) {
    }

    private static Javalin testApp() {
        Javalin app = ApiServer.create();

        app.get("/json", ctx -> ctx.json(
                new Reading("BTCUSDT", Timeframe.D1, OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))));
        app.get("/markdown", ctx -> ctx.contentType("text/markdown").result("# Heading"));
        app.get("/watchlist", ctx -> ctx.contentType("text/plain").result("BTCUSDT\nETHUSDT"));
        app.get("/timeframe", ctx -> ctx.result(ctx.queryParamAsClass("timeframe", Timeframe.class).get().name()));
        app.get("/require-fresh", ctx -> ctx.result(String.valueOf(ctx.queryParamAsClass("requireFresh", Boolean.class).getOrDefault(false))));
        app.get("/badrequest", ctx -> {
            throw new BadRequestResponse("bad input");
        });
        app.get("/notfound", ctx -> {
            throw new NotFoundResponse("asset XYZ not found");
        });
        app.get("/conflict", ctx -> {
            throw new IngestionAlreadyRunningException(Provider.BINANCE, Timeframe.D1);
        });
        app.get("/stale", ctx -> {
            throw new StaleDataException(new FreshnessStatus(Timeframe.D1, OffsetDateTime.now(), null, false, true));
        });
        app.get("/boom", ctx -> {
            throw new RuntimeException("unexpected");
        });

        return app;
    }

    @Test
    void jsonResponseSerializesOffsetDateTimeAndEnum() {
        JavalinTest.test(testApp(), (server, client) -> {
            Response response = client.get("/json");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string())
                    .contains("\"symbol\":\"BTCUSDT\"")
                    .contains("\"timeframe\":\"D1\"")
                    .contains("2024-01-01T00:00:00Z");
        });
    }

    @Test
    void markdownResponseHasCorrectContentType() {
        JavalinTest.test(testApp(), (server, client) -> {
            Response response = client.get("/markdown");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.header("Content-Type")).contains("text/markdown");
            assertThat(response.body().string()).isEqualTo("# Heading");
        });
    }

    @Test
    void plainTextWatchlistResponseHasCorrectContentType() {
        JavalinTest.test(testApp(), (server, client) -> {
            Response response = client.get("/watchlist");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.header("Content-Type")).contains("text/plain");
            assertThat(response.body().string()).isEqualTo("BTCUSDT\nETHUSDT");
        });
    }

    @Test
    void caseInsensitiveTimeframeQueryParamIsParsed() {
        JavalinTest.test(testApp(), (server, client) -> {
            assertThat(client.get("/timeframe?timeframe=d1").body().string()).isEqualTo("D1");
            assertThat(client.get("/timeframe?timeframe=W1").body().string()).isEqualTo("W1");
        });
    }

    @Test
    void missingRequiredTimeframeParamIsRejectedWith400() {
        JavalinTest.test(testApp(), (server, client) -> assertThat(client.get("/timeframe").code()).isEqualTo(400));
    }

    @Test
    void booleanFlagDefaultsWhenAbsentAndParsesWhenPresent() {
        JavalinTest.test(testApp(), (server, client) -> {
            assertThat(client.get("/require-fresh").body().string()).isEqualTo("false");
            assertThat(client.get("/require-fresh?requireFresh=true").body().string()).isEqualTo("true");
        });
    }

    @Test
    void badRequestResponseMapsTo400Automatically() {
        JavalinTest.test(testApp(), (server, client) -> assertThat(client.get("/badrequest").code()).isEqualTo(400));
    }

    @Test
    void notFoundResponseMapsTo404Automatically() {
        JavalinTest.test(testApp(), (server, client) -> {
            Response response = client.get("/notfound");
            assertThat(response.code()).isEqualTo(404);
            assertThat(response.body().string()).contains("XYZ");
        });
    }

    @Test
    void unregisteredPathAlsoMapsTo404() {
        JavalinTest.test(testApp(), (server, client) -> assertThat(client.get("/does-not-exist").code()).isEqualTo(404));
    }

    @Test
    void ingestionAlreadyRunningExceptionMapsTo409InJavalinsOwnErrorEnvelopeShape() {
        JavalinTest.test(testApp(), (server, client) -> {
            Response response = client.get("/conflict");
            assertThat(response.code()).isEqualTo(409);
            String body = response.body().string();
            assertThat(body).contains("\"title\"").contains("\"status\":409").contains("\"type\"").contains("\"details\"");
        });
    }

    @Test
    void staleDataExceptionMapsTo503WithRetryAfterHeaderAndFreshnessDetails() {
        JavalinTest.test(testApp(), (server, client) -> {
            Response response = client.get("/stale");
            assertThat(response.code()).isEqualTo(503);
            assertThat(response.header("Retry-After")).isEqualTo("3600");
            String body = response.body().string();
            assertThat(body).contains("\"type\"").contains("expectedLatestCloseTime").contains("actualLatestCloseTime");
        });
    }

    @Test
    void unexpectedExceptionMapsTo500WithoutLeakingItsMessage() {
        JavalinTest.test(testApp(), (server, client) -> {
            Response response = client.get("/boom");
            assertThat(response.code()).isEqualTo(500);
            assertThat(response.body().string()).doesNotContain("unexpected");
        });
    }

    @Test
    void openApiSpecIsServedAndDescribesTheApi() {
        JavalinTest.test(testApp(), (server, client) -> {
            Response response = client.get("/openapi");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).contains("SuperTrend Core API");
        });
    }

    @Test
    void swaggerUiIsServedAtItsDefaultPath() {
        JavalinTest.test(testApp(), (server, client) -> {
            Response response = client.get("/swagger");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string().toLowerCase()).contains("swagger");
        });
    }
}
