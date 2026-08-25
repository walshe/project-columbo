package walshe.projectcolumbo.supertrend.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tiingo's daily EOD prices API — the market data source for real (non-tokenized) equities,
 * distinct from Binance's tokenized STOCK/ETF perpetuals. One instance serves every {@code
 * EXCHANGE}-venue asset; unlike Binance, Tiingo has no spot/futures split.
 */
public final class TiingoMarketDataProvider implements MarketDataProvider {

    private static final Logger LOG = LoggerFactory.getLogger(TiingoMarketDataProvider.class);
    private static final String DEFAULT_BASE_URL = "https://api.tiingo.com";
    private static final String PRICES_PATH_TEMPLATE = "/tiingo/daily/%s/prices";
    private static final int NOT_FOUND_STATUS = 404;

    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TiingoMarketDataProvider(HttpClient httpClient, String apiKey) {
        this(httpClient, apiKey, DEFAULT_BASE_URL);
    }

    /** @param baseUrl overridable so tests can point this provider at a stub server instead of the real Tiingo API. */
    public TiingoMarketDataProvider(HttpClient httpClient, String apiKey, String baseUrl) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.apiKey = requireNonBlank(apiKey, "apiKey");
        this.baseUrl = stripTrailingSlashes(requireNonBlank(baseUrl, "baseUrl"));
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    /** A trailing slash on the configured base URL would otherwise double up with the prices path's leading one. */
    private static String stripTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    @Override
    public List<Candle> fetchDailyCandles(String symbol, long startTimeMs, long endTimeMs) {
        URI uri = pricesUri(symbol, startTimeMs, endTimeMs);

        LOG.info("Fetching daily candles from Tiingo: symbol={}, start={}, end={}", symbol, startTimeMs, endTimeMs);

        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new MarketDataProviderException("Failed to reach Tiingo for symbol " + symbol, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MarketDataProviderException("Interrupted while reaching Tiingo for symbol " + symbol, e);
        }

        if (response.statusCode() != 200) {
            handleErrorResponse(symbol, response);
        }

        return parseDailyPrices(response.body());
    }

    URI pricesUri(String symbol, long startTimeMs, long endTimeMs) {
        // endTimeMs is the exclusive not-yet-finalized boundary (see FinalizedBoundary), so the
        // last calendar day actually requested is the one that boundary minus 1ms falls on.
        String startDate = toCalendarDate(startTimeMs);
        String endDate = toCalendarDate(endTimeMs - 1);
        // URLEncoder applies application/x-www-form-urlencoded rules (e.g. space -> "+"), which
        // is wrong for a URL path segment; ".replace("+", "%20")" is this codebase's existing fix
        // for that mismatch (see TradingViewUrl.encode).
        String encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create(baseUrl + PRICES_PATH_TEMPLATE.formatted(encodedSymbol)
                + "?startDate=" + startDate
                + "&endDate=" + endDate
                + "&token=" + apiKey);
    }

    static String toCalendarDate(long epochMs) {
        LocalDate date = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate();
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    void handleErrorResponse(String symbol, HttpResponse<String> response) {
        if (response.statusCode() == NOT_FOUND_STATUS) {
            throw new InvalidSymbolException(symbol);
        }
        throw new MarketDataProviderException("Tiingo returned HTTP " + response.statusCode() + " for symbol "
                + symbol + ": " + response.body());
    }

    List<Candle> parseDailyPrices(String responseBody) {
        JsonNode rows;
        try {
            rows = objectMapper.readTree(responseBody);
        } catch (IOException e) {
            throw new MarketDataProviderException("Failed to parse Tiingo daily prices response", e);
        }

        List<Candle> candles = new ArrayList<>(rows.size());
        for (JsonNode row : rows) {
            candles.add(toCandle(row));
        }
        return candles;
    }

    /**
     * Uses the split/dividend-adjusted fields ({@code adjOpen}/{@code adjHigh}/{@code adjLow}/
     * {@code adjClose}/{@code adjVolume}), not the raw ones, so a split or dividend doesn't look
     * like a discontinuous price jump to SuperTrend. {@code closeTime} follows Binance's own D1
     * convention ({@code open + 1 day - 1ms}) for consistency with every other candle already
     * stored — derived from {@code openTime}'s calendar date rather than assumed to already be
     * exact UTC midnight, since some of the seeded assets (Shanghai A-shares, OTC ADRs) may not
     * share Tiingo's usual US-exchange {@code date} convention.
     */
    Candle toCandle(JsonNode row) {
        OffsetDateTime openTime = OffsetDateTime.parse(row.get("date").asText());
        OffsetDateTime closeTime = openTime.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime().minusNanos(1_000_000);
        return new Candle(
                openTime,
                closeTime,
                Timeframe.D1,
                row.get("adjOpen").decimalValue(),
                row.get("adjHigh").decimalValue(),
                row.get("adjLow").decimalValue(),
                row.get("adjClose").decimalValue(),
                row.get("adjVolume").decimalValue()
        );
    }
}
