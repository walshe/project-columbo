package walshe.projectcolumbo.api.v1.summary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Cross-timeframe SuperTrend trend alignment report — confluence and retest setups on W1 and D1")
public record ConfluenceSummaryReport(
    @Schema(description = "Assets bullish on both W1 and D1 SuperTrend, ordered by D1 flip date descending")
    List<SignalStateDto> bullishConfluence,

    @Schema(description = "Assets W1 bullish but D1 recently flipped bearish (retest window), ordered by D1 flip date descending")
    List<SignalStateDto> bullishRetest,

    @Schema(description = "Assets bearish on both W1 and D1 SuperTrend, ordered by D1 flip date descending")
    List<SignalStateDto> bearishConfluence,

    @Schema(description = "Assets W1 bearish but D1 recently flipped bullish (retest window), ordered by D1 flip date descending")
    List<SignalStateDto> bearishRetest,

    @Schema(description = "Timestamp when the most recent successful ingestion pipeline run finished.")
    OffsetDateTime lastIngestionAt,

    @Schema(description = "Calendar date (UTC) of the most recent daily candle in the database.")
    LocalDate candlesThrough,

    @Schema(description = "True when D1 (the driving timeframe) is missing its most recent finalized " +
            "candle (data is behind). Same determination as /candles/coverage upToDate for D1.")
    boolean stale
) {
    /** Convenience for callers that don't set freshness (e.g. Markdown rendering / tests). */
    public ConfluenceSummaryReport(List<SignalStateDto> bullishConfluence,
                                   List<SignalStateDto> bullishRetest,
                                   List<SignalStateDto> bearishConfluence,
                                   List<SignalStateDto> bearishRetest,
                                   OffsetDateTime lastIngestionAt,
                                   LocalDate candlesThrough) {
        this(bullishConfluence, bullishRetest, bearishConfluence, bearishRetest,
                lastIngestionAt, candlesThrough, false);
    }
}
