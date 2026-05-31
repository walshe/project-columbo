package walshe.projectcolumbo.api.v1;

import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.persistence.entity.MarketBreadthSnapshot;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.MarketBreadthSnapshotRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ElderImpulseMarketPulseIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MarketBreadthSnapshotRepository snapshotRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        snapshotRepository.deleteAll();
    }

    private MarketBreadthSnapshot buildSnapshot(IndicatorType type, Timeframe tf, int bullish, int bearish, int missing) {
        int total = bullish + bearish + missing;
        BigDecimal ratio = (bullish + bearish) > 0
                ? BigDecimal.valueOf(bullish).divide(BigDecimal.valueOf(bullish + bearish), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new MarketBreadthSnapshot(
                tf, type,
                OffsetDateTime.of(2024, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC),
                bullish, bearish, missing, total, ratio
        );
    }

    @Test
    void getLatestPulse_returns404_whenNoData() throws Exception {
        mockMvc.perform(get("/api/v1/elder-impulse-market-pulse")
                        .param("timeframe", "D1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getLatestPulse_returnsDto_whenDataExists() throws Exception {
        snapshotRepository.save(buildSnapshot(IndicatorType.ELDER_IMPULSE, Timeframe.D1, 7, 2, 1));

        mockMvc.perform(get("/api/v1/elder-impulse-market-pulse")
                        .param("timeframe", "D1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bullishCount").value(7))
                .andExpect(jsonPath("$.bearishCount").value(2))
                .andExpect(jsonPath("$.missingCount").value(1))
                .andExpect(jsonPath("$.totalAssets").value(10));
    }

    @Test
    void supertrendEndpoint_unaffectedByElderImpulseData() throws Exception {
        // Seed ELDER_IMPULSE snapshot
        snapshotRepository.save(buildSnapshot(IndicatorType.ELDER_IMPULSE, Timeframe.D1, 7, 2, 1));

        // SUPERTREND endpoint should return 404 — no SUPERTREND snapshot exists
        mockMvc.perform(get("/api/v1/supertrend-market-pulse")
                        .param("timeframe", "D1"))
                .andExpect(status().isNotFound());
    }
}
