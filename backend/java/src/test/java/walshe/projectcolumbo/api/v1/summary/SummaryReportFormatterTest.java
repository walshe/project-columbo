package walshe.projectcolumbo.api.v1.summary;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.summary.dto.SummaryReport;
import walshe.projectcolumbo.persistence.model.TrendState;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryReportFormatterTest {

    private final SummaryReportFormatter formatter = new SummaryReportFormatter();

    private SignalStateDto dto(String symbol, TrendState state, long daysSinceFlip, BigDecimal pctChangeSinceFlip) {
        return new SignalStateDto(symbol, state,
                OffsetDateTime.now().minusDays(daysSinceFlip), daysSinceFlip,
                BigDecimal.valueOf(5_000_000), "http://tv/" + symbol, pctChangeSinceFlip);
    }

    private SummaryReport report(List<SignalStateDto> bullishSignals, List<SignalStateDto> bearishSignals) {
        return new SummaryReport(null, bullishSignals, bearishSignals, List.of(), List.of(), null, null);
    }

    @Test
    void showsPositivePctChangeSinceFlip() {
        SummaryReport report = report(
                List.of(dto("BTC/USDT", TrendState.SUPERTREND_BULLISH, 2, new BigDecimal("12.34"))),
                List.of());

        String md = formatter.formatMarkdown(report);

        assertThat(md).contains("+12.34%");
    }

    @Test
    void showsNegativePctChangeSinceFlip() {
        SummaryReport report = report(
                List.of(),
                List.of(dto("ETH/USDT", TrendState.SUPERTREND_BEARISH, 2, new BigDecimal("-3.21"))));

        String md = formatter.formatMarkdown(report);

        assertThat(md).contains("-3.21%");
    }

    @Test
    void omitsPctChangeWhenNull() {
        SummaryReport report = report(
                List.of(dto("BTC/USDT", TrendState.SUPERTREND_BULLISH, 2, null)),
                List.of());

        String md = formatter.formatMarkdown(report);

        assertThat(md).doesNotContain("since flip");
    }
}
