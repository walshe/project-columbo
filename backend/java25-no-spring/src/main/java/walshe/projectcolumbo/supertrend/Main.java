package walshe.projectcolumbo.supertrend;

import walshe.projectcolumbo.supertrend.persistence.DataSourceFactory;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.logging.LogManager;

public final class Main {

    private static final Logger LOG = System.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException {
        configureLogging();
        LOG.log(Level.INFO, "SuperTrend Core starting (Java {0})", Runtime.version());

        DataSource dataSource = DataSourceFactory.create();
        SchemaMigrator.migrate(dataSource);

        LOG.log(Level.INFO, "SuperTrend Core ready.");
    }

    private static void configureLogging() throws IOException {
        try (InputStream config = Main.class.getResourceAsStream("/logging.properties")) {
            if (config != null) {
                LogManager.getLogManager().readConfiguration(config);
            }
        }
    }
}
