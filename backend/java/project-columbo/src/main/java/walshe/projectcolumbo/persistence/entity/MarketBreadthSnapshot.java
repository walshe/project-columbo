package walshe.projectcolumbo.persistence.entity;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.IndicatorType;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "market_breadth_snapshot", uniqueConstraints = {
    @UniqueConstraint(name = "unique_market_breadth_snapshot", columnNames = {"timeframe", "indicator_type", "snapshot_close_time"})
})
public class MarketBreadthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Timeframe timeframe;

    @Column(name = "indicator_type", nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private IndicatorType indicatorType;

    @Column(name = "snapshot_close_time", nullable = false)
    private OffsetDateTime snapshotCloseTime;

    @Column(name = "bullish_count", nullable = false)
    private int bullishCount;

    @Column(name = "bearish_count", nullable = false)
    private int bearishCount;

    @Column(name = "missing_count", nullable = false)
    private int missingCount;

    @Column(name = "total_assets", nullable = false)
    private int totalAssets;

    @Column(name = "bullish_ratio", nullable = false, precision = 5, scale = 4)
    private BigDecimal bullishRatio;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected MarketBreadthSnapshot() {
    }

    public MarketBreadthSnapshot(Timeframe timeframe, IndicatorType indicatorType, OffsetDateTime snapshotCloseTime, 
                                 int bullishCount, int bearishCount, int missingCount, int totalAssets, BigDecimal bullishRatio) {
        this.timeframe = timeframe;
        this.indicatorType = indicatorType;
        this.snapshotCloseTime = snapshotCloseTime;
        this.bullishCount = bullishCount;
        this.bearishCount = bearishCount;
        this.missingCount = missingCount;
        this.totalAssets = totalAssets;
        this.bullishRatio = bullishRatio;
    }

    public Long getId() {
        return id;
    }

    public Timeframe getTimeframe() {
        return timeframe;
    }

    public IndicatorType getIndicatorType() {
        return indicatorType;
    }

    public OffsetDateTime getSnapshotCloseTime() {
        return snapshotCloseTime;
    }

    public int getBullishCount() {
        return bullishCount;
    }

    public int getBearishCount() {
        return bearishCount;
    }

    public int getMissingCount() {
        return missingCount;
    }

    public int getTotalAssets() {
        return totalAssets;
    }

    public BigDecimal getBullishRatio() {
        return bullishRatio;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
