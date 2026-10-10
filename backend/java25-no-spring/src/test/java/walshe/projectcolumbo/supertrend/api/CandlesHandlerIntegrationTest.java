package walshe.projectcolumbo.supertrend.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.chart.CandleChartService;
import walshe.projectcolumbo.supertrend.freshness.FreshnessService;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendDirection;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendResult;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.IngestionRunDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.persistence.SignalStateDao;
import walshe.projectcolumbo.supertrend.persistence.SuperTrendIndicatorDao;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalEvent;
import walshe.projectcolumbo.supertrend.signal.SignalState;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CandlesHandlerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // Candle i (0-based) opens at BASE_OPEN + i days and closes 1ms before the next one opens.
    private static final OffsetDateTime BASE_OPEN = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    static DataSource dataSource;
    static AssetDao assetDao;
    static CandleDao candleDao;
    static SuperTrendIndicatorDao superTrendIndicatorDao;
    static SignalStateDao signalStateDao;
    static IngestionRunDao ingestionRunDao;

    @BeforeAll
    static void migrate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        SchemaMigrator.migrate(dataSource);
        assetDao = new AssetDao(dataSource);
        candleDao = new CandleDao(dataSource);
        superTrendIndicatorDao = new SuperTrendIndicatorDao(dataSource);
        signalStateDao = new SignalStateDao(dataSource);
        ingestionRunDao = new IngestionRunDao(dataSource);
    }

    private static Javalin app(Clock clock) {
        Javalin app = ApiServer.create();
        FreshnessService freshnessService = new FreshnessService(candleDao, ingestionRunDao, clock);
        CandleChartService chartService = new CandleChartService(dataSource, candleDao, superTrendIndicatorDao, signalStateDao);
        new CandlesHandler(assetDao, chartService, freshnessService).register(app);
        new CandleCoverageHandler(candleDao, freshnessService).register(app);
        return app;
    }

    private static Javalin app() {
        return app(Clock.systemUTC());
    }

    @Test
    void defaultRequestReturnsLatest500AscendingWithOpenTimeEpochSecondsAndMetadata() {
        long assetId = seedAsset("CH1USDT", Provider.BINANCE, true);
        seedDailyCandles(assetId, 600);

        JavalinTest.test(app(), (server, client) -> {
            Response response = client.get("/api/v1/candles?symbol=CH1USDT&timeframe=d1");
            assertThat(response.code()).isEqualTo(200);
            JsonNode body = json(response);

            JsonNode candles = body.get("candles");
            assertThat(candles).hasSize(CandlesHandler.DEFAULT_LIMIT);
            assertThat(body.get("hasMore").asBoolean()).isTrue();
            assertThat(candles.get(0).get("time").asLong()).isEqualTo(openTime(100).toEpochSecond());
            assertThat(candles.get(candles.size() - 1).get("time").asLong()).isEqualTo(openTime(599).toEpochSecond());
            assertThat(times(candles)).isSorted();

            JsonNode last = candles.get(candles.size() - 1);
            assertThat(last.get("time").isIntegralNumber()).isTrue();
            assertThat(last.get("open").isNumber()).isTrue();
            assertThat(last.get("close").decimalValue()).isEqualByComparingTo("704");
            assertThat(last.get("volume").isNumber()).isTrue();

            assertThat(body.get("symbol").asText()).isEqualTo("CH1USDT");
            assertThat(body.get("timeframe").asText()).isEqualTo("D1");
            assertThat(body.get("assetClass").asText()).isEqualTo("CRYPTO");
            assertThat(body.get("tradingviewUrl").asText()).contains("tradingview.com");
            assertThat(body.has("stale")).isTrue();
            assertThat(body.has("candlesThrough")).isTrue();
            assertThat(body.has("supertrend")).isFalse();
            assertThat(body.has("signals")).isFalse();
        });
    }

    @Test
    void openTimeIsReportedAsUtcEpochSeconds() {
        long assetId = seedAsset("CH2USDT", Provider.BINANCE, true);
        seedDailyCandles(assetId, 1);

        JavalinTest.test(app(), (server, client) -> {
            JsonNode body = json(client.get("/api/v1/candles?symbol=CH2USDT&timeframe=D1"));
            assertThat(body.get("candles").get(0).get("time").asLong()).isEqualTo(1767225600L); // 2026-01-01T00:00:00Z
        });
    }

    @Test
    void pagingWithToEqualToFirstTimeWalksBackWithoutOverlapUntilHasMoreIsFalse() {
        long assetId = seedAsset("CH3USDT", Provider.BINANCE, true);
        seedDailyCandles(assetId, 25);

        JavalinTest.test(app(), (server, client) -> {
            List<Long> seen = new ArrayList<>();
            String to = null;
            boolean hasMore = true;
            int pages = 0;
            while (hasMore) {
                String path = "/api/v1/candles?symbol=CH3USDT&timeframe=D1&limit=10" + (to == null ? "" : "&to=" + to);
                JsonNode body = json(client.get(path));
                List<Long> page = times(body.get("candles"));
                seen.addAll(0, page);
                hasMore = body.get("hasMore").asBoolean();
                to = String.valueOf(page.getFirst());
                pages++;
            }
            assertThat(pages).isEqualTo(3);
            assertThat(seen).hasSize(25).doesNotHaveDuplicates().isSorted();
            assertThat(seen.getFirst()).isEqualTo(openTime(0).toEpochSecond());
        });
    }

    @Test
    void explicitRangeIsFromInclusiveToExclusiveAndAcceptsEquivalentFormats() {
        long assetId = seedAsset("CH4USDT", Provider.BINANCE, true);
        seedDailyCandles(assetId, 40);
        long expectedFirst = openTime(10).toEpochSecond();

        JavalinTest.test(app(), (server, client) -> {
            for (String from : List.of(String.valueOf(expectedFirst), "2026-01-11T00:00:00Z", "2026-01-11")) {
                JsonNode body = json(client.get("/api/v1/candles?symbol=CH4USDT&timeframe=D1&from=" + from + "&to=2026-01-16"));
                assertThat(times(body.get("candles"))).as("from=%s", from)
                        .containsExactly(expectedFirst, openTime(11).toEpochSecond(), openTime(12).toEpochSecond(),
                                openTime(13).toEpochSecond(), openTime(14).toEpochSecond());
                assertThat(body.get("hasMore").asBoolean()).isFalse();
            }
        });
    }

    @Test
    void invalidParametersAreRejectedWith400() {
        seedAsset("CH5USDT", Provider.BINANCE, true);

        JavalinTest.test(app(), (server, client) -> {
            for (String query : List.of(
                    "timeframe=D1",
                    "symbol=CH5USDT",
                    "symbol=CH5USDT&timeframe=H4",
                    "symbol=CH5USDT&timeframe=D1&limit=0",
                    "symbol=CH5USDT&timeframe=D1&limit=5001",
                    "symbol=CH5USDT&timeframe=D1&limit=abc",
                    "symbol=CH5USDT&timeframe=D1&from=yesterday",
                    "symbol=CH5USDT&timeframe=D1&from=20261001",
                    "symbol=CH5USDT&timeframe=D1&from=2026-02-01&to=2026-02-01",
                    "symbol=CH5USDT&timeframe=D1&include=ema")) {
                assertThat(client.get("/api/v1/candles?" + query).code()).as(query).isEqualTo(400);
            }
        });
    }

    @Test
    void unknownOrInactiveSymbolIs404() {
        long inactiveId = seedAsset("CH6USDT", Provider.BINANCE, false);
        seedDailyCandles(inactiveId, 5);

        JavalinTest.test(app(), (server, client) -> {
            assertThat(client.get("/api/v1/candles?symbol=CH6USDT&timeframe=D1").code()).isEqualTo(404);
            assertThat(client.get("/api/v1/candles?symbol=NOPEUSDT&timeframe=D1").code()).isEqualTo(404);
            assertThat(client.get("/api/v1/candles?symbol=ch1usdt&timeframe=D1").code()).isEqualTo(404);
        });
    }

    @Test
    void activeSymbolSharedAcrossProvidersIs409ListingProviders() {
        seedAsset("CH7USDT", Provider.BINANCE, true);
        seedAsset("CH7USDT", Provider.MEXC, true);

        JavalinTest.test(app(), (server, client) -> {
            Response response = client.get("/api/v1/candles?symbol=CH7USDT&timeframe=D1");
            assertThat(response.code()).isEqualTo(409);
            assertThat(response.body().string()).contains("BINANCE").contains("MEXC");
        });
    }

    @Test
    void superTrendOverlayIsAlignedToReturnedCandlesAndOnlyReportsFlips() {
        long assetId = seedAsset("CH8USDT", Provider.BINANCE, true);
        seedDailyCandles(assetId, 20);
        for (int i = 0; i < 20; i++) {
            SuperTrendDirection direction = i < 12 ? SuperTrendDirection.DOWN : SuperTrendDirection.UP;
            superTrendIndicatorDao.upsert(assetId, Timeframe.D1, new SuperTrendResult(closeTime(i), BigDecimal.ONE,
                    BigDecimal.valueOf(120), BigDecimal.valueOf(80), BigDecimal.valueOf(direction == SuperTrendDirection.UP ? 80 : 120), direction));
            SignalEvent event = i == 12 ? SignalEvent.BULLISH_REVERSAL : i == 3 ? SignalEvent.BEARISH_REVERSAL : SignalEvent.NONE;
            signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, closeTime(i),
                    i < 12 ? TrendState.BEARISH : TrendState.BULLISH, event));
        }

        JavalinTest.test(app(), (server, client) -> {
            JsonNode body = json(client.get("/api/v1/candles?symbol=CH8USDT&timeframe=D1&limit=10&include=SuperTrend"));
            Set<Long> candleTimes = new HashSet<>(times(body.get("candles")));

            JsonNode supertrend = body.get("supertrend");
            assertThat(supertrend).hasSize(10);
            assertThat(candleTimes).containsAll(times(supertrend));
            assertThat(supertrend.get(0).get("direction").asText()).isEqualTo("DOWN"); // candle 10
            assertThat(supertrend.get(2).get("direction").asText()).isEqualTo("UP");   // candle 12
            assertThat(supertrend.get(2).get("value").decimalValue()).isEqualByComparingTo("80");

            // The BEARISH_REVERSAL on candle 3 is outside the 10-bar window; NONE rows never appear.
            JsonNode signals = body.get("signals");
            assertThat(signals).hasSize(1);
            assertThat(signals.get(0).get("event").asText()).isEqualTo("BULLISH_REVERSAL");
            assertThat(signals.get(0).get("time").asLong()).isEqualTo(openTime(12).toEpochSecond());
        });
    }

    @Test
    void emptyWindowIs200WithEmptyArrays() {
        long assetId = seedAsset("CH9USDT", Provider.BINANCE, true);
        seedDailyCandles(assetId, 5);

        JavalinTest.test(app(), (server, client) -> {
            JsonNode body = json(client.get("/api/v1/candles?symbol=CH9USDT&timeframe=W1&include=supertrend"));
            assertThat(body.get("candles")).isEmpty();
            assertThat(body.get("hasMore").asBoolean()).isFalse();
            assertThat(body.get("supertrend")).isEmpty();
            assertThat(body.get("signals")).isEmpty();
        });
    }

    @Test
    void staleDataIsStillServedWith200() {
        long assetId = seedAsset("CH10USDT", Provider.BINANCE, true);
        seedDailyCandles(assetId, 3);
        Clock farFuture = Clock.fixed(OffsetDateTime.of(2030, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

        JavalinTest.test(app(farFuture), (server, client) -> {
            Response response = client.get("/api/v1/candles?symbol=CH10USDT&timeframe=D1");
            assertThat(response.code()).isEqualTo(200);
            JsonNode body = json(response);
            assertThat(body.get("stale").asBoolean()).isTrue();
            assertThat(body.get("candles")).hasSize(3);
        });
    }

    @Test
    void coverageEndpointStillRoutesAlongsideCandles() {
        JavalinTest.test(app(), (server, client) -> {
            Response response = client.get("/api/v1/candles/coverage");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).contains("\"D1\"").contains("\"assetCount\"");
        });
    }

    private static JsonNode json(Response response) throws Exception {
        return JsonSupport.OBJECT_MAPPER.readTree(response.body().string());
    }

    private static List<Long> times(JsonNode array) {
        List<Long> times = new ArrayList<>();
        array.forEach(node -> times.add(node.get("time").asLong()));
        return times;
    }

    private static OffsetDateTime openTime(int index) {
        return BASE_OPEN.plusDays(index);
    }

    private static OffsetDateTime closeTime(int index) {
        return BASE_OPEN.plusDays(index + 1).minusNanos(1_000_000);
    }

    private static long seedAsset(String symbol, Provider provider, boolean active) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO asset (symbol, provider, active) VALUES (?, ?::provider, ?) RETURNING id")) {
            statement.setString(1, symbol);
            statement.setString(2, provider.name());
            statement.setBoolean(3, active);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong("id");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void seedDailyCandles(long assetId, int count) {
        for (int i = 0; i < count; i++) {
            candleDao.upsert(assetId, new Candle(openTime(i), closeTime(i), Timeframe.D1,
                    BigDecimal.valueOf(100 + i), BigDecimal.valueOf(110 + i), BigDecimal.valueOf(90 + i),
                    BigDecimal.valueOf(105 + i), BigDecimal.valueOf(1000)));
        }
    }
}
