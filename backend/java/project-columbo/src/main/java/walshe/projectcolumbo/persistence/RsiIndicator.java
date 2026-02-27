package walshe.projectcolumbo.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "indicator_rsi")
public class RsiIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Timeframe timeframe;

    @Column(name = "close_time", nullable = false)
    private OffsetDateTime closeTime;

    @Column(name = "rsi_value", nullable = false)
    private BigDecimal rsiValue;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    RsiIndicator() {}

    public Long getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public Timeframe getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(Timeframe timeframe) {
        this.timeframe = timeframe;
    }

    public OffsetDateTime getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(OffsetDateTime closeTime) {
        this.closeTime = closeTime;
    }

    public BigDecimal getRsiValue() {
        return rsiValue;
    }

    public void setRsiValue(BigDecimal rsiValue) {
        this.rsiValue = rsiValue;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
