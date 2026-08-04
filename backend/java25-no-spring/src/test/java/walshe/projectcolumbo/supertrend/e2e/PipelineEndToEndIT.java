package walshe.projectcolumbo.supertrend.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Builds this module's actual Docker image via Testcontainers' {@link ImageFromDockerfile} and
 * runs it against a real Postgres container plus a WireMock container stubbing Binance's klines
 * API, then drives the full pipeline (ingest -&gt; D1 indicators -&gt; D1 signals -&gt; D1 pulse -&gt;
 * W1 rollup -&gt; W1 indicators -&gt; W1 signals -&gt; W1 pulse) through real HTTP exactly as a client
 * would. This is the automated, repeatable counterpart to the one-time manual "Group 16"
 * validation documented in {@code openspec/changes/supertrend-core-java25-rewrite/design.md},
 * which hit the real Binance API and can't safely be repeated in CI (rate limits, flakiness,
 * non-reproducible market data).
 * <p>
 * Opt-in only - run with {@code mvn verify -Pe2e}. Not part of the default {@code mvn test}/{@code
 * mvn verify} run: building the Docker image and running three containers is much slower than
 * every other test in this module. See {@code pom.xml}'s {@code e2e} profile.
 * <p>
 * Candle data is a fixed, synthetic 2020 date range rather than "N days before now" - candles
 * from a fixed point in the past are always finalized (never rot as real time passes), so this
 * test's fixtures never need updating.
 */
class PipelineEndToEndIT {

    private static final int APP_PORT = 8080;
    private static final int WIREMOCK_PORT = 8080;
    private static final OffsetDateTime SERIES_START = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final int SERIES_DAYS = 220; // comfortably clears W1 SuperTrend's ~147-day ATR warm-up
    private static final String BACKFILL_START = "2019-06-01T00:00:00Z"; // before the series, and always >147 days in the past

    private static final String BEARISH_SYMBOL = "BTCUSDT";
    private static final String INVALID_SYMBOL = "ETHUSDT";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static Network network;
    private static PostgreSQLContainer<?> postgres;
    private static GenericContainer<?> wiremock;
    private static GenericContainer<?> app;
    private static String appBaseUrl;

