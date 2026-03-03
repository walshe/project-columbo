package walshe.projectcolumbo.api.v1.scan;

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
import walshe.projectcolumbo.persistence.*;

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

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        signalStateRepository.deleteAll();
        assetRepository.deleteAll();
    }

    @Test
    void shouldExecuteSingleConditionScan() throws Exception {
        Asset btc = createAsset("BTCUSDT");
        OffsetDateTime now = OffsetDateTime.now();
        createSignal(btc, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.AND,
                List.of(new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL))
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
        OffsetDateTime now = OffsetDateTime.now();

        // BTC matches both
        createSignal(btc, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);
        createSignal(btc, IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, now);

        // ETH matches only one
        createSignal(eth, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.AND,
                List.of(
                        new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL),
                        new ScanCondition(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60)
                )
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
        OffsetDateTime now = OffsetDateTime.now();

        createSignal(btc, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);
        createSignal(eth, IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, now);

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.OR,
                List.of(
                        new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL),
                        new ScanCondition(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60)
                )
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
                List.of(new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.CROSSED_ABOVE_60)) // Invalid event for ST
        );

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private Asset createAsset(String symbol) {
        Asset asset = new Asset(symbol, symbol, MarketProvider.BINANCE, true);
        return assetRepository.save(asset);
    }

    private void createSignal(Asset asset, IndicatorType type, SignalEvent event, OffsetDateTime time) {
        SignalState s = new SignalState();
        s.setAsset(asset);
        s.setIndicatorType(type);
        s.setEvent(event);
        s.setCloseTime(time);
        s.setTimeframe(Timeframe.D1);
        s.setTrendState(TrendState.BULLISH);
        signalStateRepository.save(s);
    }
}
