package walshe.projectcolumbo.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Candle coverage and freshness for a single timeframe")
public record CandleCoverageDto(
    @Schema(description = "Close time of the oldest stored candle for this timeframe; null if none")
    OffsetDateTime earliest,

    @Schema(description = "Close time of the newest stored candle for this timeframe; null if none")
    OffsetDateTime latest,

    @Schema(description = "Start of the most recent finalized period as of now. When latest is at or "
            + "after this, the timeframe has the most recently finalized candle.")
    OffsetDateTime expectedLatest,

    @Schema(description = "True when latest has reached expectedLatest (i.e. the most recent finalized "
            + "candle is present)")
    boolean upToDate,

    @Schema(description = "Number of distinct assets with at least one candle in this timeframe")
    long assetCount
) {}
