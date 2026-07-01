package walshe.projectcolumbo.persistence.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AssetCloseAtTime(Long assetId, OffsetDateTime closeTime, BigDecimal close) {}
