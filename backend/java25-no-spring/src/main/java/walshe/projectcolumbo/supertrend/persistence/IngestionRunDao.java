package walshe.projectcolumbo.supertrend.persistence;

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
    public void complete(
            long runId,
            IngestionRunStatus status,
            OffsetDateTime finishedAt,
            long durationMs,
            int insertedCount,
            int updatedCount,
            int skippedCount,
            int errorCount,
            String errorSample
    ) {
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
            statement.setString(1, status.name());
            statement.setObject(2, finishedAt);
            statement.setLong(3, durationMs);
            statement.setInt(4, insertedCount);
            statement.setInt(5, updatedCount);
            statement.setInt(6, skippedCount);
            statement.setInt(7, errorCount);
            statement.setString(8, errorSample);
            statement.setLong(9, runId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to complete ingestion run " + runId, e);
        }
    }
}
