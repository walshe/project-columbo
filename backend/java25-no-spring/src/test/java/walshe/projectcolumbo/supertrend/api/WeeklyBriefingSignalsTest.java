package walshe.projectcolumbo.supertrend.api;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.ScanConditionMatch;
import walshe.projectcolumbo.supertrend.signal.ScanResult;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyBriefingSignalsTest {

    @Test
    void byD1MovementAscendingSortsSmallestFirstAndNullsLast() {
        List<ScanResult> results = List.of(
                resultWithD1Pct("POS", "5.00"),
                resultWithD1Pct("NEG", "-3.00"),
                resultWithD1Pct("NULL", null));

        List<String> order = results.stream()
                .sorted(WeeklyBriefingSignals.byD1Movement(false))
                .map(ScanResult::symbol)
                .toList();

        assertThat(order).containsExactly("NEG", "POS", "NULL");
    }

    /**
     * Regression test: {@code nullsLast(naturalOrder()).reversed()} reverses null placement along
     * with everything else, silently turning "nulls last" into "nulls first" - this asserts the
     * fix ({@code nullsLast(reverseOrder())}) keeps nulls last in both directions.
     */
    @Test
    void byD1MovementDescendingSortsLargestFirstButStillNullsLast() {
        List<ScanResult> results = List.of(
                resultWithD1Pct("POS", "5.00"),
                resultWithD1Pct("NEG", "-3.00"),
                resultWithD1Pct("NULL", null));

        List<String> order = results.stream()
                .sorted(WeeklyBriefingSignals.byD1Movement(true))
                .map(ScanResult::symbol)
                .toList();

        assertThat(order).containsExactly("POS", "NEG", "NULL");
    }

    private static ScanResult resultWithD1Pct(String symbol, String pct) {
        BigDecimal pctChange = pct != null ? new BigDecimal(pct) : null;
        ScanConditionMatch d1Match = new ScanConditionMatch(Timeframe.D1, TrendState.BULLISH, null, null, pctChange, null);
        return new ScanResult(symbol, AssetClass.CRYPTO, List.of(d1Match), BigDecimal.ZERO);
    }
}
