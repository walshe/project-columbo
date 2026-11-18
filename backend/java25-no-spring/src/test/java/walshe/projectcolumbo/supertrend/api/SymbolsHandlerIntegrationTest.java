package walshe.projectcolumbo.supertrend.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.shared.Provider;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SymbolsHandlerIntegrationTest {

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

    private static Javalin app() {
        Javalin app = ApiServer.create();
        new SymbolsHandler(assetDao).register(app);
        return app;
    }

    @Test
    void returnsActiveSymbolsSortedAscending() throws Exception {
        seedAsset("ZZUSDT");
        seedAsset("AAUSDT");
        seedAsset("MMUSDT");

        JavalinTest.test(app(), (server, client) -> {
            var response = client.get("/api/v1/symbols");
            assertThat(response.code()).isEqualTo(200);
            List<String> symbols = new ObjectMapper().readValue(response.body().string(), List.class);
            assertThat(symbols).containsExactly("AAUSDT", "MMUSDT", "ZZUSDT");
        });
    }

    @Test
    void excludesInactiveAssets() throws Exception {
        seedAsset("ACTIVEUSDT");
        // deactivateSeededAssets already deactivated all from previous tests; seed one inactive explicitly
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO asset (symbol, provider, active) VALUES (?, ?::provider, false)")) {
            statement.setString(1, "INACTIVEUSDT");
            statement.setString(2, Provider.MEXC.name());
            statement.executeUpdate();
        }

        JavalinTest.test(app(), (server, client) -> {
            var response = client.get("/api/v1/symbols");
            String body = response.body().string();
            assertThat(body).contains("ACTIVEUSDT").doesNotContain("INACTIVEUSDT");
        });
    }

    @Test
    void returnsEmptyArrayWhenNoActiveAssets() {
        JavalinTest.test(app(), (server, client) -> {
            var response = client.get("/api/v1/symbols");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).isEqualTo("[]");
        });
    }

    private static void seedAsset(String symbol) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO asset (symbol, provider, active) VALUES (?, ?::provider, true)")) {
            statement.setString(1, symbol);
            statement.setString(2, Provider.MEXC.name());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
