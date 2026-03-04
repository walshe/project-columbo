package walshe.projectcolumbo.persistence.entity;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "candle")
public class Candle {
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

    @Column(name = "open_time", nullable = false)
    private OffsetDateTime openTime;

    @Column(name = "close_time", nullable = false)
    private OffsetDateTime closeTime;

    @Column(nullable = false)
    private BigDecimal open;

    @Column(nullable = false)
    private BigDecimal high;

    @Column(nullable = false)
    private BigDecimal low;

    @Column(nullable = false)
    private BigDecimal close;

    @Column(nullable = false)
    private BigDecimal volume;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private MarketProvider source;

    @Column(name = "raw_payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawPayload;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public Candle() {
    }

    public Long getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public Timeframe getTimeframe() {
        return timeframe;
    }

    public OffsetDateTime getOpenTime() {
        return openTime;
    }

    public OffsetDateTime getCloseTime() {
        return closeTime;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public MarketProvider getSource() {
        return source;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public void setTimeframe(Timeframe timeframe) {
        this.timeframe = timeframe;
    }

    public void setOpenTime(OffsetDateTime openTime) {
        this.openTime = openTime;
    }

    public void setCloseTime(OffsetDateTime closeTime) {
        this.closeTime = closeTime;
    }

    public void setOpen(BigDecimal open) {
        this.open = open;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public void setSource(MarketProvider source) {
        this.source = source;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
