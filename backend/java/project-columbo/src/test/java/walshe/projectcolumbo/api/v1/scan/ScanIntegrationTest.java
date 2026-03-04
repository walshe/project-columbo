package walshe.projectcolumbo.api.v1.scan;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.entity.RsiIndicator;
import walshe.projectcolumbo.persistence.entity.SignalState;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.repository.RsiRepository;
import walshe.projectcolumbo.persistence.repository.SignalStateRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.api.v1.scan.dto.ScanCondition;
import walshe.projectcolumbo.api.v1.scan.dto.ScanOperator;
import walshe.projectcolumbo.api.v1.scan.dto.ScanRequest;


import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ScanIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private SignalStateRepository signalStateRepository;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private RsiRepository rsiRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        signalStateRepository.deleteAll();
        rsiRepository.deleteAll();
        candleRepository.deleteAll();
        assetRepository.deleteAll();
    }

    @Test
    void shouldExecuteSingleConditionScan() throws Exception {
        Asset btc = createAsset("BTCUSDT");
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        createCandle(btc, now);
        createSignal(btc, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.AND,
                List.of(new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, null, null, null)),
                null
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].assetSymbol").value("BTCUSDT"));
    }

    @Test
    void shouldExecuteMultipleConditionsWithAND() throws Exception {
        Asset btc = createAsset("BTCUSDT");
        Asset eth = createAsset("ETHUSDT");
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        createCandle(btc, now);
        createCandle(eth, now);

        // BTC matches both
        createSignal(btc, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);
        createSignal(btc, IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, now);

        // ETH matches only one
        createSignal(eth, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.AND,
                List.of(
                        new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, null, null, null),
                        new ScanCondition(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, null, null, null)
                ),
                null
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].assetSymbol").value("BTCUSDT"));
    }

    @Test
    void shouldExecuteMultipleConditionsWithOR() throws Exception {
        Asset btc = createAsset("BTCUSDT");
        Asset eth = createAsset("ETHUSDT");
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        createCandle(btc, now);
        createCandle(eth, now);

        createSignal(btc, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);
        createSignal(eth, IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, now);

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.OR,
                List.of(
                        new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, null, null, null),
                        new ScanCondition(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, null, null, null)
                ),
                null
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[*].assetSymbol").value(org.hamcrest.Matchers.containsInAnyOrder("BTCUSDT", "ETHUSDT")));
    }

    @Test
    void shouldReturn400ForInvalidRequest() throws Exception {
        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.AND,
                List.of(new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.CROSSED_ABOVE_60, null, null, null)),
                null // Invalid event for ST
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldExecuteStateScanWithMaxDaysSinceFlip() throws Exception {
        Asset btc = createAsset("BTCUSDT");
        Asset eth = createAsset("ETHUSDT");
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        createCandle(btc, now);
        createCandle(eth, now);

        // BTC: BULLISH, flipped 2 days ago
        createSignal(btc, IndicatorType.SUPERTREND, SignalEvent.NONE, now.minusDays(2), TrendState.BULLISH);
        
        // ETH: BULLISH, flipped 10 days ago
        createSignal(eth, IndicatorType.SUPERTREND, SignalEvent.NONE, now.minusDays(10), TrendState.BULLISH);

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.AND,
                List.of(new ScanCondition(IndicatorType.SUPERTREND, null, TrendState.BULLISH, 5, null)),
                null
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].assetSymbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.results[0].matchedIndicators[0].indicatorType").value("SUPERTREND"))
                .andExpect(jsonPath("$.results[0].matchedIndicators[0].daysSinceFlip").value(2));
    }

    @Test
    void shouldReturnIndicatorSpecificFieldsForRsi() throws Exception {
        Asset btc = createAsset("BTCUSDT");
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        createCandle(btc, now);

        createSignal(btc, IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, now);
        createRsi(btc, now, 62.4);

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.AND,
                List.of(new ScanCondition(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, null, null, null)),
                null
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].assetSymbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.results[0].tradingviewUrl").value("https://www.tradingview.com/chart/?symbol=BINANCE%3ABTCUSDT&interval=1D"))
                .andExpect(jsonPath("$.results[0].matchedIndicators[0].indicatorType").value("RSI"))
                .andExpect(jsonPath("$.results[0].matchedIndicators[0].rsiValue").value(62.4))
                .andExpect(jsonPath("$.results[0].matchedIndicators[0].daysSinceCross").value(0));
    }

    @Test
    void shouldFilterByMaxDaysSinceCross() throws Exception {
        Asset btc = createAsset("BTCUSDT");
        Asset eth = createAsset("ETHUSDT");
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        createCandle(btc, now);
        createCandle(eth, now);

        // BTC: CROSSED_ABOVE_60 today
        createSignal(btc, IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, now);
        createRsi(btc, now, 62.4);

        // ETH: CROSSED_ABOVE_60 10 days ago
        createSignal(eth, IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, now.minusDays(10));
        createRsi(eth, now.minusDays(10), 61.5);

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.AND,
                List.of(new ScanCondition(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, null, null, 5)),
                null
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].assetSymbol").value("BTCUSDT"));
    }

    private void createRsi(Asset asset, OffsetDateTime time, double value) {
        RsiIndicator rsi = new RsiIndicator();
        rsi.setAsset(asset);
        rsi.setTimeframe(Timeframe.D1);
        rsi.setCloseTime(time);
        rsi.setRsiValue(java.math.BigDecimal.valueOf(value));
        rsiRepository.save(rsi);
    }

    private Asset createAsset(String symbol) {
        Asset asset = new Asset(symbol, symbol, MarketProvider.BINANCE, true);
        return assetRepository.save(asset);
    }

    private void createSignal(Asset asset, IndicatorType type, SignalEvent event, OffsetDateTime time) {
        createSignal(asset, type, event, time, TrendState.BULLISH);
    }

    private void createSignal(Asset asset, IndicatorType type, SignalEvent event, OffsetDateTime time, TrendState state) {
        SignalState s = new SignalState();
        s.setAsset(asset);
        s.setIndicatorType(type);
        s.setEvent(event);
        s.setCloseTime(time);
        s.setTimeframe(Timeframe.D1);
        s.setTrendState(state);
        signalStateRepository.save(s);
    }

    private void createCandle(Asset asset, OffsetDateTime closeTime) {
        Candle candle = new Candle();
        candle.setAsset(asset);
        candle.setTimeframe(Timeframe.D1);
        candle.setOpenTime(closeTime.minusDays(1));
        candle.setCloseTime(closeTime);
        candle.setOpen(java.math.BigDecimal.valueOf(100));
        candle.setHigh(java.math.BigDecimal.valueOf(110));
        candle.setLow(java.math.BigDecimal.valueOf(90));
        candle.setClose(java.math.BigDecimal.valueOf(105));
        candle.setVolume(java.math.BigDecimal.valueOf(1000));
        candle.setSource(MarketProvider.BINANCE);
        candleRepository.save(candle);
    }
}
