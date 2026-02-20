package walshe.projectcolumbo.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "asset")
public class Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private MarketProvider provider;

    private boolean active = true;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public Asset() {
    }

    public Asset(String symbol, String name, MarketProvider provider, boolean active) {
        this.symbol = symbol;
        this.name = name;
        this.provider = provider;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public MarketProvider getProvider() {
        return provider;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setProvider(MarketProvider provider) {
        this.provider = provider;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
