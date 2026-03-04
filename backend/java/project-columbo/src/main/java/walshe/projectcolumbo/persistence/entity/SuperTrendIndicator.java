package walshe.projectcolumbo.persistence.entity;
import walshe.projectcolumbo.persistence.model.SuperTrendResult;
import walshe.projectcolumbo.persistence.model.SuperTrendDirection;
import walshe.projectcolumbo.persistence.model.Timeframe;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "indicator_supertrend")
public class SuperTrendIndicator {

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

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public SuperTrendIndicator() {
    }

    public static SuperTrendIndicator fromResult(Asset asset, Timeframe timeframe, SuperTrendResult result) {
        SuperTrendIndicator indicator = new SuperTrendIndicator();
        indicator.setAsset(asset);
        indicator.setTimeframe(timeframe);
        indicator.setCloseTime(result.closeTime());
        indicator.setAtr(result.atr());
        indicator.setUpperBand(result.upperBand());
        indicator.setLowerBand(result.lowerBand());
        indicator.setSupertrend(result.supertrend());
        indicator.setDirection(result.direction());
        return indicator;
    }

    public boolean isSameValues(SuperTrendResult result) {
        return this.atr.compareTo(result.atr()) == 0 &&
                this.upperBand.compareTo(result.upperBand()) == 0 &&
                this.lowerBand.compareTo(result.lowerBand()) == 0 &&
                this.supertrend.compareTo(result.supertrend()) == 0 &&
                this.direction == result.direction();
    }

    public void updateFrom(SuperTrendResult result) {
        this.atr = result.atr();
        this.upperBand = result.upperBand();
        this.lowerBand = result.lowerBand();
        this.supertrend = result.supertrend();
        this.direction = result.direction();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public BigDecimal getAtr() {
        return atr;
    }

    public void setAtr(BigDecimal atr) {
        this.atr = atr;
    }

    public BigDecimal getUpperBand() {
        return upperBand;
    }

    public void setUpperBand(BigDecimal upperBand) {
        this.upperBand = upperBand;
    }

    public BigDecimal getLowerBand() {
        return lowerBand;
    }

    public void setLowerBand(BigDecimal lowerBand) {
        this.lowerBand = lowerBand;
    }

    public BigDecimal getSupertrend() {
        return supertrend;
    }

    public void setSupertrend(BigDecimal supertrend) {
        this.supertrend = supertrend;
    }

    public SuperTrendDirection getDirection() {
        return direction;
    }

    public void setDirection(SuperTrendDirection direction) {
        this.direction = direction;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
