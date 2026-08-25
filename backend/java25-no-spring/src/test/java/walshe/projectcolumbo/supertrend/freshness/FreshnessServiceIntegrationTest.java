package walshe.projectcolumbo.supertrend.freshness;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.IngestionRunDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.pipeline.IngestionRunOutcome;
import walshe.projectcolumbo.supertrend.pipeline.IngestionRunStatus;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class FreshnessServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime EXPECTED_D1_BOUNDARY_DAY = OffsetDateTime.of(2024, 3, 14, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime NOW = EXPECTED_D1_BOUNDARY_DAY.plusDays(1).plusHours(1); // "today" 01:00 UTC

    static DataSource dataSource;
    static AssetDao assetDao;
    static CandleDao candleDao;
    static IngestionRunDao ingestionRunDao;
    static Clock clock;

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
        ingestionRunDao = new IngestionRunDao(dataSource);
        clock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);
    }

    @Test
    void freshnessMetadataIsNullBeforeAnyIngestion() {
        // W1 specifically: every other test in this class only ever touches D1, so this stays
        // true regardless of test execution order without needing explicit ordering/isolation.
        FreshnessService service = new FreshnessService(candleDao, ingestionRunDao, clock);

        FreshnessMetadata metadata = service.metadataFor(Timeframe.W1);

        assertThat(metadata.lastSuccessfulIngestionAt()).isNull();
        assertThat(metadata.latestCandleDate()).isNull();
    }

    @Test
    void evaluateReflectsUpToDateCandleData() {
        long assetId = seedAsset("FRESH1USDT");
        seedCandle(assetId, EXPECTED_D1_BOUNDARY_DAY); // matches the expected D1 boundary at NOW

        FreshnessStatus status = new FreshnessService(candleDao, ingestionRunDao, clock).evaluate(Timeframe.D1);

        assertThat(status.upToDate()).isTrue();
        assertThat(status.staleBeyondGraceWindow()).isFalse();
    }

    @Test
    void requireFreshThrowsOnceStaleBeyondTheGraceWindow() {
        long assetId = seedAsset("FRESH2USDT");
        seedCandle(assetId, EXPECTED_D1_BOUNDARY_DAY.minusDays(10)); // way behind, no ingestion since
        Clock farPastGrace = Clock.fixed(NOW.plusHours(6).toInstant(), ZoneOffset.UTC);
        FreshnessService service = new FreshnessService(candleDao, ingestionRunDao, farPastGrace);

        assertThatThrownBy(() -> service.requireFresh(Timeframe.D1)).isInstanceOf(StaleDataException.class);
    }

    @Test
    void metadataReflectsSuccessfulIngestionAndLatestCandle() {
        long assetId = seedAsset("FRESH3USDT");
        seedCandle(assetId, EXPECTED_D1_BOUNDARY_DAY);
        long runId = ingestionRunDao.start(Timeframe.D1, 1, NOW);
        ingestionRunDao.complete(runId, new IngestionRunOutcome(IngestionRunStatus.SUCCESS, NOW.plusMinutes(1), 60_000, 1, 0, 0, 0, null));

        FreshnessMetadata metadata = new FreshnessService(candleDao, ingestionRunDao, clock).metadataFor(Timeframe.D1);

        assertThat(metadata.lastSuccessfulIngestionAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(metadata.latestCandleDate()).isEqualTo(EXPECTED_D1_BOUNDARY_DAY);
    }

    private static long seedAsset(String symbol) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO asset (symbol, provider, active) VALUES (?, ?::provider, true)")) {
            statement.setString(1, symbol);
            statement.setString(2, Provider.BINANCE.name());
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

    private static void seedCandle(long assetId, OffsetDateTime closeTime) {
        Candle candle = new Candle(
                closeTime.minusDays(1),
                closeTime,
                Timeframe.D1,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(110),
                BigDecimal.valueOf(90),
                BigDecimal.valueOf(105),
                BigDecimal.valueOf(1000)
        );
        candleDao.upsert(assetId, candle);
    }
}
