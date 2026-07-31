package walshe.projectcolumbo.supertrend.persistence;

import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalEvent;
import walshe.projectcolumbo.supertrend.signal.SignalState;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SignalStateDao {

    private final DataSource dataSource;

    public SignalStateDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Upserts a signal-state row keyed on (asset, timeframe, close_time). Unlike candle/indicator
     * upserts, there's no warn-on-revision requirement here (per spec) — this is a plain
     * insert-or-replace.
     */
    public void upsert(SignalState state) {
        String sql = """
                INSERT INTO signal_state (asset_id, timeframe, close_time, trend_state, event)
                VALUES (?, ?::timeframe, ?, ?::trend_state, ?::signal_event)
                ON CONFLICT (asset_id, timeframe, close_time)
                DO UPDATE SET trend_state = EXCLUDED.trend_state, event = EXCLUDED.event
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, state.assetId());
            statement.setString(2, state.timeframe().name());
            statement.setObject(3, state.closeTime());
            statement.setString(4, state.trendState().name());
            statement.setString(5, state.event().name());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to upsert signal state for asset " + state.assetId(), e);
        }
    }

    /** Latest signal-state row per asset for a timeframe (one row per distinct asset). */
    public List<SignalState> findLatestForAllAssets(Timeframe timeframe) {
        String sql = """
                SELECT DISTINCT ON (asset_id) asset_id, timeframe, close_time, trend_state, event
                FROM signal_state
                WHERE timeframe = ?::timeframe
                ORDER BY asset_id, close_time DESC
                """;
        List<SignalState> states = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, timeframe.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    states.add(mapRow(resultSet, timeframe));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load latest signal states for " + timeframe, e);
        }
        return states;
    }

    /** Most recent flip (event != NONE) for one asset/timeframe, if any. */
    public Optional<SignalState> findLatestFlipForAsset(long assetId, Timeframe timeframe) {
        String sql = """
                SELECT asset_id, timeframe, close_time, trend_state, event
                FROM signal_state
                WHERE asset_id = ? AND timeframe = ?::timeframe AND event != 'NONE'
                ORDER BY close_time DESC
                LIMIT 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assetId);
            statement.setString(2, timeframe.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet, timeframe));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load latest flip for asset " + assetId, e);
        }
    }

    private static SignalState mapRow(ResultSet resultSet, Timeframe timeframe) throws SQLException {
        return new SignalState(
                resultSet.getLong("asset_id"),
                timeframe,
                resultSet.getObject("close_time", java.time.OffsetDateTime.class),
                TrendState.valueOf(resultSet.getString("trend_state")),
                SignalEvent.valueOf(resultSet.getString("event"))
        );
    }
}
