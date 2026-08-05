package walshe.projectcolumbo.supertrend.persistence;

import walshe.projectcolumbo.supertrend.pulse.MarketBreadthSnapshot;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MarketBreadthSnapshotDao {

    private final DataSource dataSource;

    public MarketBreadthSnapshotDao(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    private static final String UPDATE_CLAUSE = """
            DO UPDATE SET bullish_count = EXCLUDED.bullish_count,
                          bearish_count = EXCLUDED.bearish_count,
                          missing_count = EXCLUDED.missing_count,
                          total_assets = EXCLUDED.total_assets,
                          bullish_ratio = EXCLUDED.bullish_ratio
            """;

    // Two partial unique indexes back this table (unique_market_breadth_snapshot_combined for
    // asset_class IS NULL, unique_market_breadth_snapshot_per_class for IS NOT NULL) rather than
    // one expression index, since an enum's ::text cast is STABLE, not IMMUTABLE, and Postgres
    // rejects non-immutable expressions in an index. A single INSERT can't pick its ON CONFLICT
    // target based on a bound parameter's runtime nullability, so upsert needs two SQL statements.
    private static final String UPSERT_COMBINED_SQL = """
            INSERT INTO market_breadth_snapshot
                (timeframe, snapshot_close_time, asset_class, bullish_count, bearish_count, missing_count, total_assets, bullish_ratio)
            VALUES (?::timeframe, ?, NULL, ?, ?, ?, ?, ?)
            ON CONFLICT (timeframe, snapshot_close_time) WHERE asset_class IS NULL
            """ + UPDATE_CLAUSE;

    private static final String UPSERT_PER_CLASS_SQL = """
            INSERT INTO market_breadth_snapshot
                (timeframe, snapshot_close_time, asset_class, bullish_count, bearish_count, missing_count, total_assets, bullish_ratio)
            VALUES (?::timeframe, ?, ?::asset_class, ?, ?, ?, ?, ?)
            ON CONFLICT (timeframe, snapshot_close_time, asset_class) WHERE asset_class IS NOT NULL
            """ + UPDATE_CLAUSE;

    /** @param snapshot {@code snapshot.assetClass()} null means "combined across every class". */
    public void upsert(MarketBreadthSnapshot snapshot) {
        boolean combined = snapshot.assetClass() == null;
        String sql = combined ? UPSERT_COMBINED_SQL : UPSERT_PER_CLASS_SQL;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, snapshot.timeframe().name());
            statement.setObject(index++, snapshot.snapshotCloseTime());
            if (!combined) {
                statement.setString(index++, snapshot.assetClass().name());
            }
            statement.setInt(index++, snapshot.bullishCount());
            statement.setInt(index++, snapshot.bearishCount());
            statement.setInt(index++, snapshot.missingCount());
            statement.setInt(index++, snapshot.totalAssets());
            statement.setBigDecimal(index, snapshot.bullishRatio());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to upsert market breadth snapshot for " + snapshot.timeframe(), e);
        }
    }

    /** Returns the combined (all-classes) snapshot. */
    public Optional<MarketBreadthSnapshot> findLatest(Timeframe timeframe) {
        return findLatest(timeframe, null);
    }

    /** @param assetClassFilter null returns the combined (all-classes) snapshot. */
    public Optional<MarketBreadthSnapshot> findLatest(Timeframe timeframe, AssetClass assetClassFilter) {
        String sql = """
                SELECT timeframe, snapshot_close_time, asset_class, bullish_count, bearish_count, missing_count, total_assets, bullish_ratio
                FROM market_breadth_snapshot
                WHERE timeframe = ?::timeframe
                  AND asset_class IS NOT DISTINCT FROM ?::asset_class
                ORDER BY snapshot_close_time DESC
                LIMIT 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, timeframe.name());
            statement.setString(2, assetClassFilter != null ? assetClassFilter.name() : null);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load latest market breadth snapshot for " + timeframe, e);
        }
    }

    /** Returns only combined (all-classes) snapshots. */
    public List<MarketBreadthSnapshot> findRange(Timeframe timeframe, OffsetDateTime from, OffsetDateTime to) {
        return findRange(timeframe, null, from, to);
    }

    /** @param assetClassFilter null returns only combined (all-classes) snapshots, not every class's rows mixed together. */
    public List<MarketBreadthSnapshot> findRange(Timeframe timeframe, AssetClass assetClassFilter, OffsetDateTime from, OffsetDateTime to) {
        String sql = """
                SELECT timeframe, snapshot_close_time, asset_class, bullish_count, bearish_count, missing_count, total_assets, bullish_ratio
                FROM market_breadth_snapshot
                WHERE timeframe = ?::timeframe
                  AND asset_class IS NOT DISTINCT FROM ?::asset_class
                  AND (? IS NULL OR snapshot_close_time >= ?)
                  AND (? IS NULL OR snapshot_close_time <= ?)
                ORDER BY snapshot_close_time ASC
                """;
        List<MarketBreadthSnapshot> snapshots = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, timeframe.name());
            statement.setString(2, assetClassFilter != null ? assetClassFilter.name() : null);
            statement.setObject(3, from);
            statement.setObject(4, from);
            statement.setObject(5, to);
            statement.setObject(6, to);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    snapshots.add(mapRow(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load market breadth snapshot range for " + timeframe, e);
        }
        return snapshots;
    }

    private static MarketBreadthSnapshot mapRow(ResultSet resultSet) throws SQLException {
        String assetClass = resultSet.getString("asset_class");
        return new MarketBreadthSnapshot(
                Timeframe.valueOf(resultSet.getString("timeframe")),
                resultSet.getObject("snapshot_close_time", OffsetDateTime.class),
                assetClass != null ? AssetClass.valueOf(assetClass) : null,
                resultSet.getInt("bullish_count"),
                resultSet.getInt("bearish_count"),
                resultSet.getInt("missing_count"),
                resultSet.getInt("total_assets"),
                resultSet.getBigDecimal("bullish_ratio")
        );
    }
}
