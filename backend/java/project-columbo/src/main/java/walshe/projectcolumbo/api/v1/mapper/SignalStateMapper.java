package walshe.projectcolumbo.api.v1.mapper;

import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.util.TradingViewUtil;
import walshe.projectcolumbo.persistence.SignalState;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

public class SignalStateMapper {
    public static SignalStateDto toDto(SignalState latest, SignalState lastFlip, OffsetDateTime now) {
        return toDto(latest, lastFlip, now, null);
    }

    public static SignalStateDto toDto(SignalState latest, SignalState lastFlip, OffsetDateTime now, BigDecimal avgVolume7d) {
        Long daysSinceFlip = null;
        OffsetDateTime lastFlipTime = null;
        
        if (lastFlip != null) {
            lastFlipTime = lastFlip.getCloseTime();
            daysSinceFlip = ChronoUnit.DAYS.between(lastFlipTime, now);
        }

        return new SignalStateDto(
            latest.getAsset().getSymbol(),
            latest.getTrendState(),
            lastFlipTime,
            daysSinceFlip,
            avgVolume7d,
            TradingViewUtil.generateUrl(latest.getAsset().getProvider(), latest.getAsset().getSymbol(), latest.getTimeframe())
        );
    }
}
