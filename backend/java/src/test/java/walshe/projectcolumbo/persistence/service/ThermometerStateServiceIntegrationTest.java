package walshe.projectcolumbo.persistence.service;

import walshe.projectcolumbo.TestDatabaseCleaner;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.SignalState;
import walshe.projectcolumbo.persistence.entity.ThermometerIndicator;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.SignalStateRepository;
import walshe.projectcolumbo.persistence.repository.ThermometerRepository;

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
class ThermometerStateServiceIntegrationTest {

    @Autowired
    private TestDatabaseCleaner testDatabaseCleaner;

    @Autowired
    private ThermometerStateService thermometerStateService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private ThermometerRepository thermometerRepository;

    @Autowired
    private SignalStateRepository signalStateRepository;

    private static final OffsetDateTime T1 = OffsetDateTime.of(2024, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        testDatabaseCleaner.cleanAll();
    }

    @AfterEach
    void tearDown() {
        signalStateRepository.deleteAll();
        thermometerRepository.deleteAll();
        assetRepository.deleteAll();
    }

    // ---- helpers ----

    private Asset seedAsset(String symbol) {
        return assetRepository.save(new Asset(symbol, symbol, MarketProvider.BINANCE, true));
    }

    private void seedThermometerRow(Asset asset, BigDecimal temperature, BigDecimal ema) {
        ThermometerIndicator row = new ThermometerIndicator();
        row.setAsset(asset);
        row.setCloseTime(T1);
        row.setTemperature(temperature);
        row.setTemperatureEma(ema);
        thermometerRepository.save(row);
    }

    private List<SignalState> getThermometerStates() {
        return signalStateRepository.findAll().stream()
                .filter(s -> s.getIndicatorType() == IndicatorType.ELDER_THERMOMETER)
                .toList();
    }

    // ---- state derivation tests ----

    @Test
    void shouldDeriveQUIET_whenTempBelowEma() {
        // temp = 1000, ema = 2000 → QUIET (temp ≤ ema)
        Asset asset = seedAsset("BTCUSDT");
        seedThermometerRow(asset, new BigDecimal("1000"), new BigDecimal("2000"));

        thermometerStateService.computeForAllActiveAssets();

        List<SignalState> states = getThermometerStates();
        assertThat(states).hasSize(1);
        assertThat(states.get(0).getTrendState()).isEqualTo(TrendState.ELDER_THERMOMETER_QUIET);
    }

    @Test
    void shouldDeriveHOT_whenTempAboveEma_butBelowTriple() {
        // temp = 3000, ema = 2000, 3*ema = 6000 → HOT (temp > ema AND temp ≤ 3*ema)
        Asset asset = seedAsset("BTCUSDT");
        seedThermometerRow(asset, new BigDecimal("3000"), new BigDecimal("2000"));

        thermometerStateService.computeForAllActiveAssets();

        List<SignalState> states = getThermometerStates();
        assertThat(states).hasSize(1);
        assertThat(states.get(0).getTrendState()).isEqualTo(TrendState.ELDER_THERMOMETER_HOT);
    }

    @Test
    void shouldDeriveSPIKE_whenTempAboveTripleEma() {
        // temp = 7000, ema = 2000, 3*ema = 6000 → SPIKE (temp > 3*ema)
        Asset asset = seedAsset("BTCUSDT");
        seedThermometerRow(asset, new BigDecimal("7000"), new BigDecimal("2000"));

        thermometerStateService.computeForAllActiveAssets();

        List<SignalState> states = getThermometerStates();
        assertThat(states).hasSize(1);
        assertThat(states.get(0).getTrendState()).isEqualTo(TrendState.ELDER_THERMOMETER_SPIKE);
        assertThat(states.get(0).getEvent()).isEqualTo(SignalEvent.ELDER_THERMOMETER_TRIPLE_SPIKE);
    }

