package walshe.projectcolumbo.persistence.service;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.entity.RsiIndicator;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.SignalStateResult;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;
import walshe.projectcolumbo.persistence.service.RsiCalculator;
import walshe.projectcolumbo.persistence.service.SignalStateCalculator;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RsiCalculatorLogicTest {

    private final RsiCalculator calculator = new RsiCalculator();

    @Test
    void shouldMatchTradingViewLogicForSimpleSeries() {
        // Simple series where we can predict values
        // 15 candles for a 14-period RSI initial value
        List<Candle> candles = new ArrayList<>();
        BigDecimal base = new BigDecimal("100.00");
        for (int i = 0; i < 15; i++) {
            candles.add(createCandle(base.add(BigDecimal.valueOf(i)), i));
        }
        
        // This is a series of 14 gains of 1.0 each.
        // initial avgGain = (1*14)/14 = 1.0
        // initial avgLoss = 0
        // RSI should be 100
        
        List<RsiCalculator.RsiResult> results = calculator.calculate(candles, 14);
        
        assertThat(results).hasSize(1);
        assertThat(results.get(0).rsiValue()).isEqualByComparingTo("100.0000");
    }

    @Test
    void shouldHandleZeroGainsCase() {
        List<Candle> candles = new ArrayList<>();
        BigDecimal base = new BigDecimal("100.00");
        for (int i = 0; i < 15; i++) {
            candles.add(createCandle(base.subtract(BigDecimal.valueOf(i)), i));
        }
        
        // Series of 14 losses of 1.0 each.
        // initial avgGain = 0
        // initial avgLoss = 1.0
        // RS = 0/1 = 0
        // RSI = 100 - (100 / (1 + 0)) = 0
        
        List<RsiCalculator.RsiResult> results = calculator.calculate(candles, 14);
        
        assertThat(results).hasSize(1);
        assertThat(results.get(0).rsiValue()).isEqualByComparingTo("0.0000");
    }

    @Test
    void shouldHandleNoMovementCase() {
        List<Candle> candles = new ArrayList<>();
        BigDecimal base = new BigDecimal("100.00");
        for (int i = 0; i < 15; i++) {
            candles.add(createCandle(base, i));
        }
        
        List<RsiCalculator.RsiResult> results = calculator.calculate(candles, 14);
        
        assertThat(results).hasSize(1);
        // Matching Pine Script: down == 0 ? 100
        assertThat(results.get(0).rsiValue()).isEqualByComparingTo("100.0000");
    }

    @Test
    void shouldCalculateSignalStatesForRsiWithNeutralTransitions() {
        // Given
        SignalStateCalculator signalCalculator = new SignalStateCalculator();
        List<RsiIndicator> indicators = new ArrayList<>();
        Asset asset = new Asset();
        
        // Neutral
        indicators.add(createRsiIndicator(asset, "50.0000", 1));
        // Above 60 -> Cross from Neutral
        indicators.add(createRsiIndicator(asset, "65.0000", 2));
        // Neutral
        indicators.add(createRsiIndicator(asset, "55.0000", 3));
        // Below 40 -> Cross from Neutral
        indicators.add(createRsiIndicator(asset, "35.0000", 4));
        // Above 60 -> Cross from Below 40
        indicators.add(createRsiIndicator(asset, "61.0000", 5));
        
        // When
        List<SignalStateResult> results = signalCalculator.calculateRsi(indicators, null);
        
        // Then
        assertThat(results).hasSize(5);
        assertThat(results.get(0).trendState()).isEqualTo(TrendState.NEUTRAL);
        assertThat(results.get(0).event()).isEqualTo(SignalEvent.NONE);
        
        assertThat(results.get(1).trendState()).isEqualTo(TrendState.ABOVE_60);
        assertThat(results.get(1).event()).isEqualTo(SignalEvent.CROSSED_ABOVE_60);
        
        assertThat(results.get(2).trendState()).isEqualTo(TrendState.NEUTRAL);
        assertThat(results.get(2).event()).isEqualTo(SignalEvent.NONE);
        
        assertThat(results.get(3).trendState()).isEqualTo(TrendState.BELOW_40);
        assertThat(results.get(3).event()).isEqualTo(SignalEvent.CROSSED_BELOW_40);
        
        assertThat(results.get(4).trendState()).isEqualTo(TrendState.ABOVE_60);
        assertThat(results.get(4).event()).isEqualTo(SignalEvent.CROSSED_ABOVE_60);
    }

    private RsiIndicator createRsiIndicator(Asset asset, String value, int day) {
        RsiIndicator rsi = new RsiIndicator();
        rsi.setAsset(asset);
        rsi.setTimeframe(Timeframe.D1);
        rsi.setRsiValue(new BigDecimal(value));
        rsi.setCloseTime(OffsetDateTime.parse("2024-01-01T00:00:00Z").plusDays(day));
        return rsi;
    }

    private Candle createCandle(BigDecimal close, int day) {
        Candle c = new Candle();
        c.setClose(close);
        c.setCloseTime(OffsetDateTime.parse("2024-01-01T00:00:00Z").plusDays(day));
        return c;
    }
}
