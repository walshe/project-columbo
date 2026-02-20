package walshe.projectcolumbo.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "indicator_supertrend")
class SuperTrendIndicator {

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

    @Column(nullable = false)
    private BigDecimal atr;

    @Column(name = "upper_band", nullable = false)
    private BigDecimal upperBand;

    @Column(name = "lower_band", nullable = false)
    private BigDecimal lowerBand;

    @Column(nullable = false)
    private BigDecimal supertrend;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private SuperTrendDirection direction;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    SuperTrendIndicator() {
    }

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    Asset getAsset() {
        return asset;
    }

    void setAsset(Asset asset) {
        this.asset = asset;
    }

    Timeframe getTimeframe() {
        return timeframe;
    }

    void setTimeframe(Timeframe timeframe) {
        this.timeframe = timeframe;
    }

    OffsetDateTime getCloseTime() {
        return closeTime;
    }

    void setCloseTime(OffsetDateTime closeTime) {
        this.closeTime = closeTime;
    }

    BigDecimal getAtr() {
        return atr;
    }

    void setAtr(BigDecimal atr) {
        this.atr = atr;
    }

    BigDecimal getUpperBand() {
        return upperBand;
    }

    void setUpperBand(BigDecimal upperBand) {
        this.upperBand = upperBand;
    }

    BigDecimal getLowerBand() {
        return lowerBand;
    }

    void setLowerBand(BigDecimal lowerBand) {
        this.lowerBand = lowerBand;
    }

    BigDecimal getSupertrend() {
        return supertrend;
    }

    void setSupertrend(BigDecimal supertrend) {
        this.supertrend = supertrend;
    }

    SuperTrendDirection getDirection() {
        return direction;
    }

    void setDirection(SuperTrendDirection direction) {
        this.direction = direction;
    }

    OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
