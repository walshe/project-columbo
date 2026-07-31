package walshe.projectcolumbo.supertrend.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * Builds the single, application-wide {@link DataSource}, configured entirely from
 * environment variables (falling back to local-dev-friendly defaults).
 */
public final class DataSourceFactory {

    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/supertrend_core";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASSWORD = "postgres";

    private DataSourceFactory() {
    }

    public static DataSource create() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(env("SUPERTREND_DB_URL", DEFAULT_URL));
        config.setUsername(env("SUPERTREND_DB_USER", DEFAULT_USER));
        config.setPassword(env("SUPERTREND_DB_PASSWORD", DEFAULT_PASSWORD));
        config.setPoolName("supertrend-core-pool");
        return new HikariDataSource(config);
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
