package walshe.projectcolumbo.persistence.entity;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.TrendState;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.IndicatorType;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "signal_state")
public class SignalState {

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

    @Column(name = "indicator_type", nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private IndicatorType indicatorType;

    @Column(name = "close_time", nullable = false)
    private OffsetDateTime closeTime;

    @Column(name = "trend_state", nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TrendState trendState;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private SignalEvent event;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public SignalState() {
    }

    public SignalState(Asset asset, Timeframe timeframe, IndicatorType indicatorType, OffsetDateTime closeTime, TrendState trendState, SignalEvent event) {
        this.asset = asset;
        this.timeframe = timeframe;
        this.indicatorType = indicatorType;
        this.closeTime = closeTime;
        this.trendState = trendState;
        this.event = event;
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

    public IndicatorType getIndicatorType() {
        return indicatorType;
    }

    public void setIndicatorType(IndicatorType indicatorType) {
        this.indicatorType = indicatorType;
    }

    public OffsetDateTime getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(OffsetDateTime closeTime) {
        this.closeTime = closeTime;
    }

    public TrendState getTrendState() {
        return trendState;
    }

    public void setTrendState(TrendState trendState) {
        this.trendState = trendState;
    }

    public SignalEvent getEvent() {
        return event;
    }

    public void setEvent(SignalEvent event) {
        this.event = event;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