    @Test
    void shouldDeriveSPIKE_notHOT_whenTempExactlyAt3xEmaPlus1() {
        // Verifies SPIKE check takes priority over HOT check
        // temp = 6001, ema = 2000, 3*ema = 6000 → temp > 3*ema → SPIKE not HOT
        Asset asset = seedAsset("BTCUSDT");
        seedThermometerRow(asset, new BigDecimal("6001"), new BigDecimal("2000"));

        thermometerStateService.computeForAllActiveAssets();

        List<SignalState> states = getThermometerStates();
        assertThat(states).hasSize(1);
        assertThat(states.get(0).getTrendState()).isEqualTo(TrendState.ELDER_THERMOMETER_SPIKE);
    }

    @Test
    void shouldSkip_whenTemperatureEmaIsNull() {
        // EMA null → insufficient history → no signal_state row written
        Asset asset = seedAsset("BTCUSDT");
        seedThermometerRow(asset, new BigDecimal("1000"), null);

        thermometerStateService.computeForAllActiveAssets();

        List<SignalState> states = getThermometerStates();
        assertThat(states).isEmpty();
    }

    @Test
    void shouldSkip_whenNoThermometerRowExists() {
        Asset asset = seedAsset("BTCUSDT");
        // No ThermometerIndicator seeded

        thermometerStateService.computeForAllActiveAssets();

        assertThat(getThermometerStates()).isEmpty();
    }

    @Test
    void shouldBeIdempotent() {
        Asset asset = seedAsset("BTCUSDT");
        seedThermometerRow(asset, new BigDecimal("1000"), new BigDecimal("2000"));

        thermometerStateService.computeForAllActiveAssets();
        long countAfterFirst = signalStateRepository.count();

        thermometerStateService.computeForAllActiveAssets();
        long countAfterSecond = signalStateRepository.count();

        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }

    @Test
    void shouldEmitCrossedAboveEma_whenTransitioningQuietToHot() {
        Asset asset = seedAsset("BTCUSDT");

        // Seed a previous QUIET signal_state to simulate prior state
        signalStateRepository.save(new SignalState(
                asset, Timeframe.D1, IndicatorType.ELDER_THERMOMETER,
                T1.minusDays(1),
                TrendState.ELDER_THERMOMETER_QUIET, SignalEvent.NONE));

        // Now seed today's thermometer with HOT condition (temp=3000, ema=2000)
        seedThermometerRow(asset, new BigDecimal("3000"), new BigDecimal("2000"));

        thermometerStateService.computeForAllActiveAssets();

        // Filter for the new signal at T1 (not the seeded prior state at T1.minusDays(1))
        List<SignalState> states = getThermometerStates().stream()
                .filter(s -> s.getCloseTime().equals(T1))
                .toList();
        assertThat(states).hasSize(1);
        assertThat(states.get(0).getTrendState()).isEqualTo(TrendState.ELDER_THERMOMETER_HOT);
        assertThat(states.get(0).getEvent()).isEqualTo(SignalEvent.ELDER_THERMOMETER_CROSSED_ABOVE_EMA);
    }

    @Test
    void shouldEmitCrossedBelowEma_whenTransitioningHotToQuiet() {
        Asset asset = seedAsset("BTCUSDT");

        // Seed a previous HOT signal_state
        signalStateRepository.save(new SignalState(
                asset, Timeframe.D1, IndicatorType.ELDER_THERMOMETER,
                T1.minusDays(1),
                TrendState.ELDER_THERMOMETER_HOT, SignalEvent.NONE));

        // Now seed today's thermometer with QUIET condition
        seedThermometerRow(asset, new BigDecimal("1000"), new BigDecimal("2000"));

        thermometerStateService.computeForAllActiveAssets();

        List<SignalState> states = getThermometerStates().stream()
                .filter(s -> s.getCloseTime().equals(T1))
                .toList();
        assertThat(states).hasSize(1);
        assertThat(states.get(0).getTrendState()).isEqualTo(TrendState.ELDER_THERMOMETER_QUIET);
        assertThat(states.get(0).getEvent()).isEqualTo(SignalEvent.ELDER_THERMOMETER_CROSSED_BELOW_EMA);
    }
}
