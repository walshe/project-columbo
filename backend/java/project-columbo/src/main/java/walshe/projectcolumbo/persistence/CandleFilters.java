package walshe.projectcolumbo.persistence;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class CandleFilters {

    private CandleFilters() {}

    public static OffsetDateTime utcMidnightToday(OffsetDateTime now) {
        Objects.requireNonNull(now, "now must not be null");
        LocalDate utcDate = now.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        return utcDate.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    public static List<Candle> finalizedBeforeUtcMidnightToday(List<Candle> candles, OffsetDateTime now) {
        OffsetDateTime boundary = utcMidnightToday(now);
        return candles.stream()
                .filter(c -> c.getCloseTime().isBefore(boundary))
                .sorted(Comparator.comparing(Candle::getCloseTime))
                .collect(Collectors.toList());
    }
}
