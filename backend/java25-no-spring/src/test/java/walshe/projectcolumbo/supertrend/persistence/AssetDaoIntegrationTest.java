package walshe.projectcolumbo.supertrend.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Provider;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class AssetDaoIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static DataSource dataSource;
    static AssetDao assetDao;

    @BeforeAll
    static void migrate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        SchemaMigrator.migrate(dataSource);
        assetDao = new AssetDao(dataSource);
    }

    @BeforeEach
    void deactivateSeededAssets() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE asset SET active = false");
        }
    }

    @Test
    void findAllActiveWithNoFilterReturnsEveryClass() {
        seedAsset("AD1USDT", AssetClass.CRYPTO);
        seedAsset("AD1STOCK", AssetClass.STOCK);

        assertThat(assetDao.findAllActive(null)).extracting(Asset::symbol).contains("AD1USDT", "AD1STOCK");
    }

    @Test
    void findAllActiveFiltersToTheGivenClassOnly() {
        seedAsset("AD2USDT", AssetClass.CRYPTO);
        seedAsset("AD2STOCK", AssetClass.STOCK);

        assertThat(assetDao.findAllActive(AssetClass.STOCK))
                .extracting(Asset::symbol)
                .containsExactly("AD2STOCK");
    }

    @Test
    void findAllActiveFilteredToAClassWithNoAssetsReturnsEmpty() {
        seedAsset("AD3USDT", AssetClass.CRYPTO);

        assertThat(assetDao.findAllActive(AssetClass.COMMODITY)).isEmpty();
    }

    @Test
    void seededAssetsDefaultToCrypto() {
        seedAssetWithDefaultClass("AD4USDT");

        Asset asset = assetDao.findAllActive(null).stream()
                .filter(a -> a.symbol().equals("AD4USDT"))
                .findFirst()
                .orElseThrow();

        assertThat(asset.assetClass()).isEqualTo(AssetClass.CRYPTO);
    }

    private static void seedAsset(String symbol, AssetClass assetClass) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO asset (symbol, provider, active, asset_class) VALUES (?, ?::provider, true, ?::asset_class)")) {
            statement.setString(1, symbol);
            statement.setString(2, Provider.BINANCE.name());
            statement.setString(3, assetClass.name());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void seedAssetWithDefaultClass(String symbol) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO asset (symbol, provider, active) VALUES (?, ?::provider, true)")) {
            statement.setString(1, symbol);
            statement.setString(2, Provider.BINANCE.name());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
