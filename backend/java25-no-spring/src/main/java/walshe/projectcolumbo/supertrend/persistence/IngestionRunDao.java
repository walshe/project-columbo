package walshe.projectcolumbo.supertrend.persistence;

import walshe.projectcolumbo.supertrend.pipeline.IngestionRun;
import walshe.projectcolumbo.supertrend.pipeline.IngestionRunOutcome;
import walshe.projectcolumbo.supertrend.pipeline.IngestionRunStatus;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Optional;

public final class IngestionRunDao {

    private final DataSource dataSource;

    public IngestionRunDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Returns true if a run for this provider+timeframe is currently RUNNING (concurrency guard). */
    public boolean isRunning(Provider provider, Timeframe timeframe) {
        String sql = "SELECT 1 FROM ingestion_run WHERE provider = ?::provider AND timeframe = ?::timeframe AND status = 'RUNNING'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, provider.name());
            statement.setString(2, timeframe.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to check running ingestion for " + provider + " " + timeframe, e);
        }
    }

    /** Inserts a new RUNNING run record and returns its id. */
    public long start(Provider provider, Timeframe timeframe, int assetCount, OffsetDateTime startedAt) {
        String sql = """
                INSERT INTO ingestion_run (provider, timeframe, started_at, status, asset_count)
                VALUES (?::provider, ?::timeframe, ?, 'RUNNING', ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, provider.name());
            statement.setString(2, timeframe.name());
            statement.setObject(3, startedAt);
            statement.setInt(4, assetCount);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to start ingestion run for " + provider + " " + timeframe, e);
        }
    }

    /** Finalizes a run with its terminal status and counts. */
    public void complete(long runId, IngestionRunOutcome outcome) {
        String sql = """
                UPDATE ingestion_run
                SET status = ?::ingestion_run_status,
                    finished_at = ?,
                    duration_ms = ?,
                    inserted_count = ?,
                    updated_count = ?,
                    skipped_count = ?,
                    error_count = ?,
                    error_sample = ?
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, outcome.status().name());
            statement.setObject(2, outcome.finishedAt());
            statement.setLong(3, outcome.durationMs());
            statement.setInt(4, outcome.insertedCount());
            statement.setInt(5, outcome.updatedCount());
            statement.setInt(6, outcome.skippedCount());
            statement.setInt(7, outcome.errorCount());
            statement.setString(8, outcome.errorSample());
            statement.setLong(9, runId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to complete ingestion run " + runId, e);
        }
    }

    public Optional<IngestionRun> findById(long runId) {
        String sql = """
                SELECT id, provider, timeframe, started_at, finished_at, duration_ms, status, asset_count,
                       inserted_count, updated_count, skipped_count, error_count, error_sample
                FROM ingestion_run
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, runId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load ingestion run " + runId, e);
        }
    }

    private static IngestionRun mapRow(ResultSet resultSet) throws SQLException {
        Long durationMs = (Long) resultSet.getObject("duration_ms");
        return new IngestionRun(
                resultSet.getLong("id"),
                Provider.valueOf(resultSet.getString("provider")),
                Timeframe.valueOf(resultSet.getString("timeframe")),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("finished_at", OffsetDateTime.class),
                durationMs,
                IngestionRunStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("asset_count"),
                resultSet.getInt("inserted_count"),
                resultSet.getInt("updated_count"),
                resultSet.getInt("skipped_count"),
                resultSet.getInt("error_count"),
                resultSet.getString("error_sample")
        );
    }
}
