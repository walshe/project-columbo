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
import java.util.Optional;

public final class SuperTrendIndicatorDao {

    private static final Logger LOG = LoggerFactory.getLogger(SuperTrendIndicatorDao.class);

    private final DataSource dataSource;

    public SuperTrendIndicatorDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<OffsetDateTime> findLatestCloseTime(long assetId, Timeframe timeframe) {
        String sql = "SELECT MAX(close_time) AS latest FROM indicator_supertrend WHERE asset_id = ? AND timeframe = ?::timeframe";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
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
     * Idempotent upsert keyed on (asset, timeframe, close_time), same semantics as
     * {@link CandleDao#upsert}: no-op if unchanged, update + warn if the recomputed value differs.
     */
    public UpsertOutcome upsert(long assetId, Timeframe timeframe, SuperTrendResult result) {
        try (Connection connection = dataSource.getConnection()) {
            Optional<SuperTrendResult> existing = findExisting(connection, assetId, timeframe, result.closeTime());
            if (existing.isEmpty()) {
                insert(connection, assetId, timeframe, result);
                return UpsertOutcome.INSERTED;
            }
            if (isSameValues(existing.get(), result)) {
                return UpsertOutcome.UNCHANGED;
            }
            update(connection, assetId, timeframe, result);
            LOG.warn("SuperTrend revision for asset {} {} at {}: stored={} new={}",
                    assetId, timeframe, result.closeTime(), existing.get(), result);
            return UpsertOutcome.UPDATED;
        } catch (SQLException e) {
            throw new PersistenceException("Failed to upsert SuperTrend result for asset " + assetId, e);
        }
    }

    private Optional<SuperTrendResult> findExisting(Connection connection, long assetId, Timeframe timeframe, OffsetDateTime closeTime) throws SQLException {
        String sql = """
                SELECT close_time, atr, upper_band, lower_band, supertrend, direction
                FROM indicator_supertrend
                WHERE asset_id = ? AND timeframe = ?::timeframe AND close_time = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assetId);
            statement.setString(2, timeframe.name());
            statement.setObject(3, closeTime);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
                return Optional.empty();
            }
        }
    }

    private void insert(Connection connection, long assetId, Timeframe timeframe, SuperTrendResult result) throws SQLException {
        String sql = """
                INSERT INTO indicator_supertrend (asset_id, timeframe, close_time, atr, upper_band, lower_band, supertrend, direction)
                VALUES (?, ?::timeframe, ?, ?, ?, ?, ?, ?::supertrend_direction)
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
            statement.executeUpdate();
        }
    }

    private void update(Connection connection, long assetId, Timeframe timeframe, SuperTrendResult result) throws SQLException {
        String sql = """
                UPDATE indicator_supertrend
                SET atr = ?, upper_band = ?, lower_band = ?, supertrend = ?, direction = ?::supertrend_direction
                WHERE asset_id = ? AND timeframe = ?::timeframe AND close_time = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, result.atr());
            statement.setBigDecimal(2, result.upperBand());
            statement.setBigDecimal(3, result.lowerBand());
            statement.setBigDecimal(4, result.superTrend());
            statement.setString(5, result.direction().name());
            statement.setLong(6, assetId);
            statement.setString(7, timeframe.name());
            statement.setObject(8, result.closeTime());
            statement.executeUpdate();
        }
    }

    private static boolean isSameValues(SuperTrendResult a, SuperTrendResult b) {
        return a.atr().compareTo(b.atr()) == 0
                && a.upperBand().compareTo(b.upperBand()) == 0
                && a.lowerBand().compareTo(b.lowerBand()) == 0
                && a.superTrend().compareTo(b.superTrend()) == 0
                && a.direction() == b.direction();
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
