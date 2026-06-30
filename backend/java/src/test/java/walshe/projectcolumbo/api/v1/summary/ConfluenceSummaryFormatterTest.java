package walshe.projectcolumbo.api.v1.summary;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.summary.dto.ConfluenceSummaryReport;
import walshe.projectcolumbo.persistence.model.TrendState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfluenceSummaryFormatterTest {

    private final SummaryReportFormatter formatter = new SummaryReportFormatter();

    private SignalStateDto dto(String symbol, long daysSinceFlip) {
        return new SignalStateDto(symbol, TrendState.SUPERTREND_BULLISH,
                OffsetDateTime.now().minusDays(daysSinceFlip), daysSinceFlip,
                BigDecimal.valueOf(5_000_000), "http://tv/" + symbol);
    }

    @Test
    void includesDataFreshnessHeader() {
        ConfluenceSummaryReport report = new ConfluenceSummaryReport(
                List.of(), List.of(),
                OffsetDateTime.now(), LocalDate.now());

        String md = formatter.formatConfluenceMarkdown(report);

        assertThat(md).contains("# SuperTrend Confluence Report");
        assertThat(md).contains("Data through");
        assertThat(md).contains("pipeline ran");
    }

    @Test
    void rendersBullishAndBearishSections() {
        ConfluenceSummaryReport report = new ConfluenceSummaryReport(
                List.of(dto("BTC/USDT", 2)),
                List.of(dto("ETH/USDT", 3)),
                null, null);

        String md = formatter.formatConfluenceMarkdown(report);

        assertThat(md).contains("## W1 + D1 Bullish Confluence");
        assertThat(md).contains("BTC/USDT");
        assertThat(md).contains("## W1 + D1 Bearish Confluence");
        assertThat(md).contains("ETH/USDT");
    }

    @Test
    void rendersEmptyListMessage() {
        ConfluenceSummaryReport report = new ConfluenceSummaryReport(
                List.of(), List.of(), null, null);

        String md = formatter.formatConfluenceMarkdown(report);

        assertThat(md).contains("None — no cross-timeframe alignment found.");
    }

    @Test
    void showsDaysSinceD1Flip() {
        ConfluenceSummaryReport report = new ConfluenceSummaryReport(
                List.of(dto("SOL/USDT", 4)), List.of(), null, null);

        String md = formatter.formatConfluenceMarkdown(report);

        assertThat(md).contains("4 day(s) ago");
    }
}
