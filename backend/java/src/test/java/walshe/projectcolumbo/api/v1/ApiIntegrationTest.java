package walshe.projectcolumbo.api.v1;
import walshe.projectcolumbo.persistence.entity.Asset;
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
import walshe.projectcolumbo.persistence.repository.SignalStateRepository;
import walshe.projectcolumbo.persistence.repository.SuperTrendRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import walshe.projectcolumbo.TestcontainersConfiguration;


import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApiIntegrationTest {

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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        signalStateRepository.deleteAll();
        superTrendRepository.deleteAll();
        candleRepository.deleteAll();
        marketBreadthSnapshotRepository.deleteAll();
        assetRepository.deleteAll();
    }

    @Test
    void shouldGetSignals() throws Exception {
        Asset btc = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));
        Asset eth = assetRepository.save(new Asset("ETHUSDT", "Ethereum", MarketProvider.BINANCE, true));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        OffsetDateTime t1 = now.minusDays(2);
        OffsetDateTime t2 = now.minusDays(1);

        signalStateRepository.save(new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));
        signalStateRepository.save(new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, t2, TrendState.BULLISH, SignalEvent.NONE));

        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.BEARISH, SignalEvent.BEARISH_REVERSAL));
        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, t2, TrendState.BEARISH, SignalEvent.NONE));

        mockMvc.perform(get("/api/v1/signals")
                        .param("timeframe", "D1")
                        .param("indicatorType", "SUPERTREND")
                        .param("sort", "ASSET_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$[0].trendState").value("BULLISH"))
                .andExpect(jsonPath("$[0].tradingviewUrl").value("https://www.tradingview.com/chart/?symbol=BINANCE%3ABTCUSDT&interval=1D"))
                .andExpect(jsonPath("$[1].symbol").value("ETHUSDT"))
                .andExpect(jsonPath("$[1].trendState").value("BEARISH"))
                .andExpect(jsonPath("$[1].tradingviewUrl").value("https://www.tradingview.com/chart/?symbol=BINANCE%3AETHUSDT&interval=1D"));
    }

    @Test
    void shouldGetSignalsWithStateFilter() throws Exception {
        Asset btc = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));
        Asset eth = assetRepository.save(new Asset("ETHUSDT", "Ethereum", MarketProvider.BINANCE, true));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        OffsetDateTime t1 = now.minusDays(1);

        signalStateRepository.save(new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));
        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.BEARISH, SignalEvent.BEARISH_REVERSAL));

        mockMvc.perform(get("/api/v1/signals")
                        .param("timeframe", "D1")
                        .param("indicatorType", "SUPERTREND")
                        .param("state", "BULLISH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol").value("BTCUSDT"));
    }

    @Test
    void shouldGetAssetsByState() throws Exception {
        Asset btc = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));
        Asset eth = assetRepository.save(new Asset("ETHUSDT", "Ethereum", MarketProvider.BINANCE, true));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        OffsetDateTime t1 = now.minusDays(1);

        signalStateRepository.save(new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));
        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.BEARISH, SignalEvent.BEARISH_REVERSAL));

        mockMvc.perform(get("/api/v1/assets/by-state")
                        .param("timeframe", "D1")
                        .param("indicatorType", "SUPERTREND")
                        .param("state", "BEARISH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol").value("ETHUSDT"))
                .andExpect(jsonPath("$[0].tradingviewUrl").value("https://www.tradingview.com/chart/?symbol=BINANCE%3AETHUSDT&interval=1D"));
    }

    @Test
    void shouldGetLatestMarketPulse() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        
        marketBreadthSnapshotRepository.save(new MarketBreadthSnapshot(Timeframe.D1, IndicatorType.SUPERTREND, now.minusDays(1), 10, 5, 0, 15, new BigDecimal("0.6667")));
        marketBreadthSnapshotRepository.save(new MarketBreadthSnapshot(Timeframe.D1, IndicatorType.SUPERTREND, now, 12, 3, 0, 15, new BigDecimal("0.8000")));

        mockMvc.perform(get("/api/v1/market-pulse")
                        .param("timeframe", "D1")
                        .param("indicatorType", "SUPERTREND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bullishCount").value(12))
                .andExpect(jsonPath("$.bullishRatio").value(0.8000));
    }

    @Test
    void shouldGetMarketPulseHistory() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        
        marketBreadthSnapshotRepository.save(new MarketBreadthSnapshot(Timeframe.D1, IndicatorType.SUPERTREND, now.minusDays(2), 8, 7, 0, 15, new BigDecimal("0.5333")));
        marketBreadthSnapshotRepository.save(new MarketBreadthSnapshot(Timeframe.D1, IndicatorType.SUPERTREND, now.minusDays(1), 10, 5, 0, 15, new BigDecimal("0.6667")));

        mockMvc.perform(get("/api/v1/market-pulse/history")
                        .param("timeframe", "D1")
                        .param("indicatorType", "SUPERTREND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].bullishCount").value(8))
                .andExpect(jsonPath("$[1].bullishCount").value(10));
    }

    @Test
    void shouldReturn400ForInvalidParams() throws Exception {
        mockMvc.perform(get("/api/v1/signals")
                        .param("timeframe", "INVALID")
                        .param("indicatorType", "SUPERTREND"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Parameter Value"));
    }

    @Test
    void shouldAcceptTimeframe1D() throws Exception {
        mockMvc.perform(get("/api/v1/signals")
                        .param("timeframe", "1D")
                        .param("indicatorType", "SUPERTREND"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn400ForMissingParams() throws Exception {
        mockMvc.perform(get("/api/v1/signals")
                        .param("timeframe", "D1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing Required Parameter"));
    }
}