    @BeforeAll
    static void startStack() throws IOException {
        network = Network.newNetwork();

        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withNetwork(network)
                .withNetworkAliases("postgres")
                .withDatabaseName("supertrend_core")
                .withUsername("postgres")
                .withPassword("postgres");
        postgres.start();

        wiremock = new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.13.2"))
                .withNetwork(network)
                .withNetworkAliases("binance-stub")
                .withExposedPorts(WIREMOCK_PORT)
                .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));
        for (StubMapping stub : buildStubMappings()) {
            wiremock.withCopyToContainer(
                    MountableFile.forHostPath(writeTempFile(stub.name(), stub.json())),
                    "/home/wiremock/mappings/" + stub.name());
        }
        wiremock.start();

        // ImageFromDockerfile doesn't infer its build context from the Dockerfile's location - the
        // context files it needs (matching exactly what the Dockerfile's COPY instructions expect)
        // have to be added explicitly via withFileFromPath. withDockerfile(Path) (a *local
        // filesystem* path) looked like the natural way to point at it, but that method requires a
        // "base directory" that's never set in this Transferable-upload build mode and fails with a
        // NullPointerException before a single build step runs - withDockerfilePath(String) (a path
        // *within the already-uploaded context*, alongside transferring the Dockerfile itself the
        // same way as pom.xml/src) is the working equivalent.
        ImageFromDockerfile appImage = new ImageFromDockerfile()
                .withFileFromPath("pom.xml", Path.of("pom.xml"))
                .withFileFromPath("src", Path.of("src"))
                .withFileFromPath("Dockerfile", Path.of("Dockerfile"))
                .withDockerfilePath("Dockerfile");
        app = new GenericContainer<>(appImage)
                .withNetwork(network)
                .withExposedPorts(APP_PORT)
                .withEnv("SUPERTREND_DB_URL", "jdbc:postgresql://postgres:5432/supertrend_core")
                .withEnv("SUPERTREND_DB_USER", "postgres")
                .withEnv("SUPERTREND_DB_PASSWORD", "postgres")
                .withEnv("SUPERTREND_BACKFILL_START", BACKFILL_START)
                .withEnv("SUPERTREND_BINANCE_BASE_URL", "http://binance-stub:" + WIREMOCK_PORT)
                .waitingFor(Wait.forHttp("/api/v1/candles/coverage").forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(5));
        app.start();

        appBaseUrl = "http://" + app.getHost() + ":" + app.getMappedPort(APP_PORT);
    }

    @AfterAll
    static void stopStack() {
        if (app != null) {
            app.stop();
        }
        if (wiremock != null) {
            wiremock.stop();
        }
        if (postgres != null) {
            postgres.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    @Test
    void fullPipelineIngestsComputesAndServesRealSignalsOverRealHttp() throws Exception {
        HttpResponse<String> trigger = post("/api/v1/internal/ingestion/run");
        assertThat(trigger.statusCode()).isEqualTo(202);

        awaitPipelineCompletion();

        JsonNode d1Signals = getJson("/api/v1/signals?timeframe=D1").get("signals");
        assertThat(symbolsIn(d1Signals)).contains("SOLUSDT").doesNotContain(INVALID_SYMBOL);
        assertThat(trendStateOf(d1Signals, BEARISH_SYMBOL)).isEqualTo("BEARISH");
        assertThat(trendStateOf(d1Signals, "SOLUSDT")).isEqualTo("BULLISH");

        JsonNode w1Signals = getJson("/api/v1/signals?timeframe=W1").get("signals");
        assertThat(trendStateOf(w1Signals, BEARISH_SYMBOL)).isEqualTo("BEARISH");
        assertThat(trendStateOf(w1Signals, "SOLUSDT")).isEqualTo("BULLISH");

        JsonNode summary = getJson("/api/v1/summary?timeframe=D1");
        assertThat(summary.get("timeframe").asText()).isEqualTo("D1");
        assertThat(summary.get("pulse").get("bullishCount").asInt()).isGreaterThan(0);

        JsonNode coverage = getJson("/api/v1/candles/coverage");
        assertThat(coverage.get("D1").get("assetCount").asLong()).isEqualTo(59); // 60 seeded - 1 deactivated invalid symbol
        assertThat(coverage.get("W1").get("assetCount").asLong()).isEqualTo(59);

        // Idempotency: a second trigger either starts cleanly or is rejected because the first
        // hasn't finished yet - both are correct, a 5xx would not be.
        HttpResponse<String> secondTrigger = post("/api/v1/internal/ingestion/run");
        assertThat(secondTrigger.statusCode()).isIn(202, 409);
        if (secondTrigger.statusCode() == 202) {
            awaitPipelineCompletion();
        }
        JsonNode coverageAfterRepeat = getJson("/api/v1/candles/coverage");
        assertThat(coverageAfterRepeat.get("D1").get("assetCount").asLong()).isEqualTo(59);
    }

    private static void awaitPipelineCompletion() throws InterruptedException {
        Duration timeout = Duration.ofMinutes(3);
        Duration pollInterval = Duration.ofSeconds(2);
        OffsetDateTime deadline = OffsetDateTime.now().plus(timeout);
        while (OffsetDateTime.now().isBefore(deadline)) {
            try {
                JsonNode w1Signals = getJson("/api/v1/signals?timeframe=W1").get("signals");
                if (w1Signals.size() >= 55 && noneUnknown(w1Signals)) {
                    return;
                }
            } catch (Exception e) {
                // app may still be mid-migration/startup on the very first poll - keep retrying
            }
            Thread.sleep(pollInterval.toMillis());
        }
        throw new AssertionError("Pipeline did not complete W1 signal detection within " + timeout);
    }

    private static boolean noneUnknown(JsonNode signals) {
        for (JsonNode entry : signals) {
            if ("UNKNOWN".equals(entry.get("trendState").asText())) {
                return false;
            }
        }
        return true;
    }

    private static List<String> symbolsIn(JsonNode signals) {
        return java.util.stream.StreamSupport.stream(signals.spliterator(), false)
                .map(entry -> entry.get("symbol").asText())
                .toList();
    }

    private static String trendStateOf(JsonNode signals, String symbol) {
        for (JsonNode entry : signals) {
            if (symbol.equals(entry.get("symbol").asText())) {
                return entry.get("trendState").asText();
            }
        }
        throw new AssertionError("No signal entry for " + symbol);
    }

    private static HttpResponse<String> post(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(appBaseUrl + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode getJson(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(appBaseUrl + path)).GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("GET %s", path).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private record StubMapping(String name, String json) {
    }

    private static List<StubMapping> buildStubMappings() {
        return List.of(
                new StubMapping("bearish-default.json", stubMapping(null, 200, klinesBody(false), 100)),
                new StubMapping("bearish-btc.json", stubMapping(BEARISH_SYMBOL, 200, klinesBody(false), 1)),
                new StubMapping("bullish-default.json", stubMapping(null, 200, klinesBody(true), 100)),
                new StubMapping("invalid-symbol.json", stubMapping(INVALID_SYMBOL, 400, invalidSymbolBody(), 1))
        );
    }

    /** Bullish is the catch-all (priority 100); BTC and the invalid symbol are higher-priority (lower number) overrides. */
    private static String stubMapping(String symbolOrNullForCatchAll, int status, JsonNode body, int priority) {
        ObjectNode root = JSON.createObjectNode();
        root.put("priority", priority);
        ObjectNode request = root.putObject("request");
        request.put("method", "GET");
        request.put("urlPathPattern", "/api/v3/klines");
        if (symbolOrNullForCatchAll != null) {
            request.putObject("queryParameters").putObject("symbol").put("equalTo", symbolOrNullForCatchAll);
        }
        ObjectNode response = root.putObject("response");
        response.put("status", status);
        response.set("jsonBody", body);
        response.putObject("headers").put("Content-Type", "application/json");
        return root.toString();
    }

    private static JsonNode invalidSymbolBody() {
        ObjectNode body = JSON.createObjectNode();
        body.put("code", -1121);
        body.put("msg", "Invalid symbol.");
        return body;
    }

    /**
     * Binance kline array shape: [openTime, open, high, low, close, baseVolume, closeTime,
     * quoteVolume, trades, takerBuyBase, takerBuyQuote, unused]. Only indices 0/1/2/3/4/6/7 are
     * ever read by {@code BinanceMarketDataProvider}; the rest are populated anyway to mirror the
     * real API shape.
     */
    private static JsonNode klinesBody(boolean bullish) {
        ArrayNode rows = JSON.createArrayNode();
        double base = 100.0;
        // Multiplicative (percentage) trend rather than linear - asymptotically approaches zero
        // but can never cross it, however long SERIES_DAYS gets, so this can never produce a
        // non-positive price (which Candle's own validation would reject).
        double dailyFactor = bullish ? 1.005 : 0.995;
        for (int day = 0; day < SERIES_DAYS; day++) {
            OffsetDateTime open = SERIES_START.plusDays(day);
            OffsetDateTime close = open.plusDays(1).minusNanos(1_000_000);
            double openPrice = base * Math.pow(dailyFactor, day);
            double closePrice = base * Math.pow(dailyFactor, day + 1);
            double high = Math.max(openPrice, closePrice) * 1.01;
            double low = Math.min(openPrice, closePrice) * 0.99;

            ArrayNode row = rows.addArray();
            row.add(open.toInstant().toEpochMilli());
            row.add(String.format("%.4f", openPrice));
            row.add(String.format("%.4f", high));
            row.add(String.format("%.4f", low));
            row.add(String.format("%.4f", closePrice));
            row.add("1000.0000");
            row.add(close.toInstant().toEpochMilli());
            row.add("100000.0000");
            row.add(500);
            row.add("500.0000");
            row.add("50000.0000");
            row.add("0");
        }
        return rows;
    }

    private static Path writeTempFile(String name, String content) throws IOException {
        Path path = Files.createTempFile("wiremock-", "-" + name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        path.toFile().deleteOnExit();
        return path;
    }
}
