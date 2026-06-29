package walshe.projectcolumbo.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.TestDatabaseCleaner;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.SuperTrendIndicator;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.SuperTrendDirection;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.SuperTrendRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public class ConcurrentWriteTest {

    @Autowired
    private TestDatabaseCleaner testDatabaseCleaner;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private SuperTrendRepository superTrendRepository;

    private Long indicatorId;

    @BeforeEach
    @Transactional
    public void setUp() {
        testDatabaseCleaner.cleanAll();

        Asset testAsset = new Asset();
        testAsset.setSymbol("TEST/USDT");
        testAsset.setProvider(MarketProvider.BINANCE);
        testAsset.setActive(true);
        assetRepository.save(testAsset);

        SuperTrendIndicator indicator = new SuperTrendIndicator();
        indicator.setAsset(testAsset);
        indicator.setTimeframe(Timeframe.D1);
        indicator.setCloseTime(OffsetDateTime.now());
        indicator.setAtr(new BigDecimal("10.0"));
        indicator.setUpperBand(new BigDecimal("100.0"));
        indicator.setLowerBand(new BigDecimal("90.0"));
        indicator.setSupertrend(new BigDecimal("95.0"));
        indicator.setDirection(SuperTrendDirection.SUPERTREND_UP);
        superTrendRepository.save(indicator);
        this.indicatorId = indicator.getId();
    }

    @Test
    public void testConcurrentIndicatorWrites() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        AtomicInteger successCount = new AtomicInteger(0);
        CompletableFuture<?>[] futures = new CompletableFuture[10];

        for (int i = 0; i < 10; i++) {
            final int threadIndex = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    SuperTrendIndicator retrieved = superTrendRepository.findById(indicatorId).orElseThrow();
                    retrieved.setAtr(new BigDecimal(100 + threadIndex));
                    superTrendRepository.save(retrieved);
                    successCount.incrementAndGet();
                } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                    // Expected: optimistic lock conflict when threads write concurrently
                }
            }, executor);
        }

        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(successCount.get() >= 1, "At least one concurrent update should succeed");

        var finalIndicator = superTrendRepository.findById(indicatorId);
        assertTrue(finalIndicator.isPresent(), "Indicator should exist after concurrent writes");

        SuperTrendIndicator finalState = finalIndicator.orElseThrow();
        assertEquals(Timeframe.D1, finalState.getTimeframe());
        BigDecimal finalAtr = finalState.getAtr();
        boolean validValue = finalAtr.compareTo(new BigDecimal("100")) >= 0 && finalAtr.compareTo(new BigDecimal("109")) <= 0;
        assertTrue(validValue, "Final ATR value should be between 100 and 109, but was " + finalAtr);
    }
}
