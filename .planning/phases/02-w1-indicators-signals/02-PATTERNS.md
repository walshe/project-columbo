# Phase 2: W1 Indicators & Signals - Pattern Map

**Mapped:** 2026-05-21
**Files analyzed:** 2 (1 modified, 1 new)
**Analogs found:** 2 / 2

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/MarketPulseService.java` | service | CRUD | `backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineService.java` | exact (same service-orchestrator pattern with phase logging) |
| `backend/java/src/test/java/walshe/projectcolumbo/persistence/service/W1IndicatorPipelineIntegrationTest.java` | test | request-response | `backend/java/src/test/java/walshe/projectcolumbo/ingestion/MarketPipelineIntegrationTest.java` + `backend/java/src/test/java/walshe/projectcolumbo/rollup/CandleRollupIntegrationTest.java` | exact |

---

## Pattern Assignments

### `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/MarketPulseService.java` (service, CRUD — MODIFY)

**Change:** Add `computeForTimeframe(Timeframe)` public method. Refactor existing `computeDaily()` to delegate to it. No other logic changes.

**Analog:** `backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineService.java` (phase-by-phase delegation) and the existing file itself.

**Existing class declaration pattern** (lines 23–37 of `MarketPulseService.java`):
```java
@Service
public class MarketPulseService {
    private static final Logger log = LoggerFactory.getLogger(MarketPulseService.class);

    private final AssetRepository assetRepository;
    private final SignalStateRepository signalStateRepository;
    private final MarketBreadthSnapshotRepository snapshotRepository;

