package walshe.projectcolumbo.api.v1.summary;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.summary.dto.SummaryReport;
import walshe.projectcolumbo.api.v1.util.TradingViewUtil;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
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

    @Test
    void watchlistRendersFlipSectionsWithSymbols() {
        SignalStateDto bull = new SignalStateDto("BTC", TrendState.SUPERTREND_BULLISH,
                OffsetDateTime.now().minusDays(2), 2L, BigDecimal.valueOf(5_000_000),
                TradingViewUtil.generateUrl(MarketProvider.BINANCE, "BTC", Timeframe.D1), null);
        SignalStateDto bear = new SignalStateDto("ETH", TrendState.SUPERTREND_BEARISH,
                OffsetDateTime.now().minusDays(3), 3L, BigDecimal.valueOf(5_000_000),
                TradingViewUtil.generateUrl(MarketProvider.BINANCE, "ETH", Timeframe.D1), null);

        String watchlist = formatter.formatWatchlist(report(List.of(bull), List.of(bear)));

        assertThat(watchlist).contains("###Recent Bullish Flips");
        assertThat(watchlist).contains("BINANCE:BTCUSDT");
        assertThat(watchlist).contains("###Recent Bearish Flips");
        assertThat(watchlist).contains("BINANCE:ETHUSDT");
        // RSI sections empty → omitted; no per-asset detail
        assertThat(watchlist).doesNotContain("RSI Cross");
        assertThat(watchlist).doesNotContain("Vol:");
    }

    @Test
    void watchlistExcludesFlipsWithoutRecordedFlip() {
        // daysSinceFlip == null → not a "recent flip" → excluded, mirroring the Markdown section
        SignalStateDto noFlip = new SignalStateDto("XRP", TrendState.SUPERTREND_BULLISH,
                null, null, BigDecimal.valueOf(5_000_000),
                TradingViewUtil.generateUrl(MarketProvider.BINANCE, "XRP", Timeframe.D1), null);

        String watchlist = formatter.formatWatchlist(report(List.of(noFlip), List.of()));

        assertThat(watchlist).doesNotContain("BINANCE:XRPUSDT");
        assertThat(watchlist).doesNotContain("###Recent Bullish Flips");
    }
}
