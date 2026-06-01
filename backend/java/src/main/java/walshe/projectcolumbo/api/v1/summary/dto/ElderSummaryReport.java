package walshe.projectcolumbo.api.v1.summary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import walshe.projectcolumbo.api.v1.dto.MarketPulseDto;
import walshe.projectcolumbo.api.v1.scan.dto.ScanResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = """
        Cross-timeframe Elder Impulse System + Market Thermometer brief.
        Combines W1 and D1 breadth, the primary trading shortlist, fresh weekly flips,
        and any active spike alerts into a single report. No timeframe selector — Elder
        always uses both weekly and daily together.
        """)
public record ElderSummaryReport(

        @Schema(description = "W1 Elder Impulse breadth — bullishCount = ELDER_IMPULSE_GREEN, bearishCount = ELDER_IMPULSE_RED, missingCount = ELDER_IMPULSE_NEUTRAL")
        MarketPulseDto w1ImpulsePulse,

        @Schema(description = "D1 Elder Impulse breadth — bullishCount = ELDER_IMPULSE_GREEN, bearishCount = ELDER_IMPULSE_RED, missingCount = ELDER_IMPULSE_NEUTRAL")
        MarketPulseDto d1ImpulsePulse,

        @Schema(description = "D1 Elder Thermometer breadth — bullishCount = ELDER_THERMOMETER_QUIET, bearishCount = ELDER_THERMOMETER_HOT + SPIKE, missingCount = no data")
        MarketPulseDto d1ThermometerPulse,

        @Schema(description = "Count of assets where both W1 and D1 impulse are GREEN — full bull alignment regardless of thermometer. Measures trend environment strength.")
        int w1d1BullAlignedCount,

        @Schema(description = "Count of assets where both W1 and D1 impulse are RED — full bear alignment regardless of thermometer. Measures trend environment strength.")
        int w1d1BearAlignedCount,

        @Schema(description = "Primary shortlist: assets passing all three Elder long entry conditions (W1 GREEN + D1 GREEN + D1 QUIET). These are the actionable long setups for tonight.")
        List<ScanResult> primaryShortlist,

        @Schema(description = "Primary bear shortlist: assets passing all three Elder short entry conditions (W1 RED + D1 RED + D1 QUIET). Actionable short setups for tonight.")
        List<ScanResult> primaryBearShortlist,

        @Schema(description = "Assets whose W1 Impulse flipped to GREEN within the last 7 days. Highest-conviction long setups — the weekly engine just switched on.")
        List<ScanResult> freshW1GreenFlips,

        @Schema(description = "Assets whose W1 Impulse flipped to RED within the last 7 days. Tighten stops on existing longs; early short setups on W1 RED + D1 RED alignment.")
        List<ScanResult> freshW1RedFlips,

        @Schema(description = "Assets showing D1 ELDER_THERMOMETER_SPIKE where W1 Impulse is GREEN — spike into an uptrend. Signal to take profits on longs, not open new positions.")
        List<ScanResult> spikeAlertsW1Green,

        @Schema(description = "Assets showing D1 ELDER_THERMOMETER_SPIKE where W1 Impulse is RED — spike into a downtrend (short-covering rally). Consider selling into strength.")
        List<ScanResult> spikeAlertsW1Red,

        @Schema(description = "Assets showing D1 ELDER_THERMOMETER_SPIKE where W1 Impulse is NEUTRAL — no clear weekly direction established. Context ambiguous; use extra caution.")
        List<ScanResult> spikeAlertsW1Neutral,

        @Schema(description = "Timestamp when the most recent successful ingestion pipeline run finished. " +
                "Tells you when the data was last processed. Null if no successful run has been recorded yet.")
        OffsetDateTime lastIngestionAt,

        @Schema(description = "Calendar date (UTC) of the most recent daily candle in the database. " +
                "Tells you what trading day the breadth, shortlist, and alerts are based on. " +
                "Null if no candles have been ingested yet.")
        LocalDate candlesThrough

) {}
