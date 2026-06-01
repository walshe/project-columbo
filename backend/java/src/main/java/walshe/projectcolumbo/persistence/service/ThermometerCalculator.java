package walshe.projectcolumbo.persistence.service;

import walshe.projectcolumbo.persistence.entity.Candle;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculator for the Market Thermometer indicator.
 *
 * Temperature = MAX(High_today − High_yesterday, Low_yesterday − Low_today, 0)
 * Measures how much today's bar extends outside yesterday's range.
 * Always non-negative. Zero for inside bars.
 *
 * The 22-day EMA of the temperature series acts as the signal line.
 * temperatureEma is null for the first 21 results (insufficient history to seed EMA).
 */
@Component
public class ThermometerCalculator {

    public static final int EMA_PERIOD = 22;

    public record ThermometerResult(
            OffsetDateTime closeTime,
            BigDecimal temperature,
            BigDecimal temperatureEma  // null until 22 temperature values exist
    ) {}

    private final EmaCalculator emaCalculator;

    public ThermometerCalculator(EmaCalculator emaCalculator) {
        this.emaCalculator = emaCalculator;
    }

    /**
     * Computes the temperature series and its 22-day EMA from a list of candles.
     *
     * @param candles Candles sorted by close time ascending.
     * @return List of ThermometerResult starting at candles[1]; empty if fewer than 2 candles.
     */
    public List<ThermometerResult> calculate(List<Candle> candles) {
        if (candles == null || candles.size() < 2) {
            return List.of();
        }

        List<BigDecimal> tempValues = new ArrayList<>();
        List<OffsetDateTime> tempTimes = new ArrayList<>();

        for (int i = 1; i < candles.size(); i++) {
            Candle today = candles.get(i);
            Candle yesterday = candles.get(i - 1);

            BigDecimal highDiff = today.getHigh().subtract(yesterday.getHigh());
            BigDecimal lowDiff  = yesterday.getLow().subtract(today.getLow());
            BigDecimal temp = highDiff.max(lowDiff).max(BigDecimal.ZERO);

            tempValues.add(temp);
            tempTimes.add(today.getCloseTime());
        }

        // 22-day EMA of temperature series (empty if fewer than 22 temperature values)
        List<EmaCalculator.EmaResult> emaResults =
                emaCalculator.calculateFromValues(tempValues, tempTimes, EMA_PERIOD);

        Map<OffsetDateTime, BigDecimal> emaByTime = new HashMap<>();
        for (EmaCalculator.EmaResult r : emaResults) {
            emaByTime.put(r.closeTime(), r.emaValue());
        }

        List<ThermometerResult> results = new ArrayList<>();
        for (int i = 0; i < tempValues.size(); i++) {
            results.add(new ThermometerResult(
                    tempTimes.get(i),
                    tempValues.get(i),
                    emaByTime.get(tempTimes.get(i))  // null if not yet computed
            ));
        }

        return results;
    }
}