    public MarketPulseService(AssetRepository assetRepository,
                              SignalStateRepository signalStateRepository,
                              MarketBreadthSnapshotRepository snapshotRepository) {
        this.assetRepository = assetRepository;
        this.signalStateRepository = signalStateRepository;
        this.snapshotRepository = snapshotRepository;
    }
```
No constructor changes required.

**Existing `computeDaily()` method to refactor** (lines 39–46 of `MarketPulseService.java`):
```java
@Transactional
public void computeDaily() {
    log.info("Starting MarketPulse aggregation (Breadth Snapshots)");
    Timeframe timeframe = Timeframe.D1;
    for (IndicatorType type : IndicatorType.values()) {
        computePulseForIndicator(timeframe, type);
    }
}
```

**Target state after refactor — new `computeForTimeframe` + updated `computeDaily`:**
```java
@Transactional
public void computeForTimeframe(Timeframe timeframe) {
    log.info("Starting MarketPulse aggregation for timeframe {}", timeframe);
    for (IndicatorType type : IndicatorType.values()) {
        computePulseForIndicator(timeframe, type);
    }
}

@Transactional
public void computeDaily() {
    computeForTimeframe(Timeframe.D1);
}
```

**Phase-logging pattern to copy from** (lines 84–102 of `MarketPipelineService.java`):
```java
logger.info("Starting phase: INDICATOR");
long indicatorStartTime = System.currentTimeMillis();
superTrendService.processAllActiveAssets(actualTimeframe, 10, new BigDecimal("2.0"), false);
rsiComputationService.computeForActiveAssets(actualTimeframe, 14, false);
logger.info("Completed phase: INDICATOR in {}ms", System.currentTimeMillis() - indicatorStartTime);
```
Mirror this log-before / log-after-with-duration pattern inside `computeForTimeframe`.

**Private helper is already timeframe-agnostic** (lines 48–49 of `MarketPulseService.java`):
```java
private void computePulseForIndicator(Timeframe timeframe, IndicatorType indicatorType) {
    log.debug("Computing MarketPulse for indicator: {}", indicatorType);
```
No changes needed to `computePulseForIndicator`, `upsertSnapshot`, or `isSame`.

---

### `backend/java/src/test/java/walshe/projectcolumbo/persistence/service/W1IndicatorPipelineIntegrationTest.java` (test, request-response — NEW)

**Analog 1 (class-level boilerplate):** `backend/java/src/test/java/walshe/projectcolumbo/ingestion/MarketPipelineIntegrationTest.java`
**Analog 2 (fixture seeding + assertions):** `backend/java/src/test/java/walshe/projectcolumbo/rollup/CandleRollupIntegrationTest.java`
**Analog 3 (indicator assertions):** `backend/java/src/test/java/walshe/projectcolumbo/persistence/service/SuperTrendServiceTest.java`

**Package declaration:**
```java
package walshe.projectcolumbo.persistence.service;
```
(Matches the existing `SuperTrendServiceTest` in the same directory.)

**Class-level annotations** (lines 36–38 of `MarketPipelineIntegrationTest.java`):
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class W1IndicatorPipelineIntegrationTest {
```

**Imports block** (synthesized from both analogs):
```java
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.marketpulse.MarketPulseService;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.repository.MarketBreadthSnapshotRepository;
import walshe.projectcolumbo.persistence.repository.RsiRepository;
import walshe.projectcolumbo.persistence.repository.SignalStateRepository;
import walshe.projectcolumbo.persistence.repository.SuperTrendRepository;
import walshe.projectcolumbo.persistence.service.RsiComputationService;
import walshe.projectcolumbo.persistence.service.SignalStateService;
import walshe.projectcolumbo.persistence.service.SuperTrendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.assertThat;
```

**`@Autowired` fields pattern** (from `MarketPipelineIntegrationTest.java` lines 41–62, adapted):
```java
@Autowired private SuperTrendService superTrendService;
@Autowired private RsiComputationService rsiComputationService;
@Autowired private SignalStateService signalStateService;
@Autowired private MarketPulseService marketPulseService;

@Autowired private AssetRepository assetRepository;
@Autowired private CandleRepository candleRepository;
@Autowired private SuperTrendRepository superTrendRepository;
@Autowired private RsiRepository rsiRepository;
@Autowired private SignalStateRepository signalStateRepository;
@Autowired private MarketBreadthSnapshotRepository marketBreadthSnapshotRepository;
```
No `@MockitoBean` required — Phase 2 does not touch ingestion or the Binance provider.

**`@BeforeEach` teardown pattern** (lines 67–77 of `MarketPipelineIntegrationTest.java`, adapted for W1 scope):
```java
@BeforeEach
void setUp() {
    marketBreadthSnapshotRepository.deleteAll();
    signalStateRepository.deleteAll();
    superTrendRepository.deleteAll();
    rsiRepository.deleteAll();
    candleRepository.deleteAll();
    assetRepository.deleteAll();
}
```
Delete in FK-safe order: snapshots → signal states → indicators → candles → assets.

**W1 candle fixture pattern** (from `CandleRollupIntegrationTest.java` lines 49–67, adapted for W1):
```java
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
```
Seed **15 candles** minimum (RSI-14 requires `period + 1 = 15`). Use past Sundays (2024-01-07 onward) so `CandleFilters.finalizedBeforeUtcMidnightToday` passes them through.

**INDC-01 test method pattern** (from `SuperTrendServiceTest.java` lines 71–92, adapted for W1):
```java
@Test
void supertrend_W1_isComputed() {
    Asset btc = seedAssetWithW1Candles("BTCUSDT", 15);

    superTrendService.processAllActiveAssets(Timeframe.W1, 10, new BigDecimal("2.0"), false);

    // 15 candles, atrLength=10 → 15 - 10 + 1 = 6 SuperTrend rows
    assertThat(superTrendRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(btc, Timeframe.W1))
            .hasSize(6);
}
```

**INDC-02 test method pattern:**
```java
@Test
void rsi_W1_isComputed() {
    Asset btc = seedAssetWithW1Candles("BTCUSDT", 15);

    rsiComputationService.computeForActiveAssets(Timeframe.W1, 14, false);

    // RSI-14 requires 15 candles for first value; with 15 candles expect 1 result
    assertThat(rsiRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(btc, Timeframe.W1))
            .isNotEmpty();
}
```

**INDC-03 idempotency test pattern** (from `SuperTrendServiceTest.java` lines 96–112, adapted):
```java
@Test
void indicators_W1_areIdempotent() {
    Asset btc = seedAssetWithW1Candles("BTCUSDT", 15);

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
```

**SGNL-01 test method pattern** (from `MarketPipelineIntegrationTest.java` lines 106–114, adapted):
```java
@Test
void signalState_W1_isDetected() {
    seedAssetWithW1Candles("BTCUSDT", 15);

    superTrendService.processAllActiveAssets(Timeframe.W1, 10, new BigDecimal("2.0"), false);
    rsiComputationService.computeForActiveAssets(Timeframe.W1, 14, false);
    signalStateService.detectDaily();  // iterates Timeframe.values() — covers W1 automatically

    assertThat(signalStateRepository.findAll().stream()
            .filter(s -> s.getTimeframe() == Timeframe.W1))
            .isNotEmpty();
}
```

**SGNL-02 test method pattern:**
```java
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
```

---

## Shared Patterns

### Logger Declaration
**Source:** `MarketPulseService.java` line 25 / `MarketPipelineService.java` line 7
**Apply to:** `MarketPulseService.java` (no change needed — already present)
```java
private static final Logger log = LoggerFactory.getLogger(MarketPulseService.class);
```

### `@Transactional` on public service methods
**Source:** `MarketPulseService.java` lines 39 and (after refactor) both public methods
**Apply to:** Both `computeForTimeframe(Timeframe)` and `computeDaily()` must carry `@Transactional`
```java
@Transactional
public void computeForTimeframe(Timeframe timeframe) { ... }

@Transactional
public void computeDaily() {
    computeForTimeframe(Timeframe.D1);
}
```

### `deleteAllInBatch()` vs `deleteAll()` in `@BeforeEach`
**Source:** `SuperTrendServiceTest.java` lines 60–64 uses `deleteAllInBatch()` (faster, bypasses Hibernate cascade).
`MarketPipelineIntegrationTest.java` lines 69–75 uses `deleteAll()` (safer with FK constraints).
**Apply to:** `W1IndicatorPipelineIntegrationTest` — use `deleteAll()` in FK-safe order (matches `MarketPipelineIntegrationTest` which also has multiple table types). Use `deleteAllInBatch()` only if the FK order is confirmed safe.

### AssertJ fluent assertion style
**Source:** `CandleRollupIntegrationTest.java` (all assertions), `SuperTrendServiceTest.java`
**Apply to:** All test methods in `W1IndicatorPipelineIntegrationTest`
```java
assertThat(collection).hasSize(n);
assertThat(collection).isNotEmpty();
assertThat(value).isEqualByComparingTo("string");
```
Do not use JUnit `assertEquals` / `assertTrue` (those appear only in `MarketPipelineIntegrationTest` which mixes styles — prefer the AssertJ style used in rollup and SuperTrend tests).

### Service call ordering (strict serial dependency)
**Source:** `MarketPipelineService.java` lines 84–102 (INDICATOR → SIGNAL → MARKET_PULSE)
**Apply to:** Every test method that advances beyond indicators must respect:
```
superTrendService.processAllActiveAssets(W1, ...) 
    → rsiComputationService.computeForActiveAssets(W1, ...)
        → signalStateService.detectDaily()
            → marketPulseService.computeForTimeframe(W1)
```

---

## No Analog Found

No files in Phase 2 are fully without analog. All patterns are grounded in existing production and test code.

| File | Role | Note |
|------|------|-------|
| — | — | All Phase 2 files have direct analogs in the codebase |

---

## Metadata

**Analog search scope:** `backend/java/src/main/java/walshe/projectcolumbo/` and `backend/java/src/test/java/walshe/projectcolumbo/`
**Files read:** `MarketPulseService.java`, `MarketPipelineService.java`, `MarketPipelineIntegrationTest.java`, `CandleRollupIntegrationTest.java`, `SuperTrendServiceTest.java`, `MarketPulseServiceTest.java`
**Pattern extraction date:** 2026-05-21
