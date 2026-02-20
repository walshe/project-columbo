package walshe.projectcolumbo.marketdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Market data provider implementation for Binance.
 */
@Service
@EnableConfigurationProperties(BinanceProperties.class)
class BinanceMarketDataProvider implements MarketDataProvider {

    private static final Logger logger = LoggerFactory.getLogger(BinanceMarketDataProvider.class);
    private static final String KLINES_ENDPOINT = "/api/v3/klines?symbol={symbol}&interval=1d";

    private final RestClient restClient;

    BinanceMarketDataProvider(RestClient.Builder builder, BinanceProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public List<CandleDto> fetchDailyCandles(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        logger.info("Fetching daily candles from Binance for symbol: {} (normalized: {})", symbol, normalizedSymbol);

        Object[][] response = restClient.get()
                .uri(KLINES_ENDPOINT, normalizedSymbol)
                .retrieve()
                .body(Object[][].class);

        if (response == null) {
            logger.warn("Received empty response from Binance for symbol: {}", normalizedSymbol);
            return List.of();
        }

        return Arrays.stream(response)
                .map(this::mapToCandleDto)
                .filter(Objects::nonNull)
                .toList();
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.replace("/", "").replace("-", "").toUpperCase();
    }

    private CandleDto mapToCandleDto(Object[] row) {
        if (row.length < 6) {
            logger.warn("Unexpected row length from Binance: {}", row.length);
            return null;
        }

        /*
         * Binance Kline response format:
         * [
         *   [
         *     1499040000000,      // Open time
         *     "0.01634790",       // Open
         *     "0.80000000",       // High
         *     "0.01575800",       // Low
         *     "0.01577100",       // Close
         *     "148976.11427815",  // Volume
         *     1499644799999,      // Close time
         *     "2434.19055334",    // Quote asset volume
         *     308,                // Number of trades
         *     "1756.87402397",    // Taker buy base asset volume
         *     "28.46694368",      // Taker buy quote asset volume
         *     "17928899.62484339" // Ignore.
         *   ]
         * ]
         */
        try {
            long openTimeMs = ((Number) row[0]).longValue();
            BigDecimal open = new BigDecimal(row[1].toString());
            BigDecimal high = new BigDecimal(row[2].toString());
            BigDecimal low = new BigDecimal(row[3].toString());
            BigDecimal close = new BigDecimal(row[4].toString());
            BigDecimal volume = new BigDecimal(row[5].toString());
            long closeTimeMs = ((Number) row[6]).longValue();

            return new CandleDto(
                    open,
                    high,
                    low,
                    close,
                    volume,
                    Instant.ofEpochMilli(openTimeMs),
                    Instant.ofEpochMilli(closeTimeMs)
            );
        } catch (Exception e) {
            logger.error("Error parsing Binance candle data: {}", Arrays.toString(row), e);
            return null;
        }
    }
}
