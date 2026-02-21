package walshe.projectcolumbo.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.marketdata.CandleDto;
import walshe.projectcolumbo.persistence.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CandleIngestionIntegrationTest {

    @Autowired
    private CandleIngestionService candleIngestionService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private SuperTrendRepository superTrendRepository;

    @Autowired
    private SignalStateRepository signalStateRepository;

    @BeforeEach
    void setUp() {
        signalStateRepository.deleteAll();
        superTrendRepository.deleteAll();
        candleRepository.deleteAll();
        assetRepository.deleteAll();
    }

    @Test
    void shouldVerifyDataPersistenceWithEnumMapping() {
        // This test ensures that the ENUM and JSONB mapping work with PostgreSQL
        // using the actual repositories and Testcontainers.
        
        // 1. Create and Save Asset
        Asset btc = new Asset("BTC", "Bitcoin", MarketProvider.BINANCE, true);
        assetRepository.save(btc);
        
        // 2. Create and Save Candle
        Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Candle candle = new Candle();
        candle.setAsset(btc);
        candle.setTimeframe(Timeframe.D1);
        candle.setOpenTime(now.atOffset(java.time.ZoneOffset.UTC));
        candle.setCloseTime(now.plus(1, ChronoUnit.DAYS).atOffset(java.time.ZoneOffset.UTC));
        candle.setOpen(new java.math.BigDecimal("50000"));
        candle.setHigh(new java.math.BigDecimal("51000"));
        candle.setLow(new java.math.BigDecimal("49000"));
        candle.setClose(new java.math.BigDecimal("50500"));
        candle.setVolume(new java.math.BigDecimal("100"));
        candle.setSource(MarketProvider.BINANCE);
        candle.setRawPayload("{\"test\": true}");
        
        candleRepository.save(candle);
        
        // 3. Verify retrieval works without ENUM errors
        List<Candle> retrieved = candleRepository.findByAssetAndTimeframe(btc, Timeframe.D1);
        assertThat(retrieved).hasSize(1);
        assertThat(retrieved.get(0).getTimeframe()).isEqualTo(Timeframe.D1);
        assertThat(retrieved.get(0).getSource()).isEqualTo(MarketProvider.BINANCE);
        assertThat(retrieved.get(0).getRawPayload()).contains("test");
    }
}
