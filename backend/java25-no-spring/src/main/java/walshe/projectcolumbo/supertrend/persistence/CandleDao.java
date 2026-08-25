package walshe.projectcolumbo.supertrend.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
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
import java.util.Objects;
import java.util.Optional;

public final class CandleDao {

    private static final Logger LOG = LoggerFactory.getLogger(CandleDao.class);

    private final DataSource dataSource;

    public CandleDao(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
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
        return findEarliestCloseTimeAcrossAllAssets(timeframe, null);
    }

    /** @param assetClassFilter restricts the scan to this class only; every class is included when null. */
    public Optional<OffsetDateTime> findEarliestCloseTimeAcrossAllAssets(Timeframe timeframe, AssetClass assetClassFilter) {
        String sql = assetClassFilter == null
                ? "SELECT MIN(close_time) AS earliest FROM candle WHERE timeframe = ?::timeframe"
                : "SELECT MIN(c.close_time) AS earliest FROM candle c JOIN asset a ON a.id = c.asset_id WHERE c.timeframe = ?::timeframe AND a.asset_class = ?::asset_class";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, timeframe.name());
            if (assetClassFilter != null) {
                statement.setString(2, assetClassFilter.name());
            }
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
        return countDistinctAssetsForTimeframe(timeframe, null);
    }

    /** @param assetClassFilter restricts the count to this class only; every class is included when null. */
    public long countDistinctAssetsForTimeframe(Timeframe timeframe, AssetClass assetClassFilter) {
        String sql = assetClassFilter == null
                ? "SELECT COUNT(DISTINCT asset_id) AS asset_count FROM candle WHERE timeframe = ?::timeframe"
                : "SELECT COUNT(DISTINCT c.asset_id) AS asset_count FROM candle c JOIN asset a ON a.id = c.asset_id WHERE c.timeframe = ?::timeframe AND a.asset_class = ?::asset_class";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, timeframe.name());
            if (assetClassFilter != null) {
                statement.setString(2, assetClassFilter.name());
            }
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
     * Idempotent, atomic upsert keyed on (asset, timeframe, close_time): a single {@code INSERT
     * ... ON CONFLICT ... DO UPDATE ... WHERE <differs>} inserts if absent, is a no-op if an
     * identical row already exists, or updates and logs a warning if the stored row's OHLCV
     * differs from the newly ingested candle. Atomic so two concurrent upserts for the same new
     * key can never both attempt an INSERT and have one fail with a raw constraint violation -
     * unlike a check-then-act (SELECT then INSERT/UPDATE) shape, which is exactly what happened
     * in production when two pipeline runs ended up processing the same asset concurrently.
     */
    public UpsertOutcome upsert(long assetId, Candle candle) {
        String sql = """
                INSERT INTO candle (asset_id, timeframe, open_time, close_time, open, high, low, close, volume)
                VALUES (?, ?::timeframe, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (asset_id, timeframe, close_time) DO UPDATE
                SET open_time = EXCLUDED.open_time, open = EXCLUDED.open, high = EXCLUDED.high,
                    low = EXCLUDED.low, close = EXCLUDED.close, volume = EXCLUDED.volume
                WHERE candle.open IS DISTINCT FROM EXCLUDED.open
                   OR candle.high IS DISTINCT FROM EXCLUDED.high
                   OR candle.low IS DISTINCT FROM EXCLUDED.low
                   OR candle.close IS DISTINCT FROM EXCLUDED.close
                   OR candle.volume IS DISTINCT FROM EXCLUDED.volume
                RETURNING (xmax = 0) AS inserted
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assetId);
            statement.setString(2, candle.timeframe().name());
            statement.setObject(3, candle.openTime());
            statement.setObject(4, candle.closeTime());
            statement.setBigDecimal(5, candle.open());
            statement.setBigDecimal(6, candle.high());
            statement.setBigDecimal(7, candle.low());
            statement.setBigDecimal(8, candle.close());
            statement.setBigDecimal(9, candle.volume());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    // WHERE on the DO UPDATE branch didn't match - a conflict existed but every
                    // value was already identical, so xmax=0 vs. not is moot: nothing to report.
                    return UpsertOutcome.UNCHANGED;
                }
                if (resultSet.getBoolean("inserted")) {
                    return UpsertOutcome.INSERTED;
                }
                LOG.warn("Candle revision for asset {} {} at {}: new={}", assetId, candle.timeframe(), candle.closeTime(), candle);
                return UpsertOutcome.UPDATED;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to upsert candle for asset " + assetId, e);
        }
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
