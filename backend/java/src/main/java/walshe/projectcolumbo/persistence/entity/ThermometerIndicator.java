package walshe.projectcolumbo.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "indicator_thermometer")
public class ThermometerIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "close_time", nullable = false)
    private OffsetDateTime closeTime;

    @Column(name = "temperature", nullable = false)
    private BigDecimal temperature;

    @Column(name = "temperature_ema")
    private BigDecimal temperatureEma;  // nullable — null until 22 temperature values exist

    @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    public ThermometerIndicator() {}

    public Long getId() { return id; }

    public Asset getAsset() { return asset; }
    public void setAsset(Asset asset) { this.asset = asset; }

    public OffsetDateTime getCloseTime() { return closeTime; }
    public void setCloseTime(OffsetDateTime closeTime) { this.closeTime = closeTime; }

    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }

    public BigDecimal getTemperatureEma() { return temperatureEma; }
    public void setTemperatureEma(BigDecimal temperatureEma) { this.temperatureEma = temperatureEma; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
