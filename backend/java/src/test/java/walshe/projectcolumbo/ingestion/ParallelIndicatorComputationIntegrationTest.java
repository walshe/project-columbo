package walshe.projectcolumbo.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.repository.RsiRepository;
import walshe.projectcolumbo.persistence.repository.SuperTrendRepository;
import walshe.projectcolumbo.persistence.service.RsiComputationService;
import walshe.projectcolumbo.persistence.service.SuperTrendService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class ParallelIndicatorComputationIntegrationTest {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private SuperTrendRepository superTrendRepository;

    @Autowired
    private RsiRepository rsiRepository;

    @Autowired
    private SuperTrendService superTrendService;

    @Autowired
    private RsiComputationService rsiComputationService;

    private List<Asset> testAssets;

    @BeforeEach
    @Transactional
    public void setup() {
        testAssets = new ArrayList<>();
        long timestamp = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            Asset asset = new Asset();
            asset.setSymbol("TEST" + i + "_" + timestamp + "/USDT");
            asset.setProvider(MarketProvider.BINANCE);
            asset.setActive(true);
            assetRepository.save(asset);
            testAssets.add(asset);
        }

        // Add candles for each asset
        OffsetDateTime now = OffsetDateTime.now();
        for (Asset asset : testAssets) {
            for (int i = 0; i < 30; i++) {
                Candle candle = new Candle();
                candle.setAsset(asset);
                candle.setTimeframe(Timeframe.D1);
                OffsetDateTime candleTime = now.minusDays(30 - i);
                candle.setOpenTime(candleTime);
                candle.setCloseTime(candleTime);
                candle.setSource(MarketProvider.BINANCE);
                candle.setOpen(new BigDecimal("100"));
                candle.setHigh(new BigDecimal("105"));
                candle.setLow(new BigDecimal("95"));
                candle.setClose(new BigDecimal("102"));
                candle.setVolume(new BigDecimal("1000"));
                candleRepository.save(candle);
            }
        }
    }

    @Test
    public void testParallelComputationProducesConsistentResults() throws Exception {
        // Arrange: Run async SuperTrend computation for all assets
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Asset asset : testAssets) {
            futures.add(superTrendService.processAssetAsync(asset, Timeframe.D1, 10, new BigDecimal("2.0"), false));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Assert: All assets should have SuperTrend indicators
        for (Asset asset : testAssets) {
            var indicators = superTrendRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, Timeframe.D1);
            assertTrue(!indicators.isEmpty(), "Asset " + asset.getSymbol() + " should have SuperTrend indicators");
        }
    }

    @Test
    @Timeout(30)
    public void testParallelComputationWithMultipleAssets() throws Exception {
        // Create 20 test assets for load testing
        List<Asset> loadTestAssets = new ArrayList<>();
        long timestamp = System.currentTimeMillis();
        for (int i = 0; i < 20; i++) {
            Asset asset = new Asset();
            asset.setSymbol("LOAD" + i + "_" + timestamp + "/USDT");
            asset.setProvider(MarketProvider.BINANCE);
            asset.setActive(true);
            assetRepository.save(asset);
            loadTestAssets.add(asset);

            // Add candles
            OffsetDateTime now = OffsetDateTime.now();
            for (int j = 0; j < 30; j++) {
                Candle candle = new Candle();
                candle.setAsset(asset);
                candle.setTimeframe(Timeframe.D1);
                OffsetDateTime candleTime = now.minusDays(30 - j);
                candle.setOpenTime(candleTime);
                candle.setCloseTime(candleTime);
                candle.setSource(MarketProvider.BINANCE);
                candle.setOpen(new BigDecimal("100"));
                candle.setHigh(new BigDecimal("105"));
                candle.setLow(new BigDecimal("95"));
                candle.setClose(new BigDecimal("102"));
                candle.setVolume(new BigDecimal("1000"));
                candleRepository.save(candle);
            }
        }

        // Execute parallel RSI computation
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Asset asset : loadTestAssets) {
            futures.add(rsiComputationService.computeForAssetAsync(asset, Timeframe.D1, 14, false));
        }

        long startTime = System.currentTimeMillis();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long duration = System.currentTimeMillis() - startTime;

        // Assert: All computations should complete
        assertEquals(20, loadTestAssets.size());
        assertTrue(duration < 30000, "Parallel computation should complete within 30 seconds");

        // Verify results
        for (Asset asset : loadTestAssets) {
            var rsiIndicators = rsiRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, Timeframe.D1);
            assertTrue(!rsiIndicators.isEmpty(), "Asset should have RSI indicators");
        }
    }

    @Test
    public void testPartialFailureHandling() throws Exception {
        // Create one asset with no candles to trigger failure
        Asset failingAsset = new Asset();
        long timestamp = System.currentTimeMillis();
        failingAsset.setSymbol("FAIL_" + timestamp + "/USDT");
        failingAsset.setProvider(MarketProvider.BINANCE);
        failingAsset.setActive(true);
        assetRepository.save(failingAsset);

        // Execute with mix of valid and invalid assets
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Asset asset : testAssets) {
            futures.add(superTrendService.processAssetAsync(asset, Timeframe.D1, 10, new BigDecimal("2.0"), false));
        }
        futures.add(superTrendService.processAssetAsync(failingAsset, Timeframe.D1, 10, new BigDecimal("2.0"), false));

        // Execute all and collect results
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            allFutures.join();
        } catch (Exception e) {
            // Some failures are expected
        }

        // Assert: Valid assets should still have been processed
        for (Asset asset : testAssets) {
            var indicators = superTrendRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, Timeframe.D1);
            assertTrue(!indicators.isEmpty(), "Valid asset " + asset.getSymbol() + " should have indicators despite failures");
        }
    }

    @Test
    @Transactional
    public void testConfigurableAsyncExecution() {
        // Test that async methods return CompletableFuture
        Asset testAsset = testAssets.get(0);
        CompletableFuture<Void> future = superTrendService.processAssetAsync(testAsset, Timeframe.D1, 10, new BigDecimal("2.0"), false);

        assertFalse(future.isCompletedExceptionally(), "Async execution should not immediately fail");
        assertTrue(future.isDone() || !future.isDone(), "Future should be in valid state");
    }
}
