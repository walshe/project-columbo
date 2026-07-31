package walshe.projectcolumbo.supertrend.persistence;

import walshe.projectcolumbo.supertrend.pulse.MarketBreadthSnapshot;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MarketBreadthSnapshotDao {

    private final DataSource dataSource;

    public MarketBreadthSnapshotDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsert(MarketBreadthSnapshot snapshot) {
        String sql = """
                INSERT INTO market_breadth_snapshot
                    (timeframe, snapshot_close_time, bullish_count, bearish_count, missing_count, total_assets, bullish_ratio)
                VALUES (?::timeframe, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (timeframe, snapshot_close_time)
                DO UPDATE SET bullish_count = EXCLUDED.bullish_count,
                              bearish_count = EXCLUDED.bearish_count,
                              missing_count = EXCLUDED.missing_count,
                              total_assets = EXCLUDED.total_assets,
                              bullish_ratio = EXCLUDED.bullish_ratio
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, snapshot.timeframe().name());
            statement.setObject(2, snapshot.snapshotCloseTime());
            statement.setInt(3, snapshot.bullishCount());
            statement.setInt(4, snapshot.bearishCount());
            statement.setInt(5, snapshot.missingCount());
            statement.setInt(6, snapshot.totalAssets());
            statement.setBigDecimal(7, snapshot.bullishRatio());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to upsert market breadth snapshot for " + snapshot.timeframe(), e);
        }
    }

    public Optional<MarketBreadthSnapshot> findLatest(Timeframe timeframe) {
        String sql = """
                SELECT timeframe, snapshot_close_time, bullish_count, bearish_count, missing_count, total_assets, bullish_ratio
                FROM market_breadth_snapshot
                WHERE timeframe = ?::timeframe
                ORDER BY snapshot_close_time DESC
                LIMIT 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, timeframe.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load latest market breadth snapshot for " + timeframe, e);
        }
    }

    public List<MarketBreadthSnapshot> findRange(Timeframe timeframe, OffsetDateTime from, OffsetDateTime to) {
        String sql = """
                SELECT timeframe, snapshot_close_time, bullish_count, bearish_count, missing_count, total_assets, bullish_ratio
                FROM market_breadth_snapshot
                WHERE timeframe = ?::timeframe
                  AND (? IS NULL OR snapshot_close_time >= ?)
                  AND (? IS NULL OR snapshot_close_time <= ?)
                ORDER BY snapshot_close_time ASC
                """;
        List<MarketBreadthSnapshot> snapshots = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, timeframe.name());
            statement.setObject(2, from);
            statement.setObject(3, from);
            statement.setObject(4, to);
            statement.setObject(5, to);
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
        return new MarketBreadthSnapshot(
                Timeframe.valueOf(resultSet.getString("timeframe")),
                resultSet.getObject("snapshot_close_time", OffsetDateTime.class),
                resultSet.getInt("bullish_count"),
                resultSet.getInt("bearish_count"),
                resultSet.getInt("missing_count"),
                resultSet.getInt("total_assets"),
                resultSet.getBigDecimal("bullish_ratio")
        );
    }
}
