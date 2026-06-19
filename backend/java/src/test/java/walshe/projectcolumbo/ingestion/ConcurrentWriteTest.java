package walshe.projectcolumbo.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.SuperTrendIndicator;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.SuperTrendDirection;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.SuperTrendRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
public class ConcurrentWriteTest {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private SuperTrendRepository superTrendRepository;

    @Test
    @Transactional
    public void testConcurrentIndicatorWrites() throws Exception {
        // Setup: Create a test asset
        Asset testAsset = new Asset();
        testAsset.setSymbol("TEST/USDT");
        testAsset.setActive(true);
        assetRepository.save(testAsset);

        // Create an initial indicator
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

        // Execute concurrent updates with retry logic for optimistic lock failures
        ExecutorService executor = Executors.newFixedThreadPool(5);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        CompletableFuture<?>[] futures = new CompletableFuture[10];

        for (int i = 0; i < 10; i++) {
            final int threadIndex = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    SuperTrendIndicator retrieved = superTrendRepository.findById(indicator.getId()).orElseThrow();
                    retrieved.setAtr(new BigDecimal(100 + threadIndex));
                    superTrendRepository.save(retrieved);
                    successCount.incrementAndGet();
                } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                    // Expected: only one thread succeeds, others fail with optimistic lock error
                }
            }, executor);
        }

        // Wait for all futures to complete
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify: At least one thread should have successfully updated the record
        assertTrue(successCount.get() >= 1, "At least one concurrent update should succeed");

        // Verify: The final state should be one of the updated values
        SuperTrendIndicator finalState = superTrendRepository.findById(indicator.getId()).orElseThrow();
        assertEquals(Timeframe.D1, finalState.getTimeframe());
        // One of the threads should have written a value between 100 and 109
        BigDecimal finalAtr = finalState.getAtr();
        boolean validValue = finalAtr.compareTo(new BigDecimal("100")) >= 0 && finalAtr.compareTo(new BigDecimal("109")) <= 0;
        assertTrue(validValue, "Final ATR value should be between 100 and 109, but was " + finalAtr);
    }
}
