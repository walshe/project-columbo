package walshe.projectcolumbo.supertrend.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendDirection;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendResult;
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

public final class SuperTrendIndicatorDao {

    private static final Logger LOG = LoggerFactory.getLogger(SuperTrendIndicatorDao.class);

    private final DataSource dataSource;

    public SuperTrendIndicatorDao(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    public Optional<OffsetDateTime> findLatestCloseTime(long assetId, Timeframe timeframe) {
        try (Connection connection = dataSource.getConnection()) {
            return findLatestCloseTime(connection, assetId, timeframe);
        } catch (SQLException e) {
            throw new PersistenceException("Failed to acquire connection to load latest indicator close time for asset " + assetId + " " + timeframe, e);
        }
    }

    /** Full stored SuperTrend series for one asset/timeframe, ordered oldest-to-newest. */
    public List<SuperTrendResult> findByAssetAndTimeframe(long assetId, Timeframe timeframe) {
        String sql = """
                SELECT close_time, atr, upper_band, lower_band, supertrend, direction
                FROM indicator_supertrend
                WHERE asset_id = ? AND timeframe = ?::timeframe
                ORDER BY close_time ASC
                """;
        List<SuperTrendResult> results = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assetId);
            statement.setString(2, timeframe.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load stored SuperTrend series for asset " + assetId + " " + timeframe, e);
        }
        return results;
    }

    /** Reuses a caller-managed connection instead of acquiring its own - see {@link CandleDao#findByAssetAndTimeframe(Connection, long, Timeframe)} for why. */
    public Optional<OffsetDateTime> findLatestCloseTime(Connection connection, long assetId, Timeframe timeframe) {
        String sql = "SELECT MAX(close_time) AS latest FROM indicator_supertrend WHERE asset_id = ? AND timeframe = ?::timeframe";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assetId);
            statement.setString(2, timeframe.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.ofNullable(resultSet.getObject("latest", OffsetDateTime.class));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load latest indicator close time for asset " + assetId + " " + timeframe, e);
        }
    }

    /**
     * Idempotent, atomic upsert keyed on (asset, timeframe, close_time) - same shape and
     * concurrency rationale as {@link CandleDao#upsert}: a single {@code INSERT ... ON CONFLICT
     * ... DO UPDATE ... WHERE <differs>} removes the check-then-act race a two-statement
     * (SELECT then INSERT/UPDATE) shape has under concurrent writes to the same key.
     */
    public UpsertOutcome upsert(long assetId, Timeframe timeframe, SuperTrendResult result) {
        try (Connection connection = dataSource.getConnection()) {
            return upsert(connection, assetId, timeframe, result);
        } catch (SQLException e) {
            throw new PersistenceException("Failed to acquire connection to upsert SuperTrend result for asset " + assetId, e);
        }
    }

    /** Reuses a caller-managed connection instead of acquiring its own - see {@link CandleDao#findByAssetAndTimeframe(Connection, long, Timeframe)} for why. */
    public UpsertOutcome upsert(Connection connection, long assetId, Timeframe timeframe, SuperTrendResult result) {
        String sql = """
                INSERT INTO indicator_supertrend (asset_id, timeframe, close_time, atr, upper_band, lower_band, supertrend, direction)
                VALUES (?, ?::timeframe, ?, ?, ?, ?, ?, ?::supertrend_direction)
                ON CONFLICT (asset_id, timeframe, close_time) DO UPDATE
                SET atr = EXCLUDED.atr, upper_band = EXCLUDED.upper_band, lower_band = EXCLUDED.lower_band,
                    supertrend = EXCLUDED.supertrend, direction = EXCLUDED.direction
                WHERE indicator_supertrend.atr IS DISTINCT FROM EXCLUDED.atr
                   OR indicator_supertrend.upper_band IS DISTINCT FROM EXCLUDED.upper_band
                   OR indicator_supertrend.lower_band IS DISTINCT FROM EXCLUDED.lower_band
                   OR indicator_supertrend.supertrend IS DISTINCT FROM EXCLUDED.supertrend
                   OR indicator_supertrend.direction IS DISTINCT FROM EXCLUDED.direction
                RETURNING (xmax = 0) AS inserted
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assetId);
            statement.setString(2, timeframe.name());
            statement.setObject(3, result.closeTime());
            statement.setBigDecimal(4, result.atr());
            statement.setBigDecimal(5, result.upperBand());
            statement.setBigDecimal(6, result.lowerBand());
            statement.setBigDecimal(7, result.superTrend());
            statement.setString(8, result.direction().name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return UpsertOutcome.UNCHANGED;
                }
                if (resultSet.getBoolean("inserted")) {
                    return UpsertOutcome.INSERTED;
                }
                LOG.warn("SuperTrend revision for asset {} {} at {}: new={}", assetId, timeframe, result.closeTime(), result);
                return UpsertOutcome.UPDATED;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to upsert SuperTrend result for asset " + assetId, e);
        }
    }

    private static SuperTrendResult mapRow(ResultSet resultSet) throws SQLException {
        return new SuperTrendResult(
                resultSet.getObject("close_time", OffsetDateTime.class),
                resultSet.getBigDecimal("atr"),
                resultSet.getBigDecimal("upper_band"),
                resultSet.getBigDecimal("lower_band"),
                resultSet.getBigDecimal("supertrend"),
                SuperTrendDirection.valueOf(resultSet.getString("direction"))
        );
    }
}
