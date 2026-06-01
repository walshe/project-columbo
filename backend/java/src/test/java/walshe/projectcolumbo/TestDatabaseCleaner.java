package walshe.projectcolumbo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Truncates all tables in dependency order so every integration-test setUp
 * starts from an empty database.  Uses CASCADE so we never have to maintain
 * a manually ordered delete chain as the schema evolves.
 */
@Component
public class TestDatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;

    public TestDatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void cleanAll() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE " +
            "signal_state, " +
            "indicator_supertrend, " +
            "indicator_rsi, " +
            "indicator_ema, " +
            "indicator_macd, " +
            "indicator_thermometer, " +
            "market_breadth_snapshot, " +
            "ingestion_run, " +
            "candle, " +
            "asset " +
            "RESTART IDENTITY CASCADE"
        );
    }
}
