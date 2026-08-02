package walshe.projectcolumbo.supertrend.signal;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScanServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2024, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void singleConditionReturnsAllCandidates() {
        ScanRequest request = new ScanRequest(ScanOperator.AND, List.of(
                new ScanCondition(Timeframe.D1, TrendState.BULLISH, null)
        ), null);
        List<List<SignalSummary>> candidates = List.of(List.of(summary("AAAUSDT", null), summary("BBBUSDT", null)));

        List<ScanResult> results = ScanService.combine(request, candidates, NOW);

        assertThat(results).extracting(ScanResult::symbol).containsExactly("AAAUSDT", "BBBUSDT");
    }

    @Test
    void andCombinedConditionsAcrossTimeframesRequireMatchingBoth() {
        ScanRequest request = new ScanRequest(ScanOperator.AND, List.of(
                new ScanCondition(Timeframe.D1, TrendState.BULLISH, null),
                new ScanCondition(Timeframe.W1, TrendState.BULLISH, null)
        ), null);
        List<List<SignalSummary>> candidates = List.of(
                List.of(summary("AAAUSDT", null), summary("BBBUSDT", null)), // D1 bullish
                List.of(summary("AAAUSDT", null))                             // W1 bullish - only AAA
        );

        List<ScanResult> results = ScanService.combine(request, candidates, NOW);

        assertThat(results).extracting(ScanResult::symbol).containsExactly("AAAUSDT");
        assertThat(results.get(0).matchedConditions()).hasSize(2);
        assertThat(results.get(0).matchedConditions()).extracting(ScanConditionMatch::timeframe)
                .containsExactly(Timeframe.D1, Timeframe.W1);
    }

    @Test
    void orCombinedConditionsUnionMatchesAndOnlyRecordConditionsActuallyMatched() {
        ScanRequest request = new ScanRequest(ScanOperator.OR, List.of(
                new ScanCondition(Timeframe.D1, TrendState.BULLISH, null),
                new ScanCondition(Timeframe.D1, TrendState.BEARISH, null)
        ), null);
        List<List<SignalSummary>> candidates = List.of(
                List.of(summary("AAAUSDT", null)),
                List.of(summary("BBBUSDT", null))
        );

        List<ScanResult> results = ScanService.combine(request, candidates, NOW);

        assertThat(results).extracting(ScanResult::symbol).containsExactly("AAAUSDT", "BBBUSDT");
        assertThat(results.get(0).matchedConditions()).hasSize(1); // AAA only matched the first (bullish) condition
        assertThat(results.get(1).matchedConditions()).hasSize(1);
    }

    @Test
    void maxDaysSinceFlipExcludesFlipsOlderThanTheLimit() {
        ScanRequest request = new ScanRequest(ScanOperator.AND, List.of(
                new ScanCondition(Timeframe.D1, TrendState.BULLISH, 10)
        ), null);
        List<List<SignalSummary>> candidates = List.of(List.of(
                summary("WITHIN", NOW.minusDays(10)),
                summary("BEYOND", NOW.minusDays(11)),
                summary("NEVERFLIPPED", null)
        ));

        List<ScanResult> results = ScanService.combine(request, candidates, NOW);

        assertThat(results).extracting(ScanResult::symbol).containsExactly("WITHIN");
    }

    @Test
    void andEarlyEmptyIntersectionYieldsNoResults() {
        ScanRequest request = new ScanRequest(ScanOperator.AND, List.of(
                new ScanCondition(Timeframe.D1, TrendState.BULLISH, null),
                new ScanCondition(Timeframe.W1, TrendState.BULLISH, null)
        ), null);
        List<List<SignalSummary>> candidates = List.of(
                List.of(summary("AAAUSDT", null)),
                List.of(summary("BBBUSDT", null)) // disjoint from D1's match
        );

        List<ScanResult> results = ScanService.combine(request, candidates, NOW);

        assertThat(results).isEmpty();
    }

    @Test
    void limitTruncatesResultsAfterCombination() {
        ScanRequest request = new ScanRequest(ScanOperator.AND, List.of(
                new ScanCondition(Timeframe.D1, TrendState.BULLISH, null)
        ), 2);
        List<List<SignalSummary>> candidates = List.of(List.of(
                summary("AAAUSDT", null), summary("BBBUSDT", null), summary("CCCUSDT", null)
        ));

        List<ScanResult> results = ScanService.combine(request, candidates, NOW);

        assertThat(results).hasSize(2);
    }

    @Test
    void noLimitReturnsEveryMatch() {
        ScanRequest request = new ScanRequest(ScanOperator.AND, List.of(
                new ScanCondition(Timeframe.D1, TrendState.BULLISH, null)
        ), null);
        List<List<SignalSummary>> candidates = List.of(List.of(
                summary("AAAUSDT", null), summary("BBBUSDT", null), summary("CCCUSDT", null)
        ));

        List<ScanResult> results = ScanService.combine(request, candidates, NOW);

        assertThat(results).hasSize(3);
    }

    @Test
    void resultsAreSortedBySymbol() {
        ScanRequest request = new ScanRequest(ScanOperator.AND, List.of(
                new ScanCondition(Timeframe.D1, TrendState.BULLISH, null)
        ), null);
        List<List<SignalSummary>> candidates = List.of(List.of(summary("ZZZUSDT", null), summary("AAAUSDT", null)));

        List<ScanResult> results = ScanService.combine(request, candidates, NOW);

        assertThat(results).extracting(ScanResult::symbol).containsExactly("AAAUSDT", "ZZZUSDT");
    }

    private static SignalSummary summary(String symbol, OffsetDateTime lastFlipTime) {
        return new SignalSummary(symbol, TrendState.BULLISH, lastFlipTime, BigDecimal.ZERO, null);
    }
}
