package walshe.projectcolumbo.supertrend.signal;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.persistence.Asset;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.AssetVenue;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SignalQueryServiceTest {

    private static final OffsetDateTime LATEST = OffsetDateTime.of(2024, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime FLIP_EARLY = OffsetDateTime.of(2024, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime FLIP_LATE = OffsetDateTime.of(2024, 1, 8, 0, 0, 0, 0, ZoneOffset.UTC);

    private static final Asset ASSET_A = new Asset(1, "AAAUSDT", Provider.BINANCE, true, AssetClass.CRYPTO, AssetVenue.SPOT);
    private static final Asset ASSET_B = new Asset(2, "BBBUSDT", Provider.BINANCE, true, AssetClass.CRYPTO, AssetVenue.SPOT);
    private static final Asset ASSET_C = new Asset(3, "CCCUSDT", Provider.BINANCE, true, AssetClass.CRYPTO, AssetVenue.SPOT);

    @Test
    void defaultSortIsAssetAscendingWhenSortIsNull() {
        List<SignalState> latest = List.of(state(2, TrendState.BULLISH), state(1, TrendState.BULLISH));

        List<SignalSummary> result = SignalQueryService.summarize(latest, enrichment(Map.of(), Map.of(), Map.of(), Map.of()), null, null);

        assertThat(result).extracting(SignalSummary::symbol).containsExactly("AAAUSDT", "BBBUSDT");
    }

    @Test
    void stateFilterExcludesNonMatchingAssets() {
        List<SignalState> latest = List.of(state(1, TrendState.BULLISH), state(2, TrendState.BEARISH));

        List<SignalSummary> result = SignalQueryService.summarize(
                latest, enrichment(Map.of(), Map.of(), Map.of(), Map.of()), TrendState.BEARISH, null);

        assertThat(result).extracting(SignalSummary::symbol).containsExactly("BBBUSDT");
    }

    @Test
    void lastFlipAscNullsSortLast() {
        List<SignalState> latest = List.of(state(1, TrendState.BULLISH), state(2, TrendState.BULLISH), state(3, TrendState.BULLISH));
        Map<Long, SignalState> flips = Map.of(
                1L, flip(1, FLIP_LATE),
                2L, flip(2, FLIP_EARLY)
                // asset 3 never flipped
        );

        List<SignalSummary> result = SignalQueryService.summarize(
                latest, enrichment(flips, Map.of(), Map.of(), Map.of()), null, SignalSort.LAST_FLIP_ASC);

        assertThat(result).extracting(SignalSummary::symbol).containsExactly("BBBUSDT", "AAAUSDT", "CCCUSDT");
    }

    @Test
    void lastFlipDescNullsSortLastToo() {
        List<SignalState> latest = List.of(state(1, TrendState.BULLISH), state(2, TrendState.BULLISH), state(3, TrendState.BULLISH));
        Map<Long, SignalState> flips = Map.of(
                1L, flip(1, FLIP_LATE),
                2L, flip(2, FLIP_EARLY)
        );

        List<SignalSummary> result = SignalQueryService.summarize(
                latest, enrichment(flips, Map.of(), Map.of(), Map.of()), null, SignalSort.LAST_FLIP_DESC);

        assertThat(result).extracting(SignalSummary::symbol).containsExactly("AAAUSDT", "BBBUSDT", "CCCUSDT");
    }

    @Test
    void trendStateAscSortsByEnumDeclarationOrder() {
        List<SignalState> latest = List.of(
                state(1, TrendState.UNKNOWN), state(2, TrendState.BEARISH), state(3, TrendState.BULLISH));

        List<SignalSummary> result = SignalQueryService.summarize(
                latest, enrichment(Map.of(), Map.of(), Map.of(), Map.of()), null, SignalSort.TREND_STATE_ASC);

        assertThat(result).extracting(SignalSummary::trendState)
                .containsExactly(TrendState.BULLISH, TrendState.BEARISH, TrendState.UNKNOWN);
    }

    @Test
    void liquidityDescSortsHighestVolumeFirstWithMissingTreatedAsZero() {
        List<SignalState> latest = List.of(state(1, TrendState.BULLISH), state(2, TrendState.BULLISH), state(3, TrendState.BULLISH));
        Map<Long, BigDecimal> liquidity = Map.of(1L, new BigDecimal("10"), 2L, new BigDecimal("100"));

        List<SignalSummary> result = SignalQueryService.summarize(
                latest, enrichment(Map.of(), liquidity, Map.of(), Map.of()), null, SignalSort.LIQUIDITY_DESC);

        assertThat(result).extracting(SignalSummary::symbol).containsExactly("BBBUSDT", "AAAUSDT", "CCCUSDT");
    }

    @Test
    void pctChangeAscAndDescNullsAlwaysSortLast() {
        List<SignalState> latest = List.of(state(1, TrendState.BULLISH), state(2, TrendState.BULLISH), state(3, TrendState.BULLISH));
        Map<Long, SignalState> flips = Map.of(1L, flip(1, FLIP_EARLY), 2L, flip(2, FLIP_EARLY));
        Map<Long, BigDecimal> latestClose = Map.of(1L, new BigDecimal("120"), 2L, new BigDecimal("90"), 3L, new BigDecimal("50"));
        Map<Long, BigDecimal> flipClose = Map.of(1L, new BigDecimal("100"), 2L, new BigDecimal("100"));

        List<SignalSummary> asc = SignalQueryService.summarize(
                latest, enrichment(flips, Map.of(), latestClose, flipClose), null, SignalSort.PCT_CHANGE_ASC);
        assertThat(asc).extracting(SignalSummary::symbol).containsExactly("BBBUSDT", "AAAUSDT", "CCCUSDT");

        List<SignalSummary> desc = SignalQueryService.summarize(
                latest, enrichment(flips, Map.of(), latestClose, flipClose), null, SignalSort.PCT_CHANGE_DESC);
        assertThat(desc).extracting(SignalSummary::symbol).containsExactly("AAAUSDT", "BBBUSDT", "CCCUSDT");
    }

    @Test
    void pctChangeSinceFlipFormulaIsSignedPercentRoundedToTwoDecimals() {
        List<SignalState> latest = List.of(state(1, TrendState.BULLISH));
        Map<Long, SignalState> flips = Map.of(1L, flip(1, FLIP_EARLY));
        Map<Long, BigDecimal> latestClose = Map.of(1L, new BigDecimal("115.00"));
        Map<Long, BigDecimal> flipClose = Map.of(1L, new BigDecimal("100.00"));

        List<SignalSummary> result = SignalQueryService.summarize(
                latest, enrichment(flips, Map.of(), latestClose, flipClose), null, null);

        assertThat(result.get(0).pctChangeSinceFlip()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    void pctChangeSinceFlipIsNullWhenThereIsNoFlip() {
        List<SignalState> latest = List.of(state(1, TrendState.BULLISH));
        Map<Long, BigDecimal> latestClose = Map.of(1L, new BigDecimal("115.00"));

        List<SignalSummary> result = SignalQueryService.summarize(
                latest, enrichment(Map.of(), Map.of(), latestClose, Map.of()), null, null);

        assertThat(result.get(0).pctChangeSinceFlip()).isNull();
        assertThat(result.get(0).lastFlipTime()).isNull();
    }

    @Test
    void pctChangeSinceFlipIsNullWhenFlipCloseIsZero() {
        List<SignalState> latest = List.of(state(1, TrendState.BULLISH));
        Map<Long, SignalState> flips = Map.of(1L, flip(1, FLIP_EARLY));
        Map<Long, BigDecimal> latestClose = Map.of(1L, new BigDecimal("115.00"));
        Map<Long, BigDecimal> flipClose = Map.of(1L, BigDecimal.ZERO);

        List<SignalSummary> result = SignalQueryService.summarize(
                latest, enrichment(flips, Map.of(), latestClose, flipClose), null, null);

        assertThat(result.get(0).pctChangeSinceFlip()).isNull();
    }

    @Test
    void avgVolume7dDefaultsToZeroWhenMissing() {
        List<SignalState> latest = List.of(state(1, TrendState.BULLISH));

        List<SignalSummary> result = SignalQueryService.summarize(
                latest, enrichment(Map.of(), Map.of(), Map.of(), Map.of()), null, null);

        assertThat(result.get(0).avgVolume7d()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private static SignalQueryService.Enrichment enrichment(
            Map<Long, SignalState> flipsByAssetId,
            Map<Long, BigDecimal> avgVolume7dByAssetId,
            Map<Long, BigDecimal> latestCloseByAssetId,
            Map<Long, BigDecimal> flipCloseByAssetId) {
        return new SignalQueryService.Enrichment(assetsById(), flipsByAssetId, avgVolume7dByAssetId, latestCloseByAssetId, flipCloseByAssetId);
    }

    private static Map<Long, Asset> assetsById() {
        return Map.of(1L, ASSET_A, 2L, ASSET_B, 3L, ASSET_C);
    }

    private static SignalState state(long assetId, TrendState trendState) {
        return new SignalState(assetId, Timeframe.D1, LATEST, trendState, SignalEvent.NONE);
    }

    private static SignalState flip(long assetId, OffsetDateTime closeTime) {
        return new SignalState(assetId, Timeframe.D1, closeTime, TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL);
    }
}
