package walshe.projectcolumbo.supertrend.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import walshe.projectcolumbo.supertrend.indicator.Candle;
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
 * MEXC's public klines API - unauthenticated, and (confirmed live against the real API) byte-
 * identical in response shape to Binance's spot klines: an 8-field row array (open time, O/H/L/C,
 * base volume, close time, quote volume). Unlike Binance, MEXC has exactly one spot market
 * covering both crypto and tokenized real-equity pairs, so there's no venue-based host/path
 * switch here, and no symbol normalization - {@code asset.symbol()} is already the exact MEXC
 * ticker (e.g. {@code "AAPLONUSDT"}) at seed time, not a bare base asset that needs a suffix
 * appended.
 */
public final class MexcMarketDataProvider implements MarketDataProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MexcMarketDataProvider.class);
    private static final String DEFAULT_BASE_URL = "https://api.mexc.com";
    private static final String KLINES_PATH = "/api/v3/klines";
    private static final int MEXC_INVALID_SYMBOL_CODE = -1121;

    private final HttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MexcMarketDataProvider(HttpClient httpClient) {
        this(httpClient, DEFAULT_BASE_URL);
    }

    /** @param baseUrl overridable so tests can point this provider at a stub server instead of the real MEXC API. */
    public MexcMarketDataProvider(HttpClient httpClient, String baseUrl) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.baseUrl = stripTrailingSlashes(Objects.requireNonNull(baseUrl, "baseUrl must not be null"));
    }

    /** A trailing slash on the configured base URL would otherwise double up with {@link #KLINES_PATH}'s leading one. */
    private static String stripTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    @Override
    public List<Candle> fetchDailyCandles(String symbol, long startTimeMs, long endTimeMs) {
        URI uri = klinesUri(symbol, startTimeMs, endTimeMs);

        LOG.info("Fetching daily candles from MEXC: symbol={}, start={}, end={}", symbol, startTimeMs, endTimeMs);

        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new MarketDataProviderException("Failed to reach MEXC for symbol " + symbol, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MarketDataProviderException("Interrupted while reaching MEXC for symbol " + symbol, e);
        }

        if (response.statusCode() != 200) {
            handleErrorResponse(symbol, response);
        }

        return parseKlines(response.body());
    }

    URI klinesUri(String symbol, long startTimeMs, long endTimeMs) {
        return URI.create(baseUrl + KLINES_PATH
                + "?symbol=" + symbol
                + "&interval=1d"
                + "&startTime=" + startTimeMs
                + "&endTime=" + endTimeMs);
    }

    void handleErrorResponse(String symbol, HttpResponse<String> response) {
        if (isInvalidSymbolResponse(response.body())) {
            throw new InvalidSymbolException(symbol);
        }
        throw new MarketDataProviderException("MEXC returned HTTP " + response.statusCode() + " for symbol "
                + symbol + ": " + response.body());
    }

    boolean isInvalidSymbolResponse(String responseBody) {
        JsonNode body = readTreeOrNull(responseBody);
        return body != null && body.path("code").asInt() == MEXC_INVALID_SYMBOL_CODE;
    }

    List<Candle> parseKlines(String responseBody) {
        JsonNode rows;
        try {
            rows = objectMapper.readTree(responseBody);
        } catch (IOException e) {
            throw new MarketDataProviderException("Failed to parse MEXC klines response", e);
        }

        if (!rows.isArray()) {
            throw new MarketDataProviderException("MEXC klines response was not a JSON array: " + responseBody);
        }

        List<Candle> candles = new ArrayList<>(rows.size());
        for (JsonNode row : rows) {
            candles.add(toCandle(row));
        }
        return candles;
    }

    /**
     * MEXC kline array indices, confirmed live-identical to Binance's spot klines: [0] open time,
     * [1] open, [2] high, [3] low, [4] close, [5] base-asset volume, [6] close time, [7]
     * quote-asset volume. Volume is read from index 7 (quote-asset, i.e. USDT-denominated), same
     * reasoning as {@code BinanceMarketDataProvider.toCandle}.
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

    private JsonNode readTreeOrNull(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            return null;
        }
    }
}
