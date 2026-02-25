package walshe.projectcolumbo.ingestion;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import walshe.projectcolumbo.persistence.MarketProvider;
import walshe.projectcolumbo.persistence.Timeframe;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ingestion_run")
public class IngestionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private MarketProvider provider;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Timeframe timeframe;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private IngestionRunStatus status;

    @Column(name = "asset_count", nullable = false)
    private int assetCount;

    @Column(name = "inserted_count", nullable = false)
    private int insertedCount = 0;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount = 0;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount = 0;

    @Column(name = "error_count", nullable = false)
    private int errorCount = 0;

    @Column(name = "error_sample")
    private String errorSample;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public IngestionRun() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MarketProvider getProvider() {
        return provider;
    }

    public void setProvider(MarketProvider provider) {
        this.provider = provider;
    }

    public Timeframe getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(Timeframe timeframe) {
        this.timeframe = timeframe;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public IngestionRunStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionRunStatus status) {
        this.status = status;
    }

    public int getAssetCount() {
        return assetCount;
    }

    public void setAssetCount(int assetCount) {
        this.assetCount = assetCount;
    }

    public int getInsertedCount() {
        return insertedCount;
    }

    public void setInsertedCount(int insertedCount) {
        this.insertedCount = insertedCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public String getErrorSample() {
        return errorSample;
    }

    public void setErrorSample(String errorSample) {
        this.errorSample = errorSample;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
