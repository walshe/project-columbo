package walshe.projectcolumbo.api.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.api.v1.scan.dto.ScanCondition;
import walshe.projectcolumbo.api.v1.scan.dto.ScanOperator;
import walshe.projectcolumbo.api.v1.scan.dto.ScanRequest;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.entity.MarketBreadthSnapshot;
import walshe.projectcolumbo.persistence.entity.SignalState;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.repository.MarketBreadthSnapshotRepository;
import walshe.projectcolumbo.persistence.repository.RsiRepository;
import walshe.projectcolumbo.persistence.repository.SignalStateRepository;
import walshe.projectcolumbo.persistence.repository.SuperTrendRepository;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class W1ApiIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private SuperTrendRepository superTrendRepository;

    @Autowired
    private SignalStateRepository signalStateRepository;

    @Autowired
    private MarketBreadthSnapshotRepository marketBreadthSnapshotRepository;

    @Autowired
    private RsiRepository rsiRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        signalStateRepository.deleteAll();
        superTrendRepository.deleteAll();
        rsiRepository.deleteAll();
        candleRepository.deleteAll();
        marketBreadthSnapshotRepository.deleteAll();
        assetRepository.deleteAll();
    }

    @Test
    void marketPulseEndpoint_returnsW1Breadth() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);

        // Seed D1 breadth snapshot — bullishCount=7 (should NOT be returned)
        marketBreadthSnapshotRepository.save(new MarketBreadthSnapshot(
                Timeframe.D1, IndicatorType.SUPERTREND, now.minusDays(1),
                7, 5, 3, 15, new BigDecimal("0.4667")));

        // Seed W1 breadth snapshot — bullishCount=12 (should be returned)
        marketBreadthSnapshotRepository.save(new MarketBreadthSnapshot(
                Timeframe.W1, IndicatorType.SUPERTREND, now,
                12, 2, 1, 15, new BigDecimal("0.8000")));

        mockMvc.perform(get("/api/v1/market-pulse")
                        .param("timeframe", "W1")
                        .param("indicatorType", "SUPERTREND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bullishCount").value(12))
                .andExpect(jsonPath("$.bullishRatio").value(0.8000));
    }

    @Test
    void signalsEndpoint_returnsW1SignalStates() throws Exception {
        Asset btc = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));

        // closeTime must be strictly before boundary (now.truncatedTo(DAYS))
        OffsetDateTime boundary = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime signalCloseTime = boundary.minusDays(1);

        // Seed D1 signal — BEARISH (should NOT appear in W1 response)
        signalStateRepository.save(new SignalState(
                btc, Timeframe.D1, IndicatorType.SUPERTREND, signalCloseTime,
                TrendState.SUPERTREND_BEARISH, SignalEvent.SUPERTREND_BEARISH_REVERSAL));

        // Seed W1 signal — BULLISH (should appear in W1 response)
        signalStateRepository.save(new SignalState(
                btc, Timeframe.W1, IndicatorType.SUPERTREND, signalCloseTime,
                TrendState.SUPERTREND_BULLISH, SignalEvent.SUPERTREND_BULLISH_REVERSAL));

        mockMvc.perform(get("/api/v1/signals")
                        .param("timeframe", "W1")
                        .param("indicatorType", "SUPERTREND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$[0].trendState").value("SUPERTREND_BULLISH"));
    }

    @Test
    void scanEndpoint_returnsW1Results() throws Exception {
        Asset btc = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);

        // Seed a W1 Candle — required by ScanService.getLatestFinalizedCloseTime to build event-match window
        Candle w1Candle = new Candle();
        w1Candle.setAsset(btc);
        w1Candle.setTimeframe(Timeframe.W1);
        w1Candle.setOpenTime(now.minusDays(7));
        w1Candle.setCloseTime(now);
        w1Candle.setOpen(BigDecimal.valueOf(50000));
        w1Candle.setHigh(BigDecimal.valueOf(55000));
        w1Candle.setLow(BigDecimal.valueOf(48000));
        w1Candle.setClose(BigDecimal.valueOf(53000));
        w1Candle.setVolume(BigDecimal.valueOf(10000));
        w1Candle.setSource(MarketProvider.BINANCE);
        candleRepository.save(w1Candle);

        // Seed W1 SignalState — BULLISH_REVERSAL (should match the scan condition)
        SignalState w1Signal = new SignalState();
        w1Signal.setAsset(btc);
        w1Signal.setTimeframe(Timeframe.W1);
        w1Signal.setIndicatorType(IndicatorType.SUPERTREND);
        w1Signal.setEvent(SignalEvent.SUPERTREND_BULLISH_REVERSAL);
        w1Signal.setTrendState(TrendState.SUPERTREND_BULLISH);
        w1Signal.setCloseTime(now);
        signalStateRepository.save(w1Signal);

        // Seed D1 SignalState for same asset and same indicator/event — must NOT cause a duplicate
        SignalState d1Signal = new SignalState();
        d1Signal.setAsset(btc);
        d1Signal.setTimeframe(Timeframe.D1);
        d1Signal.setIndicatorType(IndicatorType.SUPERTREND);
        d1Signal.setEvent(SignalEvent.SUPERTREND_BULLISH_REVERSAL);
        d1Signal.setTrendState(TrendState.SUPERTREND_BULLISH);
        d1Signal.setCloseTime(now);
        signalStateRepository.save(d1Signal);

        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.W1, IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, null, null, null)),
                null
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].assetSymbol").value("BTCUSDT"));
    }
}
