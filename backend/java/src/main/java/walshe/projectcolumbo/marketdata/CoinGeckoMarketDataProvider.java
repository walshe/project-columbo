package walshe.projectcolumbo.marketdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Market data provider implementation for CoinGecko.
 */
@Service
@EnableConfigurationProperties(CoinGeckoProperties.class)
class CoinGeckoMarketDataProvider implements MarketDataProvider {

    private static final Logger logger = LoggerFactory.getLogger(CoinGeckoMarketDataProvider.class);
    private static final String OHLC_ENDPOINT = "/coins/{id}/ohlc?vs_currency=usd&days=365";
    private static final String API_KEY_HEADER = "x-cg-demo-api-key";

    private final RestClient restClient;

    CoinGeckoMarketDataProvider(RestClient.Builder builder, CoinGeckoProperties properties) {
        RestClient.Builder customBuilder = builder.baseUrl(properties.baseUrl());
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            customBuilder.defaultHeader(API_KEY_HEADER, properties.apiKey());
        }
        this.restClient = customBuilder.build();
    }

    @Override
    public List<CandleDto> fetchDailyCandles(String providerId, Long startTime, Long endTime) {
        logger.info("Fetching daily candles for asset: {} (startTime={}, endTime={})", providerId, startTime, endTime);

        try {
            // Apply a mild delay to respect rate limits (as per plan.md)
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Thread interrupted during delay before API call", e);
        }

        BigDecimal[][] response = restClient.get()
                .uri(OHLC_ENDPOINT, providerId)
                .retrieve()
                .body(BigDecimal[][].class);

        if (response == null) {
            logger.warn("Received empty response from CoinGecko for providerId: {}", providerId);
            return List.of();
        }

        return Arrays.stream(response)
                .map(this::mapToCandleDto)
                .filter(Objects::nonNull)
                .toList();
    }

    private CandleDto mapToCandleDto(BigDecimal[] row) {
        if (row.length < 5) {
            logger.warn("Unexpected row length from CoinGecko: {}", row.length);
            return null;
        }

        // Response format: [timestamp_ms, open, high, low, close]
        long timestampMs = row[0].longValue();
        BigDecimal open = row[1];
        BigDecimal high = row[2];
        BigDecimal low = row[3];
        BigDecimal close = row[4];
        BigDecimal volume = null;

        Instant closeTime = Instant.ofEpochMilli(timestampMs);
        Instant openTime = closeTime.minus(1, ChronoUnit.DAYS);

        return new CandleDto(open, high, low, close, volume, openTime, closeTime);
    }
}
