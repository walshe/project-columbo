package walshe.projectcolumbo.supertrend.ingestion;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.shared.AssetVenue;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

// The 60 migration-seeded assets are deactivated in @BeforeAll so ingestDaily()'s per-asset
// loop (with its 200ms polite delay) only touches the assets each test actually cares about.
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CandleIngestionServiceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime BACKFILL_START = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime NOW = OffsetDateTime.of(2024, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    static DataSource dataSource;
    static AssetDao assetDao;
    static CandleDao candleDao;
    static IngestionConfig ingestionConfig;
    static Clock clock;

    @BeforeAll
    static void setUp() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        SchemaMigrator.migrate(dataSource);
        assetDao = new AssetDao(dataSource);
        candleDao = new CandleDao(dataSource);
        ingestionConfig = new IngestionConfig(BACKFILL_START);
        clock = Clock.fixed(Instant.from(NOW), ZoneOffset.UTC);

        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE asset SET active = false");
        }
    }

    @Test
    @Order(1)
    void ingestDailyInsertsFetchedCandlesForActiveAssets() {
        long assetId = seedAsset("ING1USDT");
        FakeMarketDataProvider provider = new FakeMarketDataProvider();
        provider.onFetch("ING1USDT", () -> List.of(candle(1), candle(2)));

        IngestionStats stats = ingestionService(provider).ingestDaily();

        assertThat(stats.insertedCount()).isEqualTo(2);
        assertThat(stats.errorCount()).isZero();
        assertThat(candleDao.findByAssetAndTimeframe(assetId, Timeframe.D1)).hasSize(2);
    }

    @Test
    @Order(2)
    void reIngestingTheSameCandlesIsIdempotent() {
        seedAsset("ING2USDT");
        FakeMarketDataProvider provider = new FakeMarketDataProvider();
        provider.onFetch("ING2USDT", () -> List.of(candle(1), candle(2)));
        CandleIngestionService service = ingestionService(provider);

        IngestionStats first = service.ingestDaily();
        IngestionStats second = service.ingestDaily();

        assertThat(first.insertedCount()).isEqualTo(2);
        assertThat(second.insertedCount()).isZero();
        assertThat(second.unchangedCount()).isEqualTo(2);
    }

    @Test
    @Order(3)
    void invalidSymbolDeactivatesTheAsset() {
        long assetId = seedAsset("ING3USDT");
        FakeMarketDataProvider provider = new FakeMarketDataProvider();
        provider.onFetch("ING3USDT", () -> {
            throw new InvalidSymbolException("ING3USDT");
        });

        IngestionStats stats = ingestionService(provider).ingestDaily();

        assertThat(stats.errorCount()).isEqualTo(1);
        assertThat(assetDao.findAllActive()).extracting(a -> a.id()).doesNotContain(assetId);
    }

    @Test
    @Order(4)
    void oneAssetsFailureDoesNotAbortTheRun() {
        seedAsset("ING4AUSDT");
        seedAsset("ING4BUSDT");
        FakeMarketDataProvider provider = new FakeMarketDataProvider();
        provider.onFetch("ING4AUSDT", () -> {
            throw new RuntimeException("provider unavailable");
        });
        provider.onFetch("ING4BUSDT", () -> List.of(candle(1)));

        IngestionStats stats = ingestionService(provider).ingestDaily();

        assertThat(stats.errorCount()).isEqualTo(1);
        assertThat(stats.insertedCount()).isEqualTo(1);
    }

    @Test
    @Order(5)
    void routesEachAssetToTheProviderForItsVenue() {
        seedAsset("ING5SPOTUSDT", AssetVenue.SPOT);
        seedAsset("ING5FUTUSDT", AssetVenue.FUTURES);
        FakeMarketDataProvider spotProvider = new FakeMarketDataProvider();
        spotProvider.onFetch("ING5SPOTUSDT", () -> List.of(candle(1)));
        FakeMarketDataProvider futuresProvider = new FakeMarketDataProvider();
        futuresProvider.onFetch("ING5FUTUSDT", () -> List.of(candle(1), candle(2)));
        CandleIngestionService service = new CandleIngestionService(
                assetDao, candleDao,
                Map.of(AssetVenue.SPOT, spotProvider, AssetVenue.FUTURES, futuresProvider),
                ingestionConfig, clock);

        IngestionStats stats = service.ingestDaily();

        assertThat(stats.insertedCount()).isEqualTo(3);
        assertThat(stats.errorCount()).isZero();
    }

    @Test
    @Order(6)
    void routesAnExchangeVenueAssetToItsConfiguredProviderAlongsideSpotAndFutures() {
        seedAsset("ING6SPOTUSDT", AssetVenue.SPOT);
        seedAsset("ING6FUTUSDT", AssetVenue.FUTURES);
        seedAsset("AAPL", AssetVenue.EXCHANGE);
        FakeMarketDataProvider spotProvider = new FakeMarketDataProvider();
        spotProvider.onFetch("ING6SPOTUSDT", () -> List.of(candle(1)));
        FakeMarketDataProvider futuresProvider = new FakeMarketDataProvider();
        futuresProvider.onFetch("ING6FUTUSDT", () -> List.of(candle(1)));
        FakeMarketDataProvider exchangeProvider = new FakeMarketDataProvider();
        exchangeProvider.onFetch("AAPL", () -> List.of(candle(1), candle(2)));
        CandleIngestionService service = new CandleIngestionService(
                assetDao, candleDao,
                Map.of(AssetVenue.SPOT, spotProvider, AssetVenue.FUTURES, futuresProvider, AssetVenue.EXCHANGE, exchangeProvider),
                ingestionConfig, clock);

        IngestionStats stats = service.ingestDaily();

        assertThat(stats.insertedCount()).isEqualTo(4);
        assertThat(stats.errorCount()).isZero();
    }

    @Test
    @Order(7)
    void zeroCandlesDuringAnExpectedFetchWindowIsLoggedNotSilent() {
        // Regression test for the production bug: a provider returning a valid-but-empty
        // response for an asset that wasn't already caught up went completely unlogged,
        // indistinguishable from "nothing to do". slf4j-simple (this project's only logging
        // backend, no test-capture API) logs to System.err by default - captured here directly
        // rather than pulling in a logging framework just to assert one warning line.
        seedAsset("ING7USDT");
        FakeMarketDataProvider provider = new FakeMarketDataProvider();
        provider.onFetch("ING7USDT", List::of);

        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured));
        try {
            ingestionService(provider).ingestDaily();
        } finally {
            System.setErr(originalErr);
        }

        // This run also re-touches every asset seeded by earlier tests in this class (none of
        // them are deactivated), so other symbols may log the same warning too - match the
        // symbol and the phrase together, not just each independently anywhere in the buffer.
        assertThat(captured.toString()).containsIgnoringCase("zero candles for ING7USDT");
    }

    private static CandleIngestionService ingestionService(FakeMarketDataProvider provider) {
        return new CandleIngestionService(
                assetDao, candleDao,
                Map.of(AssetVenue.SPOT, provider, AssetVenue.FUTURES, provider),
                ingestionConfig, clock);
    }

    private static long seedAsset(String symbol) {
        return seedAsset(symbol, AssetVenue.SPOT);
    }

    private static long seedAsset(String symbol, AssetVenue venue) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO asset (symbol, provider, active, venue) VALUES (?, ?::provider, true, ?::asset_venue)")) {
            statement.setString(1, symbol);
            statement.setString(2, Provider.BINANCE.name());
            statement.setString(3, venue.name());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return assetDao.findAllActive().stream()
                .filter(a -> a.symbol().equals(symbol))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static Candle candle(int dayOffset) {
        OffsetDateTime closeTime = BACKFILL_START.plusDays(dayOffset);
        return new Candle(
                closeTime.minusDays(1),
                closeTime,
                Timeframe.D1,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(110),
                BigDecimal.valueOf(90),
                BigDecimal.valueOf(105),
                BigDecimal.valueOf(1000)
        );
    }

    private static final class FakeMarketDataProvider implements MarketDataProvider {
        private final Map<String, Supplier<List<Candle>>> behaviors = new HashMap<>();

        void onFetch(String symbol, Supplier<List<Candle>> behavior) {
            behaviors.put(symbol, behavior);
        }

        @Override
        public List<Candle> fetchDailyCandles(String symbol, long startTimeMs, long endTimeMs) {
            Supplier<List<Candle>> behavior = behaviors.get(symbol);
            return behavior == null ? List.of() : behavior.get();
        }
    }
}
