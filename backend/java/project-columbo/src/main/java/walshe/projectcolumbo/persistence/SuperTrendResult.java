package walshe.projectcolumbo.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SuperTrendResult(
        OffsetDateTime closeTime,
        BigDecimal atr,
        BigDecimal upperBand,
        BigDecimal lowerBand,
        BigDecimal supertrend,
        SuperTrendDirection direction
) {}
