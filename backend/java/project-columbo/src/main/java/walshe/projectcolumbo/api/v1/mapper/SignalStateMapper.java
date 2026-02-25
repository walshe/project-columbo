package walshe.projectcolumbo.api.v1.mapper;

import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.persistence.SignalState;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

public class SignalStateMapper {
    public static SignalStateDto toDto(SignalState latest, SignalState lastFlip, OffsetDateTime now) {
        long daysSinceFlip = ChronoUnit.DAYS.between(lastFlip.getCloseTime(), now);
        return new SignalStateDto(
            latest.getAsset().getSymbol(),
            latest.getTrendState(),
            lastFlip.getCloseTime(),
            daysSinceFlip
        );
    }
}
