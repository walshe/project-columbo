package walshe.projectcolumbo.supertrend.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.shared.AssetVenue;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Binance's public klines API — the only market data provider actually active in production.
 * Spot and futures are separate Binance products with separate hosts <em>and</em> separate klines
 * paths, not just a configurable base URL, so one instance is scoped to exactly one {@link
 * AssetVenue} for its whole lifetime.
 */
public final class BinanceMarketDataProvider implements MarketDataProvider {

    private static final Logger LOG = LoggerFactory.getLogger(BinanceMarketDataProvider.class);
    private static final String SPOT_DEFAULT_BASE_URL = "https://api.binance.com";
    private static final String SPOT_KLINES_PATH = "/api/v3/klines";
    private static final String FUTURES_DEFAULT_BASE_URL = "https://fapi.binance.com";
    private static final String FUTURES_KLINES_PATH = "/fapi/v1/klines";
    private static final int BINANCE_INVALID_SYMBOL_CODE = -1121;

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String klinesPath;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BinanceMarketDataProvider(HttpClient httpClient, AssetVenue venue) {
        this(httpClient, venue, defaultBaseUrlFor(venue));
    }

    /** @param baseUrl overridable so tests can point this venue at a stub server instead of the real Binance API. */
    public BinanceMarketDataProvider(HttpClient httpClient, AssetVenue venue, String baseUrl) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.klinesPath = klinesPathFor(Objects.requireNonNull(venue, "venue must not be null"));
        this.baseUrl = stripTrailingSlashes(Objects.requireNonNull(baseUrl, "baseUrl must not be null"));
    }

    private static String defaultBaseUrlFor(AssetVenue venue) {
        return switch (venue) {
            case SPOT -> SPOT_DEFAULT_BASE_URL;
            case FUTURES -> FUTURES_DEFAULT_BASE_URL;
            case EXCHANGE, MEXC -> throw unsupportedVenue(venue);
        };
    }

    private static String klinesPathFor(AssetVenue venue) {
        return switch (venue) {
            case SPOT -> SPOT_KLINES_PATH;
            case FUTURES -> FUTURES_KLINES_PATH;
            case EXCHANGE, MEXC -> throw unsupportedVenue(venue);
        };
    }

    private static IllegalArgumentException unsupportedVenue(AssetVenue venue) {
        return new IllegalArgumentException("BinanceMarketDataProvider does not support venue " + venue);
    }

    /** A trailing slash on the configured base URL would otherwise double up with {@link #klinesPath}'s leading one. */
    private static String stripTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    @Override
    public List<Candle> fetchDailyCandles(String symbol, long startTimeMs, long endTimeMs) {
        String normalizedSymbol = normalizeSymbol(symbol);
        URI uri = klinesUri(normalizedSymbol, startTimeMs, endTimeMs);

        LOG.info("Fetching daily candles from Binance: symbol={} (normalized={}), start={}, end={}",
                symbol, normalizedSymbol, startTimeMs, endTimeMs);

        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new MarketDataProviderException("Failed to reach Binance for symbol " + normalizedSymbol, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MarketDataProviderException("Interrupted while reaching Binance for symbol " + normalizedSymbol, e);
        }

        if (response.statusCode() != 200) {
            handleErrorResponse(normalizedSymbol, response);
        }

        return parseKlines(response.body());
    }

    URI klinesUri(String normalizedSymbol, long startTimeMs, long endTimeMs) {
        return URI.create(baseUrl + klinesPath
                + "?symbol=" + normalizedSymbol
                + "&interval=1d"
                + "&startTime=" + startTimeMs
                + "&endTime=" + endTimeMs);
    }

    void handleErrorResponse(String normalizedSymbol, HttpResponse<String> response) {
        if (isInvalidSymbolResponse(response.body())) {
            throw new InvalidSymbolException(normalizedSymbol);
        }
        throw new MarketDataProviderException("Binance returned HTTP " + response.statusCode() + " for symbol "
                + normalizedSymbol + ": " + response.body());
    }

    boolean isInvalidSymbolResponse(String responseBody) {
        JsonNode body = readTreeOrNull(responseBody);
        return body != null && body.path("code").asInt() == BINANCE_INVALID_SYMBOL_CODE;
    }

    List<Candle> parseKlines(String responseBody) {
        JsonNode rows;
        try {
            rows = objectMapper.readTree(responseBody);
        } catch (IOException e) {
            throw new MarketDataProviderException("Failed to parse Binance klines response", e);
        }

        if (!rows.isArray()) {
            throw new MarketDataProviderException("Binance klines response was not a JSON array: " + responseBody);
        }

        List<Candle> candles = new ArrayList<>(rows.size());
        for (JsonNode row : rows) {
            candles.add(toCandle(row));
        }
        return candles;
    }

    /**
     * Binance kline array indices: [0] open time, [1] open, [2] high, [3] low, [4] close,
     * [5] base-asset volume, [6] close time, [7] quote-asset volume, ...
     * <p>
     * Volume is read from index 7 (quote-asset, i.e. USDT-denominated) rather than index 5
     * (base-asset, e.g. BTC) so average-volume liquidity comparisons are meaningful across
     * assets with wildly different unit prices — matches {@code backend/java}'s behavior.
     */
    Candle toCandle(JsonNode row) {
        Instant openTime = Instant.ofEpochMilli(row.get(0).asLong());
        Instant closeTime = Instant.ofEpochMilli(row.get(6).asLong());
        return new Candle(
                openTime.atOffset(ZoneOffset.UTC),
                closeTime.atOffset(ZoneOffset.UTC),
                Timeframe.D1,
                new BigDecimal(row.get(1).asText()),
                new BigDecimal(row.get(2).asText()),
                new BigDecimal(row.get(3).asText()),
                new BigDecimal(row.get(4).asText()),
                new BigDecimal(row.get(7).asText())
        );
    }

    static String normalizeSymbol(String symbol) {
        String normalized = symbol.replace("/", "").replace("-", "").toUpperCase();
        return normalized.endsWith("USDT") ? normalized : normalized + "USDT";
    }

    private JsonNode readTreeOrNull(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            return null;
        }
    }
}
