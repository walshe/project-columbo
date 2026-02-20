package walshe.projectcolumbo.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Data Transfer Object for Candle (OHLC) data.
 *
 * @param open       Open price
 * @param high       High price
 * @param low        Low price
 * @param close      Close price
 * @param volume     Volume
 * @param openTime   Opening timestamp (UTC)
 * @param closeTime  Closing timestamp (UTC)
 */
public record CandleDto(
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume,
    Instant openTime,
    Instant closeTime
) {}
