package walshe.projectcolumbo.supertrend.signal;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.shared.AssetClass;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrendAlignmentServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2024, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void bullishConfluenceRequiresBullishOnBothTimeframes() {
        List<SignalSummary> w1Bullish = List.of(summary("AAAUSDT", null), summary("BBBUSDT", null));
        List<SignalSummary> d1Bullish = List.of(summary("AAAUSDT", null), summary("CCCUSDT", null)); // CCC missing from W1

        TrendAlignment alignment = TrendAlignmentService.align(w1Bullish, List.of(), d1Bullish, List.of(), 7, NOW);

        assertThat(alignment.bullishConfluence()).extracting(SignalSummary::symbol).containsExactly("AAAUSDT");
    }

    @Test
    void assetUnknownOnEitherTimeframeIsSilentlyExcludedFromEveryList() {
        // AAAUSDT is D1 bullish but never appears in a W1 list at all (e.g. W1 UNKNOWN) - it must
        // not appear in confluence or retest for either direction, with no special-case needed:
        // it's simply absent from every W1-derived symbol set.
        List<SignalSummary> d1Bullish = List.of(summary("AAAUSDT", null));

        TrendAlignment alignment = TrendAlignmentService.align(List.of(), List.of(), d1Bullish, List.of(), 7, NOW);

        assertThat(alignment.bullishConfluence()).isEmpty();
        assertThat(alignment.bullishRetest()).isEmpty();
        assertThat(alignment.bearishConfluence()).isEmpty();
        assertThat(alignment.bearishRetest()).isEmpty();
    }

    @Test
    void confluenceOrderFollowsD1InputOrderUnchanged() {
        List<SignalSummary> w1Bullish = List.of(summary("AAAUSDT", null), summary("BBBUSDT", null), summary("CCCUSDT", null));
        // Pre-sorted last-flip-descending by the caller (SignalQueryService) - intersection must preserve this order.
        List<SignalSummary> d1Bullish = List.of(summary("CCCUSDT", null), summary("AAAUSDT", null), summary("BBBUSDT", null));

        TrendAlignment alignment = TrendAlignmentService.align(w1Bullish, List.of(), d1Bullish, List.of(), 7, NOW);

        assertThat(alignment.bullishConfluence()).extracting(SignalSummary::symbol).containsExactly("CCCUSDT", "AAAUSDT", "BBBUSDT");
    }

    @Test
    void bullishRetestRequiresW1BullishAndD1RecentlyBearish() {
        List<SignalSummary> w1Bullish = List.of(summary("AAAUSDT", null));
        List<SignalSummary> d1Bearish = List.of(summary("AAAUSDT", NOW.minusDays(3)));

        TrendAlignment alignment = TrendAlignmentService.align(w1Bullish, List.of(), List.of(), d1Bearish, 7, NOW);

        assertThat(alignment.bullishRetest()).extracting(SignalSummary::symbol).containsExactly("AAAUSDT");
    }

    @Test
    void retestAgeBoundaryIsInclusive() {
        List<SignalSummary> w1Bullish = List.of(summary("EXACT", null), summary("OVER", null));
        List<SignalSummary> d1Bearish = List.of(
                summary("EXACT", NOW.minusDays(7)), // exactly maxRetestAgeDays - included
                summary("OVER", NOW.minusDays(8))    // one day beyond - excluded
        );

        TrendAlignment alignment = TrendAlignmentService.align(w1Bullish, List.of(), List.of(), d1Bearish, 7, NOW);

        assertThat(alignment.bullishRetest()).extracting(SignalSummary::symbol).containsExactly("EXACT");
    }

    @Test
    void retestExcludesAssetsWithNoRecordedFlip() {
        List<SignalSummary> w1Bullish = List.of(summary("NEVERFLIPPED", null));
        List<SignalSummary> d1Bearish = List.of(summary("NEVERFLIPPED", null));

        TrendAlignment alignment = TrendAlignmentService.align(w1Bullish, List.of(), List.of(), d1Bearish, 7, NOW);

        assertThat(alignment.bullishRetest()).isEmpty();
    }

    @Test
    void bearishConfluenceAndRetestAreTheMirrorOfBullish() {
        List<SignalSummary> w1Bearish = List.of(summary("AAAUSDT", null));
        List<SignalSummary> d1Bearish = List.of(summary("AAAUSDT", null));
        List<SignalSummary> d1Bullish = List.of(summary("BBBUSDT", NOW.minusDays(2)));
        List<SignalSummary> w1BearishForRetest = List.of(summary("BBBUSDT", null));

        TrendAlignment confluence = TrendAlignmentService.align(List.of(), w1Bearish, List.of(), d1Bearish, 7, NOW);
        assertThat(confluence.bearishConfluence()).extracting(SignalSummary::symbol).containsExactly("AAAUSDT");

        TrendAlignment retest = TrendAlignmentService.align(List.of(), w1BearishForRetest, d1Bullish, List.of(), 7, NOW);
        assertThat(retest.bearishRetest()).extracting(SignalSummary::symbol).containsExactly("BBBUSDT");
    }

    private static SignalSummary summary(String symbol, OffsetDateTime lastFlipTime) {
        return new SignalSummary(symbol, TrendState.BULLISH, lastFlipTime, BigDecimal.ZERO, null, null, AssetClass.CRYPTO, null);
    }
}
