package walshe.projectcolumbo.persistence.service;

import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.marketpulse.MarketPulseService;
import walshe.projectcolumbo.marketpulse.W1IndicatorService;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class W1IndicatorPipelineIntegrationTest {

    @Autowired
    private SuperTrendService superTrendService;

    @Autowired
    private RsiComputationService rsiComputationService;

    @Autowired
    private SignalStateService signalStateService;

    @Autowired
    private MarketPulseService marketPulseService;

    @Autowired
    private W1IndicatorService w1IndicatorService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private SuperTrendRepository superTrendRepository;

    @Autowired
    private RsiRepository rsiRepository;

    @Autowired
    private SignalStateRepository signalStateRepository;

    @Autowired
    private MarketBreadthSnapshotRepository marketBreadthSnapshotRepository;

    @BeforeEach
    void setUp() {
        marketBreadthSnapshotRepository.deleteAll();
        signalStateRepository.deleteAll();
        superTrendRepository.deleteAll();
        rsiRepository.deleteAll();
        candleRepository.deleteAll();
        assetRepository.deleteAll();
    }

    private Asset seedAssetWithW1Candles(String symbol, int count) {
        Asset asset = assetRepository.save(new Asset(symbol, symbol, MarketProvider.BINANCE, true));
        // Sunday close_time: 2024-01-07 23:59:59.999 UTC is the first Sunday fixture
        OffsetDateTime sundayBase = OffsetDateTime.of(2024, 1, 7, 23, 59, 59, 999_000_000, ZoneOffset.UTC);
        for (int i = 0; i < count; i++) {
            Candle w1 = new Candle();
            w1.setAsset(asset);
            w1.setTimeframe(Timeframe.W1);
            w1.setOpenTime(sundayBase.minusDays(6).plusWeeks(i).withHour(0).withMinute(0).withSecond(0).withNano(0));
            w1.setCloseTime(sundayBase.plusWeeks(i));
            w1.setOpen(new BigDecimal("40000"));
            w1.setHigh(new BigDecimal("41000"));
            w1.setLow(new BigDecimal("39000"));
            w1.setClose(new BigDecimal("40500"));
            w1.setVolume(new BigDecimal("1000"));
            w1.setSource(MarketProvider.BINANCE);
            candleRepository.save(w1);
        }
        return asset;
    }

    @Test
    void supertrend_W1_isComputed() {
        Asset btc = seedAssetWithW1Candles("BTCUSDT", 15);

        superTrendService.processAllActiveAssets(Timeframe.W1, 10, new BigDecimal("2.0"), false);

        assertThat(superTrendRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(btc, Timeframe.W1))
                .isNotEmpty();
    }

    @Test
    void rsi_W1_isComputed() {
        Asset btc = seedAssetWithW1Candles("BTCUSDT", 15);

        rsiComputationService.computeForActiveAssets(Timeframe.W1, 14, false);

        assertThat(rsiRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(btc, Timeframe.W1))
                .isNotEmpty();
    }

    @Test
    void indicators_W1_areIdempotent() {
        seedAssetWithW1Candles("BTCUSDT", 15);

        superTrendService.processAllActiveAssets(Timeframe.W1, 10, new BigDecimal("2.0"), false);
        rsiComputationService.computeForActiveAssets(Timeframe.W1, 14, false);
        long superTrendCountFirst = superTrendRepository.count();
        long rsiCountFirst = rsiRepository.count();

        // Re-run — must not add rows
        superTrendService.processAllActiveAssets(Timeframe.W1, 10, new BigDecimal("2.0"), false);
        rsiComputationService.computeForActiveAssets(Timeframe.W1, 14, false);

        assertThat(superTrendRepository.count()).isEqualTo(superTrendCountFirst);
        assertThat(rsiRepository.count()).isEqualTo(rsiCountFirst);
    }

    @Test
    void signalState_W1_isDetected() {
        seedAssetWithW1Candles("BTCUSDT", 15);

        superTrendService.processAllActiveAssets(Timeframe.W1, 10, new BigDecimal("2.0"), false);
        rsiComputationService.computeForActiveAssets(Timeframe.W1, 14, false);
        signalStateService.detectDaily(); // iterates Timeframe.values() — covers W1 automatically

        assertThat(signalStateRepository.findAll().stream()
                .filter(s -> s.getTimeframe() == Timeframe.W1))
                .isNotEmpty();
    }

    @Test
    void marketBreadth_W1_isComputed() {
        seedAssetWithW1Candles("BTCUSDT", 15);

        superTrendService.processAllActiveAssets(Timeframe.W1, 10, new BigDecimal("2.0"), false);
        rsiComputationService.computeForActiveAssets(Timeframe.W1, 14, false);
        signalStateService.detectDaily();
        marketPulseService.computeForTimeframe(Timeframe.W1);

        assertThat(marketBreadthSnapshotRepository.findAll().stream()
                .filter(s -> s.getTimeframe() == Timeframe.W1))
                .isNotEmpty();
    }

    @Test
    void orchestrator_processAllActiveAssets_producesAllOutputs() {
        Asset btc = seedAssetWithW1Candles("BTCUSDT", 15);

        // Single orchestrator call — no direct calls to underlying services in this method
        w1IndicatorService.processAllActiveAssets();

        assertThat(superTrendRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(btc, Timeframe.W1))
                .isNotEmpty();
        assertThat(rsiRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(btc, Timeframe.W1))
                .isNotEmpty();
        assertThat(signalStateRepository.findAll().stream()
                .filter(s -> s.getTimeframe() == Timeframe.W1))
                .isNotEmpty();
        assertThat(marketBreadthSnapshotRepository.findAll().stream()
                .filter(s -> s.getTimeframe() == Timeframe.W1))
                .isNotEmpty();
    }
}
