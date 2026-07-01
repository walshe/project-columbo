package walshe.projectcolumbo.api.v1.mapper;

import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.util.TradingViewUtil;
import walshe.projectcolumbo.persistence.entity.SignalState;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

public class SignalStateMapper {
    public static SignalStateDto toDto(SignalState latest, SignalState lastFlip, OffsetDateTime now) {
        return toDto(latest, lastFlip, now, null);
    }

    public static SignalStateDto toDto(SignalState latest, SignalState lastFlip, OffsetDateTime now, BigDecimal avgVolume7d) {
        return toDto(latest, lastFlip, now, avgVolume7d, null, null);
    }

    public static SignalStateDto toDto(SignalState latest, SignalState lastFlip, OffsetDateTime now, BigDecimal avgVolume7d,
                                        BigDecimal flipClosePrice, BigDecimal latestClosePrice) {
        Long daysSinceFlip = null;
        OffsetDateTime lastFlipTime = null;

        if (lastFlip != null) {
            lastFlipTime = lastFlip.getCloseTime();
            daysSinceFlip = ChronoUnit.DAYS.between(lastFlipTime.toLocalDate(), now.toLocalDate());
        }

        BigDecimal pctChangeSinceFlip = null;
        if (flipClosePrice != null && latestClosePrice != null && flipClosePrice.compareTo(BigDecimal.ZERO) != 0) {
            pctChangeSinceFlip = latestClosePrice.subtract(flipClosePrice)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(flipClosePrice, 2, RoundingMode.HALF_UP);
        }

        return new SignalStateDto(
            latest.getAsset().getSymbol(),
            latest.getTrendState(),
            lastFlipTime,
            daysSinceFlip,
            avgVolume7d,
            TradingViewUtil.generateUrl(latest.getAsset().getProvider(), latest.getAsset().getSymbol(), latest.getTimeframe()),
            pctChangeSinceFlip
        );
    }
}
