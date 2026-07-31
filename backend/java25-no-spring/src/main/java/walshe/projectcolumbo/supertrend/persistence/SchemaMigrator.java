package walshe.projectcolumbo.supertrend.persistence;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/** Runs Flyway migrations (classpath:db/migration) against the application's schema at startup. */
public final class SchemaMigrator {

    private SchemaMigrator() {
    }

    public static void migrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
