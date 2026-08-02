package walshe.projectcolumbo.supertrend.signal;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.AssetLiquidityDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.persistence.SignalStateDao;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class TrendAlignmentServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime NOW = OffsetDateTime.of(2024, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime W1_CLOSE = OffsetDateTime.of(2024, 1, 14, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime D1_CLOSE = OffsetDateTime.of(2024, 1, 14, 0, 0, 0, 0, ZoneOffset.UTC);

    static DataSource dataSource;
    static AssetDao assetDao;
    static SignalStateDao signalStateDao;
    static TrendAlignmentService trendAlignmentService;

    @BeforeAll
    static void migrate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        SchemaMigrator.migrate(dataSource);
        assetDao = new AssetDao(dataSource);
        CandleDao candleDao = new CandleDao(dataSource);
        signalStateDao = new SignalStateDao(dataSource);
        AssetLiquidityDao assetLiquidityDao = new AssetLiquidityDao(dataSource);
        SignalQueryService signalQueryService = new SignalQueryService(assetDao, signalStateDao, candleDao, assetLiquidityDao);
        trendAlignmentService = new TrendAlignmentService(signalQueryService, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
    }

    @BeforeEach
    void deactivateSeededAssets() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE asset SET active = false");
        }
    }

    @Test
    void computesBullishConfluenceFromRealSignalStateRows() {
        long assetId = seedAsset("TA1USDT");
        signalStateDao.upsert(new SignalState(assetId, Timeframe.W1, W1_CLOSE, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, D1_CLOSE, TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));

        TrendAlignment alignment = trendAlignmentService.computeAlignment(7);

        assertThat(alignment.bullishConfluence()).extracting(SignalSummary::symbol).containsExactly("TA1USDT");
        assertThat(alignment.bullishRetest()).isEmpty();
        assertThat(alignment.bearishConfluence()).isEmpty();
    }

    @Test
    void computesBearishRetestFromRealSignalStateRows() {
        long assetId = seedAsset("TA2USDT");
        signalStateDao.upsert(new SignalState(assetId, Timeframe.W1, W1_CLOSE, TrendState.BEARISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, NOW.minusDays(2), TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));

        TrendAlignment alignment = trendAlignmentService.computeAlignment(7);

        assertThat(alignment.bearishRetest()).extracting(SignalSummary::symbol).containsExactly("TA2USDT");
        assertThat(alignment.bearishConfluence()).isEmpty();
    }

    @Test
    void deactivatedAssetsAreExcluded() {
        long assetId = seedAsset("TA3USDT");
        signalStateDao.upsert(new SignalState(assetId, Timeframe.W1, W1_CLOSE, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, D1_CLOSE, TrendState.BULLISH, SignalEvent.NONE));
        assetDao.deactivate(assetId);

        TrendAlignment alignment = trendAlignmentService.computeAlignment(7);

        assertThat(alignment.bullishConfluence()).isEmpty();
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
}
