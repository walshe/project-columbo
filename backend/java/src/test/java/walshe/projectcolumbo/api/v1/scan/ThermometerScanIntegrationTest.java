package walshe.projectcolumbo.api.v1.scan;

import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.api.v1.scan.dto.ScanCondition;
import walshe.projectcolumbo.api.v1.scan.dto.ScanOperator;
import walshe.projectcolumbo.api.v1.scan.dto.ScanRequest;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.entity.SignalState;
import walshe.projectcolumbo.persistence.entity.ThermometerIndicator;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.repository.SignalStateRepository;
import walshe.projectcolumbo.persistence.repository.ThermometerRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ThermometerScanIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private SignalStateRepository signalStateRepository;

    @Autowired
    private ThermometerRepository thermometerRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private static final OffsetDateTime CLOSE_TIME =
            OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).truncatedTo(ChronoUnit.DAYS);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        signalStateRepository.deleteAll();
        thermometerRepository.deleteAll();
        candleRepository.deleteAll();
        assetRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        signalStateRepository.deleteAll();
        thermometerRepository.deleteAll();
        candleRepository.deleteAll();
        assetRepository.deleteAll();
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

    private void createThermometerSignal(Asset asset, TrendState state, OffsetDateTime closeTime) {
        SignalEvent event = switch (state) {
            case ELDER_THERMOMETER_SPIKE -> SignalEvent.ELDER_THERMOMETER_TRIPLE_SPIKE;
            case ELDER_THERMOMETER_HOT   -> SignalEvent.ELDER_THERMOMETER_CROSSED_ABOVE_EMA;
            case ELDER_THERMOMETER_QUIET -> SignalEvent.NONE;
            default -> SignalEvent.NONE;
        };
        signalStateRepository.save(
                new SignalState(asset, Timeframe.D1, IndicatorType.ELDER_THERMOMETER,
                        closeTime, state, event));
    }

    private void createThermometerIndicator(Asset asset, BigDecimal temperature,
                                             BigDecimal temperatureEma, OffsetDateTime closeTime) {
        ThermometerIndicator row = new ThermometerIndicator();
        row.setAsset(asset);
        row.setCloseTime(closeTime);
        row.setTemperature(temperature);
        row.setTemperatureEma(temperatureEma);
        thermometerRepository.save(row);
    }

    // ---- tests ----

    @Test
    void shouldReturnAsset_withThermometerMatchIncludingRawValues() throws Exception {
        // temperature=1500, ema=3000 → QUIET (1500 < 3000)
        Asset btc = createAsset("BTCUSDT");
        createCandle(btc, Timeframe.D1, CLOSE_TIME);
        createThermometerSignal(btc, TrendState.ELDER_THERMOMETER_QUIET, CLOSE_TIME);
        createThermometerIndicator(btc,
                new BigDecimal("1500.00000000"),
                new BigDecimal("3000.00000000"),
                CLOSE_TIME);

        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.D1, IndicatorType.ELDER_THERMOMETER,
                        null, TrendState.ELDER_THERMOMETER_QUIET, null, null)),
                null
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].assetSymbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.results[0].matchedIndicators[0].indicatorType")
                        .value("ELDER_THERMOMETER"))
                .andExpect(jsonPath("$.results[0].matchedIndicators[0].state")
                        .value("ELDER_THERMOMETER_QUIET"))
                .andExpect(jsonPath("$.results[0].matchedIndicators[0].temperature").isNumber())
                .andExpect(jsonPath("$.results[0].matchedIndicators[0].temperatureEma").isNumber());
    }

    @Test
    void shouldReturnEmpty_whenNoThermometerQuietAssetExists() throws Exception {
        Asset btc = createAsset("BTCUSDT");
        createCandle(btc, Timeframe.D1, CLOSE_TIME);
        createThermometerSignal(btc, TrendState.ELDER_THERMOMETER_HOT, CLOSE_TIME);
        createThermometerIndicator(btc,
                new BigDecimal("4000.00000000"),
                new BigDecimal("3000.00000000"),
                CLOSE_TIME);

        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.D1, IndicatorType.ELDER_THERMOMETER,
                        null, TrendState.ELDER_THERMOMETER_QUIET, null, null)),
                null
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    @Test
    void shouldReturnAsset_withThermometerSpikeCondition() throws Exception {
        // SPIKE: temperature=7000, ema=2000, 3*ema=6000 → 7000 > 6000
        Asset eth = createAsset("ETHUSDT");
        createCandle(eth, Timeframe.D1, CLOSE_TIME);
        createThermometerSignal(eth, TrendState.ELDER_THERMOMETER_SPIKE, CLOSE_TIME);
        createThermometerIndicator(eth,
                new BigDecimal("7000.00000000"),
                new BigDecimal("2000.00000000"),
                CLOSE_TIME);

        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.D1, IndicatorType.ELDER_THERMOMETER,
                        null, TrendState.ELDER_THERMOMETER_SPIKE, null, null)),
                null
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].assetSymbol").value("ETHUSDT"))
                .andExpect(jsonPath("$.results[0].matchedIndicators[0].temperature").isNumber())
                .andExpect(jsonPath("$.results[0].matchedIndicators[0].temperatureEma").isNumber());
    }

    /**
     * Primary daily trading scan:
     * W1 IMPULSE_GREEN AND D1 IMPULSE_GREEN AND D1 THERMOMETER_QUIET
     *
     * BTC has all three conditions → should match.
     * ETH has W1 GREEN + D1 GREEN but D1 HOT thermometer → excluded.
     */
    @Test
    void shouldExecuteFullDailyTradingScan() throws Exception {
        Asset btc = createAsset("BTCUSDT");
        Asset eth = createAsset("ETHUSDT");

        // BTC: W1 impulse GREEN + D1 impulse GREEN + D1 thermometer QUIET
        createCandle(btc, Timeframe.W1, CLOSE_TIME);
        createCandle(btc, Timeframe.D1, CLOSE_TIME);
        signalStateRepository.save(new SignalState(btc, Timeframe.W1, IndicatorType.ELDER_IMPULSE,
                CLOSE_TIME, TrendState.ELDER_IMPULSE_GREEN, SignalEvent.ELDER_IMPULSE_TURNED_GREEN));
        signalStateRepository.save(new SignalState(btc, Timeframe.D1, IndicatorType.ELDER_IMPULSE,
                CLOSE_TIME, TrendState.ELDER_IMPULSE_GREEN, SignalEvent.ELDER_IMPULSE_TURNED_GREEN));
        createThermometerSignal(btc, TrendState.ELDER_THERMOMETER_QUIET, CLOSE_TIME);
        createThermometerIndicator(btc,
                new BigDecimal("1200.00000000"),
                new BigDecimal("2500.00000000"),
                CLOSE_TIME);

        // ETH: W1 impulse GREEN + D1 impulse GREEN + D1 thermometer HOT → should NOT match
        createCandle(eth, Timeframe.W1, CLOSE_TIME);
        createCandle(eth, Timeframe.D1, CLOSE_TIME);
        signalStateRepository.save(new SignalState(eth, Timeframe.W1, IndicatorType.ELDER_IMPULSE,
                CLOSE_TIME, TrendState.ELDER_IMPULSE_GREEN, SignalEvent.ELDER_IMPULSE_TURNED_GREEN));
        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.ELDER_IMPULSE,
                CLOSE_TIME, TrendState.ELDER_IMPULSE_GREEN, SignalEvent.ELDER_IMPULSE_TURNED_GREEN));
        createThermometerSignal(eth, TrendState.ELDER_THERMOMETER_HOT, CLOSE_TIME);
        createThermometerIndicator(eth,
                new BigDecimal("3500.00000000"),
                new BigDecimal("2500.00000000"),
                CLOSE_TIME);

        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(
                        new ScanCondition(Timeframe.W1, IndicatorType.ELDER_IMPULSE,
                                null, TrendState.ELDER_IMPULSE_GREEN, null, null),
                        new ScanCondition(Timeframe.D1, IndicatorType.ELDER_IMPULSE,
                                null, TrendState.ELDER_IMPULSE_GREEN, null, null),
                        new ScanCondition(Timeframe.D1, IndicatorType.ELDER_THERMOMETER,
                                null, TrendState.ELDER_THERMOMETER_QUIET, null, null)
                ),
                null
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].assetSymbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.results[0].matchedIndicators", hasSize(3)));
    }
}
