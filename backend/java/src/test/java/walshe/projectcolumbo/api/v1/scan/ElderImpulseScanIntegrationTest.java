package walshe.projectcolumbo.api.v1.scan;

import walshe.projectcolumbo.TestDatabaseCleaner;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.api.v1.scan.dto.ScanCondition;
import walshe.projectcolumbo.api.v1.scan.dto.ScanOperator;
import walshe.projectcolumbo.api.v1.scan.dto.ScanRequest;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.entity.SignalState;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.repository.EmaRepository;
import walshe.projectcolumbo.persistence.repository.MacdRepository;
import walshe.projectcolumbo.persistence.repository.RsiRepository;
import walshe.projectcolumbo.persistence.repository.SignalStateRepository;
import walshe.projectcolumbo.persistence.repository.SuperTrendRepository;
import walshe.projectcolumbo.persistence.repository.ThermometerRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// DISABLED: Elder Impulse System is not active — scan conditions on it are now rejected by
// ScanValidator, so these end-to-end scan tests no longer apply. Re-enable alongside Elder.
@org.junit.jupiter.api.Disabled("Elder Impulse System disabled — Elder scan conditions are rejected")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ElderImpulseScanIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private TestDatabaseCleaner testDatabaseCleaner;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private SignalStateRepository signalStateRepository;

    @Autowired
    private SuperTrendRepository superTrendRepository;

    @Autowired
    private RsiRepository rsiRepository;

    @Autowired
    private EmaRepository emaRepository;

    @Autowired
    private MacdRepository macdRepository;

    @Autowired
    private ThermometerRepository thermometerRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private static final OffsetDateTime CLOSE_TIME =
            OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).truncatedTo(ChronoUnit.DAYS);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        testDatabaseCleaner.cleanAll();
    }

    // ---- helpers ----

    private Asset createAsset(String symbol) {
        return assetRepository.save(new Asset(symbol, symbol, MarketProvider.BINANCE, true));
    }

    private void createCandle(Asset asset, Timeframe timeframe, OffsetDateTime closeTime) {
        Candle c = new Candle();
        c.setAsset(asset);
        c.setTimeframe(timeframe);
        c.setOpenTime(closeTime);
        c.setCloseTime(closeTime);
        c.setOpen(BigDecimal.valueOf(50000));
        c.setHigh(BigDecimal.valueOf(51000));
        c.setLow(BigDecimal.valueOf(49000));
        c.setClose(BigDecimal.valueOf(50000));
        c.setVolume(BigDecimal.valueOf(1000));
        c.setSource(MarketProvider.BINANCE);
        candleRepository.save(c);
    }

    private void createImpulseSignal(Asset asset, Timeframe tf, TrendState state, OffsetDateTime closeTime) {
        SignalEvent event = switch (state) {
            case ELDER_IMPULSE_GREEN -> SignalEvent.ELDER_IMPULSE_TURNED_GREEN;
            case ELDER_IMPULSE_RED -> SignalEvent.ELDER_IMPULSE_TURNED_RED;
            default -> SignalEvent.ELDER_IMPULSE_TURNED_NEUTRAL;
        };
        signalStateRepository.save(new SignalState(asset, tf, IndicatorType.ELDER_IMPULSE, closeTime, state, event));
    }

    // ---- tests ----

    @Test
    void shouldReturnAsset_whenElderImpulseGreenStateMatches() throws Exception {
        Asset btc = createAsset("BTCUSDT");
        Asset eth = createAsset("ETHUSDT");

        createCandle(btc, Timeframe.D1, CLOSE_TIME);
        createCandle(eth, Timeframe.D1, CLOSE_TIME);

        createImpulseSignal(btc, Timeframe.D1, TrendState.ELDER_IMPULSE_GREEN, CLOSE_TIME);
        createImpulseSignal(eth, Timeframe.D1, TrendState.ELDER_IMPULSE_RED, CLOSE_TIME);

        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.D1, IndicatorType.ELDER_IMPULSE, null, TrendState.ELDER_IMPULSE_GREEN, null, null)),
                null
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].assetSymbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.results[0].matchedIndicators[0].indicatorType").value("ELDER_IMPULSE"))
                .andExpect(jsonPath("$.results[0].matchedIndicators[0].state").value("ELDER_IMPULSE_GREEN"))
                .andExpect(jsonPath("$.results[0].matchedIndicators[0].daysSinceChange").isNumber());
    }

    @Test
    void shouldReturnEmpty_whenNoElderImpulseGreenExists() throws Exception {
        Asset btc = createAsset("BTCUSDT");

        createCandle(btc, Timeframe.D1, CLOSE_TIME);
        createImpulseSignal(btc, Timeframe.D1, TrendState.ELDER_IMPULSE_RED, CLOSE_TIME);

        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.D1, IndicatorType.ELDER_IMPULSE, null, TrendState.ELDER_IMPULSE_GREEN, null, null)),
                null
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    @Test
    void shouldIntersect_withW1AndD1ElderImpulseConditions() throws Exception {
        Asset btc = createAsset("BTCUSDT");
        Asset eth = createAsset("ETHUSDT");

        // BTC: W1 GREEN + D1 GREEN → should match
        createCandle(btc, Timeframe.W1, CLOSE_TIME);
        createCandle(btc, Timeframe.D1, CLOSE_TIME);
        createImpulseSignal(btc, Timeframe.W1, TrendState.ELDER_IMPULSE_GREEN, CLOSE_TIME);
        createImpulseSignal(btc, Timeframe.D1, TrendState.ELDER_IMPULSE_GREEN, CLOSE_TIME);

        // ETH: W1 GREEN + D1 RED → should not match (D1 is not GREEN)
        createCandle(eth, Timeframe.W1, CLOSE_TIME);
        createCandle(eth, Timeframe.D1, CLOSE_TIME);
        createImpulseSignal(eth, Timeframe.W1, TrendState.ELDER_IMPULSE_GREEN, CLOSE_TIME);
        createImpulseSignal(eth, Timeframe.D1, TrendState.ELDER_IMPULSE_RED, CLOSE_TIME);

        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(
                        new ScanCondition(Timeframe.W1, IndicatorType.ELDER_IMPULSE, null, TrendState.ELDER_IMPULSE_GREEN, null, null),
                        new ScanCondition(Timeframe.D1, IndicatorType.ELDER_IMPULSE, null, TrendState.ELDER_IMPULSE_GREEN, null, null)
                ),
                null
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].assetSymbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.results[0].matchedIndicators", hasSize(2)));
    }
}
