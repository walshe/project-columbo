package walshe.projectcolumbo.supertrend.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Binance's public klines API — the only market data provider actually active in production. */
public final class BinanceMarketDataProvider implements MarketDataProvider {

    private static final Logger LOG = System.getLogger(BinanceMarketDataProvider.class.getName());
    private static final String BASE_URL = "https://api.binance.com";
    private static final String KLINES_PATH = "/api/v3/klines";
    private static final int BINANCE_INVALID_SYMBOL_CODE = -1121;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BinanceMarketDataProvider() {
        this(HttpClient.newHttpClient());
    }

    public BinanceMarketDataProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public List<Candle> fetchDailyCandles(String symbol, long startTimeMs, long endTimeMs) {
        String normalizedSymbol = normalizeSymbol(symbol);
        URI uri = URI.create(BASE_URL + KLINES_PATH
                + "?symbol=" + normalizedSymbol
                + "&interval=1d"
                + "&startTime=" + startTimeMs
                + "&endTime=" + endTimeMs);

        LOG.log(Level.INFO, "Fetching daily candles from Binance: symbol={0} (normalized={1}), start={2}, end={3}",
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
