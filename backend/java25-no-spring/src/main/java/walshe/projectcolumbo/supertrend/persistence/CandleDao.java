package walshe.projectcolumbo.supertrend.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CandleDao {

    private static final Logger LOG = LoggerFactory.getLogger(CandleDao.class);

    private final DataSource dataSource;

    public CandleDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Candle> findByAssetAndTimeframe(long assetId, Timeframe timeframe) {
        String sql = """
                SELECT open_time, close_time, open, high, low, close, volume
                FROM candle
                WHERE asset_id = ? AND timeframe = ?::timeframe
                ORDER BY close_time ASC
                """;
        List<Candle> candles = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assetId);
            statement.setString(2, timeframe.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    candles.add(mapRow(resultSet, timeframe));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load candles for asset " + assetId + " " + timeframe, e);
        }
        return candles;
    }

    public Optional<OffsetDateTime> findLatestCloseTime(long assetId, Timeframe timeframe) {
        String sql = "SELECT MAX(close_time) AS latest FROM candle WHERE asset_id = ? AND timeframe = ?::timeframe";
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
            throw new PersistenceException("Failed to load latest close time for asset " + assetId + " " + timeframe, e);
        }
    }

    /** System-wide latest close time across every asset for a timeframe (not scoped to one asset) - used for freshness checks. */
    public Optional<OffsetDateTime> findLatestCloseTimeAcrossAllAssets(Timeframe timeframe) {
        String sql = "SELECT MAX(close_time) AS latest FROM candle WHERE timeframe = ?::timeframe";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, timeframe.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.ofNullable(resultSet.getObject("latest", OffsetDateTime.class));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load latest close time across all assets for " + timeframe, e);
        }
    }

    /** System-wide earliest close time across every asset for a timeframe - used for candle coverage reporting. */
    public Optional<OffsetDateTime> findEarliestCloseTimeAcrossAllAssets(Timeframe timeframe) {
        String sql = "SELECT MIN(close_time) AS earliest FROM candle WHERE timeframe = ?::timeframe";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, timeframe.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.ofNullable(resultSet.getObject("earliest", OffsetDateTime.class));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load earliest close time across all assets for " + timeframe, e);
        }
    }

    /** Number of distinct assets with at least one stored candle for a timeframe - used for candle coverage reporting. */
    public long countDistinctAssetsForTimeframe(Timeframe timeframe) {
        String sql = "SELECT COUNT(DISTINCT asset_id) AS asset_count FROM candle WHERE timeframe = ?::timeframe";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, timeframe.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong("asset_count");
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to count distinct assets for " + timeframe, e);
        }
    }

    /** Latest close price per asset for a timeframe - used for percentage-change-since-flip. */
    public Map<Long, BigDecimal> findLatestCloseByAssetForTimeframe(Timeframe timeframe) {
        String sql = """
                SELECT DISTINCT ON (asset_id) asset_id, close
                FROM candle
                WHERE timeframe = ?::timeframe
                ORDER BY asset_id, close_time DESC
                """;
        Map<Long, BigDecimal> closeByAssetId = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, timeframe.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    closeByAssetId.put(resultSet.getLong("asset_id"), resultSet.getBigDecimal("close"));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load latest close prices for " + timeframe, e);
        }
        return closeByAssetId;
    }

    /**
     * Close price for each asset at its own given close time (e.g. the candle a flip happened
     * on) - one query for the whole batch rather than one per asset. Each asset is matched only
     * against its own close time, never another asset's.
     */
    public Map<Long, BigDecimal> findCloseAtTimes(Timeframe timeframe, Map<Long, OffsetDateTime> closeTimeByAssetId) {
        if (closeTimeByAssetId.isEmpty()) {
            return Map.of();
        }
        List<Map.Entry<Long, OffsetDateTime>> entries = new ArrayList<>(closeTimeByAssetId.entrySet());
        StringBuilder sql = new StringBuilder("SELECT asset_id, close FROM candle WHERE timeframe = ?::timeframe AND (");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("(asset_id = ? AND close_time = ?)");
        }
        sql.append(")");

        Map<Long, BigDecimal> closeByAssetId = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, timeframe.name());
            int paramIndex = 2;
            for (Map.Entry<Long, OffsetDateTime> entry : entries) {
                statement.setLong(paramIndex++, entry.getKey());
                statement.setObject(paramIndex++, entry.getValue());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    closeByAssetId.put(resultSet.getLong("asset_id"), resultSet.getBigDecimal("close"));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load close prices at given times for " + timeframe, e);
        }
        return closeByAssetId;
    }

    /**
     * Idempotent upsert keyed on (asset, timeframe, close_time): inserts if absent, is a no-op
     * if an identical row already exists, or updates and logs a warning if the stored row's
     * OHLCV differs from the newly ingested candle.
     */
    public UpsertOutcome upsert(long assetId, Candle candle) {
        try (Connection connection = dataSource.getConnection()) {
            Optional<Candle> existing = findExisting(connection, assetId, candle.timeframe(), candle.closeTime());
            if (existing.isEmpty()) {
                insert(connection, assetId, candle);
                return UpsertOutcome.INSERTED;
            }
            if (isSameOhlcv(existing.get(), candle)) {
                return UpsertOutcome.UNCHANGED;
            }
            update(connection, assetId, candle);
            LOG.warn("Candle revision for asset {} {} at {}: stored={} new={}",
                    assetId, candle.timeframe(), candle.closeTime(), existing.get(), candle);
            return UpsertOutcome.UPDATED;
        } catch (SQLException e) {
            throw new PersistenceException("Failed to upsert candle for asset " + assetId, e);
        }
    }

    private Optional<Candle> findExisting(Connection connection, long assetId, Timeframe timeframe, OffsetDateTime closeTime) throws SQLException {
        String sql = """
                SELECT open_time, close_time, open, high, low, close, volume
                FROM candle
                WHERE asset_id = ? AND timeframe = ?::timeframe AND close_time = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assetId);
            statement.setString(2, timeframe.name());
            statement.setObject(3, closeTime);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet, timeframe));
                }
                return Optional.empty();
            }
        }
    }

    private void insert(Connection connection, long assetId, Candle candle) throws SQLException {
        String sql = """
                INSERT INTO candle (asset_id, timeframe, open_time, close_time, open, high, low, close, volume)
                VALUES (?, ?::timeframe, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assetId);
            statement.setString(2, candle.timeframe().name());
            statement.setObject(3, candle.openTime());
            statement.setObject(4, candle.closeTime());
            statement.setBigDecimal(5, candle.open());
            statement.setBigDecimal(6, candle.high());
            statement.setBigDecimal(7, candle.low());
            statement.setBigDecimal(8, candle.close());
            statement.setBigDecimal(9, candle.volume());
            statement.executeUpdate();
        }
    }

    private void update(Connection connection, long assetId, Candle candle) throws SQLException {
        String sql = """
                UPDATE candle
                SET open_time = ?, open = ?, high = ?, low = ?, close = ?, volume = ?
                WHERE asset_id = ? AND timeframe = ?::timeframe AND close_time = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, candle.openTime());
            statement.setBigDecimal(2, candle.open());
            statement.setBigDecimal(3, candle.high());
            statement.setBigDecimal(4, candle.low());
            statement.setBigDecimal(5, candle.close());
            statement.setBigDecimal(6, candle.volume());
            statement.setLong(7, assetId);
            statement.setString(8, candle.timeframe().name());
            statement.setObject(9, candle.closeTime());
            statement.executeUpdate();
        }
    }

    private static boolean isSameOhlcv(Candle a, Candle b) {
        return a.open().compareTo(b.open()) == 0
                && a.high().compareTo(b.high()) == 0
                && a.low().compareTo(b.low()) == 0
                && a.close().compareTo(b.close()) == 0
                && a.volume().compareTo(b.volume()) == 0;
    }

    private static Candle mapRow(ResultSet resultSet, Timeframe timeframe) throws SQLException {
        return new Candle(
                resultSet.getObject("open_time", OffsetDateTime.class),
                resultSet.getObject("close_time", OffsetDateTime.class),
                timeframe,
                resultSet.getBigDecimal("open"),
                resultSet.getBigDecimal("high"),
                resultSet.getBigDecimal("low"),
                resultSet.getBigDecimal("close"),
                resultSet.getBigDecimal("volume")
        );
    }
}
