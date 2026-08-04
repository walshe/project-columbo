package walshe.projectcolumbo.supertrend.shared;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimeframeTest {

    @ParameterizedTest(name = "{0}: close {1} -> open {2}")
    @CsvSource({
            "D1, 2026-08-02T23:59:59.999Z, 2026-08-02T00:00:00.000Z",
            "W1, 2026-08-02T23:59:59.999Z, 2026-07-27T00:00:00.000Z"
    })
    void openTimeForDerivesExactOpenFromCloseGivenBinancesCloseEqualsOpenPlusSpanMinusOneMsConvention(
            Timeframe timeframe, OffsetDateTime closeTime, OffsetDateTime expectedOpenTime) {
        assertThat(timeframe.openTimeFor(closeTime)).isEqualTo(expectedOpenTime);
    }
}
