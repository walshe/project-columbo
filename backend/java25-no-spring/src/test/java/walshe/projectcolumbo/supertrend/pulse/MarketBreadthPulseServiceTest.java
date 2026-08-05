package walshe.projectcolumbo.supertrend.pulse;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalEvent;
import walshe.projectcolumbo.supertrend.signal.SignalState;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketBreadthPulseServiceTest {

    private static final OffsetDateTime CLOSE_TIME = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void bullishBearishMissingCountsSumToTotalActiveAssets() {
        List<SignalState> latestStates = List.of(
                state(1, TrendState.BULLISH),
                state(2, TrendState.BULLISH),
                state(3, TrendState.BEARISH),
                state(4, TrendState.UNKNOWN)
        );

        // totalAssets(6) > latestStates.size()(4): two active assets have no signal_state row at
        // all yet - they must still land in missingCount for the invariant to hold.
        MarketBreadthSnapshot snapshot = MarketBreadthPulseService.snapshot(Timeframe.D1, null, CLOSE_TIME, latestStates, 6);

        assertThat(snapshot.bullishCount()).isEqualTo(2);
        assertThat(snapshot.bearishCount()).isEqualTo(1);
        assertThat(snapshot.missingCount()).isEqualTo(3);
        assertThat(snapshot.bullishCount() + snapshot.bearishCount() + snapshot.missingCount()).isEqualTo(snapshot.totalAssets());
    }

    @Test
    void bullishRatioIsBullishOverBullishPlusBearish() {
        List<SignalState> latestStates = List.of(state(1, TrendState.BULLISH), state(2, TrendState.BULLISH), state(3, TrendState.BEARISH));

        MarketBreadthSnapshot snapshot = MarketBreadthPulseService.snapshot(Timeframe.D1, null, CLOSE_TIME, latestStates, 3);

        assertThat(snapshot.bullishRatio()).isEqualByComparingTo(new BigDecimal("0.6667"));
    }

    @Test
    void bullishRatioIsZeroWhenNoDirectionalStatesExist() {
        List<SignalState> latestStates = List.of(state(1, TrendState.UNKNOWN));

        MarketBreadthSnapshot snapshot = MarketBreadthPulseService.snapshot(Timeframe.D1, null, CLOSE_TIME, latestStates, 1);

        assertThat(snapshot.bullishRatio()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void timeframeAndCloseTimePropagateUnchanged() {
        MarketBreadthSnapshot snapshot =
                MarketBreadthPulseService.snapshot(Timeframe.W1, null, CLOSE_TIME, List.of(state(1, TrendState.BULLISH)), 1);

        assertThat(snapshot.timeframe()).isEqualTo(Timeframe.W1);
        assertThat(snapshot.snapshotCloseTime()).isEqualTo(CLOSE_TIME);
    }

    @Test
    void assetClassPropagatesUnchanged() {
        MarketBreadthSnapshot snapshot =
                MarketBreadthPulseService.snapshot(Timeframe.D1, AssetClass.STOCK, CLOSE_TIME, List.of(state(1, TrendState.BULLISH)), 1);

        assertThat(snapshot.assetClass()).isEqualTo(AssetClass.STOCK);
    }

    @Test
    void nullAssetClassMeansCombined() {
        MarketBreadthSnapshot snapshot =
                MarketBreadthPulseService.snapshot(Timeframe.D1, null, CLOSE_TIME, List.of(state(1, TrendState.BULLISH)), 1);

        assertThat(snapshot.assetClass()).isNull();
    }

    private static SignalState state(long assetId, TrendState trendState) {
        return new SignalState(assetId, Timeframe.D1, CLOSE_TIME, trendState, SignalEvent.NONE);
    }
}
