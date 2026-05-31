package walshe.projectcolumbo.persistence.service;

import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.EmaIndicator;
import walshe.projectcolumbo.persistence.entity.MacdIndicator;
import walshe.projectcolumbo.persistence.entity.SignalState;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.EmaRepository;
import walshe.projectcolumbo.persistence.repository.MacdRepository;
import walshe.projectcolumbo.persistence.repository.SignalStateRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ElderImpulseStateServiceIntegrationTest {

    @Autowired
    private ElderImpulseStateService elderImpulseStateService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private EmaRepository emaRepository;

    @Autowired
    private MacdRepository macdRepository;

    @Autowired
    private SignalStateRepository signalStateRepository;

    private static final OffsetDateTime T1 = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T2 = OffsetDateTime.of(2024, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        signalStateRepository.deleteAll();
        emaRepository.deleteAll();
        macdRepository.deleteAll();
        assetRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        signalStateRepository.deleteAll();
        emaRepository.deleteAll();
        macdRepository.deleteAll();
        assetRepository.deleteAll();
    }

    // ---- helpers ----

    private Asset seedAsset(String symbol) {
        return assetRepository.save(new Asset(symbol, symbol, MarketProvider.BINANCE, true));
    }

    private void seedEmaRow(Asset asset, Timeframe tf, int period, OffsetDateTime time, BigDecimal value) {
        EmaIndicator ema = new EmaIndicator();
        ema.setAsset(asset);
        ema.setTimeframe(tf);
        ema.setPeriod(period);
        ema.setCloseTime(time);
        ema.setEmaValue(value);
        emaRepository.save(ema);
    }

    private void seedMacdRow(Asset asset, Timeframe tf, OffsetDateTime time, BigDecimal histogram) {
        MacdIndicator macd = new MacdIndicator();
        macd.setAsset(asset);
        macd.setTimeframe(tf);
        macd.setCloseTime(time);
        macd.setMacdLine(BigDecimal.ONE);
        macd.setSignalLine(BigDecimal.ZERO);
        macd.setHistogram(histogram);
        macdRepository.save(macd);
    }

    private List<SignalState> getImpulseStates() {
        return signalStateRepository.findAll().stream()
                .filter(s -> s.getIndicatorType() == IndicatorType.ELDER_IMPULSE)
                .toList();
    }

    // ---- D1 tests ----

    @Test
    void d1GreenState_whenBothSlopesPositive() {
        Asset asset = seedAsset("BTCUSDT");
        seedEmaRow(asset, Timeframe.D1, 13, T1, new BigDecimal("50000"));
        seedEmaRow(asset, Timeframe.D1, 13, T2, new BigDecimal("51000"));
        seedMacdRow(asset, Timeframe.D1, T1, new BigDecimal("10"));
        seedMacdRow(asset, Timeframe.D1, T2, new BigDecimal("20"));

        elderImpulseStateService.computeForAllActiveAssets(Timeframe.D1);

        List<SignalState> states = getImpulseStates();
        assertThat(states).hasSize(1);
        assertThat(states.get(0).getTrendState()).isEqualTo(TrendState.ELDER_IMPULSE_GREEN);
        assertThat(states.get(0).getEvent()).isEqualTo(SignalEvent.ELDER_IMPULSE_TURNED_GREEN);
    }

    @Test
    void d1RedState_whenBothSlopesFalling() {
        Asset asset = seedAsset("BTCUSDT");
        seedEmaRow(asset, Timeframe.D1, 13, T1, new BigDecimal("51000"));
        seedEmaRow(asset, Timeframe.D1, 13, T2, new BigDecimal("50000"));
        seedMacdRow(asset, Timeframe.D1, T1, new BigDecimal("20"));
        seedMacdRow(asset, Timeframe.D1, T2, new BigDecimal("10"));

        elderImpulseStateService.computeForAllActiveAssets(Timeframe.D1);

        List<SignalState> states = getImpulseStates();
        assertThat(states).hasSize(1);
        assertThat(states.get(0).getTrendState()).isEqualTo(TrendState.ELDER_IMPULSE_RED);
        assertThat(states.get(0).getEvent()).isEqualTo(SignalEvent.ELDER_IMPULSE_TURNED_RED);
    }

    @Test
    void d1NeutralState_whenSlopesDiverge() {
        Asset asset = seedAsset("BTCUSDT");
        // EMA rising, MACD histogram falling
        seedEmaRow(asset, Timeframe.D1, 13, T1, new BigDecimal("50000"));
        seedEmaRow(asset, Timeframe.D1, 13, T2, new BigDecimal("51000"));
        seedMacdRow(asset, Timeframe.D1, T1, new BigDecimal("20"));
        seedMacdRow(asset, Timeframe.D1, T2, new BigDecimal("10"));

        elderImpulseStateService.computeForAllActiveAssets(Timeframe.D1);

        List<SignalState> states = getImpulseStates();
        assertThat(states).hasSize(1);
        assertThat(states.get(0).getTrendState()).isEqualTo(TrendState.ELDER_IMPULSE_NEUTRAL);
    }

    @Test
    void d1SkipsAsset_whenInsufficientEmaRows() {
        Asset asset = seedAsset("BTCUSDT");
        // Only 1 EMA row (needs 2)
        seedEmaRow(asset, Timeframe.D1, 13, T1, new BigDecimal("50000"));
        seedMacdRow(asset, Timeframe.D1, T1, new BigDecimal("10"));
        seedMacdRow(asset, Timeframe.D1, T2, new BigDecimal("20"));

        elderImpulseStateService.computeForAllActiveAssets(Timeframe.D1);

        List<SignalState> states = getImpulseStates();
        assertThat(states).isEmpty();
    }

    @Test
    void d1SkipsAsset_whenInsufficientMacdRows() {
        Asset asset = seedAsset("BTCUSDT");
        seedEmaRow(asset, Timeframe.D1, 13, T1, new BigDecimal("50000"));
        seedEmaRow(asset, Timeframe.D1, 13, T2, new BigDecimal("51000"));
        // Only 1 MACD row (needs 2)
        seedMacdRow(asset, Timeframe.D1, T1, new BigDecimal("10"));

        elderImpulseStateService.computeForAllActiveAssets(Timeframe.D1);

        List<SignalState> states = getImpulseStates();
        assertThat(states).isEmpty();
    }

    @Test
    void d1IsIdempotent() {
        Asset asset = seedAsset("BTCUSDT");
        seedEmaRow(asset, Timeframe.D1, 13, T1, new BigDecimal("50000"));
        seedEmaRow(asset, Timeframe.D1, 13, T2, new BigDecimal("51000"));
        seedMacdRow(asset, Timeframe.D1, T1, new BigDecimal("10"));
        seedMacdRow(asset, Timeframe.D1, T2, new BigDecimal("20"));

        elderImpulseStateService.computeForAllActiveAssets(Timeframe.D1);
        long countAfterFirst = signalStateRepository.count();

        elderImpulseStateService.computeForAllActiveAssets(Timeframe.D1);
        long countAfterSecond = signalStateRepository.count();

        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }

    @Test
    void d1secondRunIsNoOp_whenNoNewDataArrived() {
        // The early-exit guard compares the latest stored state's closeTime against the latest
        // EMA row's closeTime.  When they match, there is no new candle — the second run must
        // be a complete no-op so that the flip event written by the first run is never
        // overwritten by a same-day re-trigger of the pipeline.
        Asset asset = seedAsset("BTCUSDT");
        seedEmaRow(asset, Timeframe.D1, 13, T1, new BigDecimal("50000"));
        seedEmaRow(asset, Timeframe.D1, 13, T2, new BigDecimal("51000"));
        seedMacdRow(asset, Timeframe.D1, T1, new BigDecimal("10"));
        seedMacdRow(asset, Timeframe.D1, T2, new BigDecimal("20"));

        // First run → IMPULSE_TURNED_GREEN stored at T2
        elderImpulseStateService.computeForAllActiveAssets(Timeframe.D1);
        List<SignalState> firstRunStates = getImpulseStates();
        assertThat(firstRunStates).hasSize(1);
        assertThat(firstRunStates.get(0).getEvent()).isEqualTo(SignalEvent.ELDER_IMPULSE_TURNED_GREEN);

        // Second run with identical EMA/MACD data — early-exit fires, nothing changes.
        // The flip event must be preserved; overwriting it with NONE would be wrong.
        elderImpulseStateService.computeForAllActiveAssets(Timeframe.D1);

        List<SignalState> states = getImpulseStates();
        assertThat(states).hasSize(1);
        assertThat(states.get(0).getTrendState()).isEqualTo(TrendState.ELDER_IMPULSE_GREEN);
        assertThat(states.get(0).getEvent()).isEqualTo(SignalEvent.ELDER_IMPULSE_TURNED_GREEN); // unchanged
    }

    // ---- W1 tests ----

    @Test
    void w1GreenState_whenSlopePositive() {
        Asset asset = seedAsset("BTCUSDT");
        seedEmaRow(asset, Timeframe.W1, 26, T1, new BigDecimal("50000"));
        seedEmaRow(asset, Timeframe.W1, 26, T2, new BigDecimal("51000"));

        elderImpulseStateService.computeForAllActiveAssets(Timeframe.W1);

        List<SignalState> states = getImpulseStates();
        assertThat(states).hasSize(1);
        assertThat(states.get(0).getTrendState()).isEqualTo(TrendState.ELDER_IMPULSE_GREEN);
        assertThat(states.get(0).getEvent()).isEqualTo(SignalEvent.ELDER_IMPULSE_TURNED_GREEN);
    }

    @Test
    void w1RedState_whenSlopeNegative() {
        Asset asset = seedAsset("BTCUSDT");
        seedEmaRow(asset, Timeframe.W1, 26, T1, new BigDecimal("51000"));
        seedEmaRow(asset, Timeframe.W1, 26, T2, new BigDecimal("50000"));

        elderImpulseStateService.computeForAllActiveAssets(Timeframe.W1);

        List<SignalState> states = getImpulseStates();
        assertThat(states).hasSize(1);
        assertThat(states.get(0).getTrendState()).isEqualTo(TrendState.ELDER_IMPULSE_RED);
        assertThat(states.get(0).getEvent()).isEqualTo(SignalEvent.ELDER_IMPULSE_TURNED_RED);
    }

    @Test
    void w1NeutralState_whenSlopeFlat() {
        Asset asset = seedAsset("BTCUSDT");
        seedEmaRow(asset, Timeframe.W1, 26, T1, new BigDecimal("50000"));
        seedEmaRow(asset, Timeframe.W1, 26, T2, new BigDecimal("50000"));

        elderImpulseStateService.computeForAllActiveAssets(Timeframe.W1);

        List<SignalState> states = getImpulseStates();
        assertThat(states).hasSize(1);
        assertThat(states.get(0).getTrendState()).isEqualTo(TrendState.ELDER_IMPULSE_NEUTRAL);
        assertThat(states.get(0).getEvent()).isEqualTo(SignalEvent.ELDER_IMPULSE_TURNED_NEUTRAL);
    }
}
