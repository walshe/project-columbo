package walshe.projectcolumbo.supertrend.api;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import walshe.projectcolumbo.supertrend.chart.CandleChartService;
import walshe.projectcolumbo.supertrend.chart.CandleChartService.CandleChart;
import walshe.projectcolumbo.supertrend.freshness.FreshnessMetadata;
import walshe.projectcolumbo.supertrend.freshness.FreshnessService;
import walshe.projectcolumbo.supertrend.freshness.FreshnessStatus;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.persistence.Asset;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.shared.TradingViewUrl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Registers {@code GET /api/v1/candles}: one asset's stored candles as a chart-ready time series,
 * optionally with Columbo's own SuperTrend line and flip markers aligned to the same bars.
 * Deliberately library-agnostic - no charting library's name appears in the path or fields.
 */
public final class CandlesHandler {

    static final int DEFAULT_LIMIT = 500;
    static final int MAX_LIMIT = 5000;
    private static final String INCLUDE_SUPERTREND = "supertrend";

    private final AssetDao assetDao;
    private final CandleChartService candleChartService;
    private final FreshnessService freshnessService;

    public CandlesHandler(AssetDao assetDao, CandleChartService candleChartService, FreshnessService freshnessService) {
        this.assetDao = Objects.requireNonNull(assetDao, "assetDao must not be null");
        this.candleChartService = Objects.requireNonNull(candleChartService, "candleChartService must not be null");
        this.freshnessService = Objects.requireNonNull(freshnessService, "freshnessService must not be null");
    }

    public void register(Javalin app) {
        app.get("/api/v1/candles", this::getCandles);
    }

    @OpenApi(
            path = "/api/v1/candles",
            methods = HttpMethod.GET,
            summary = "One asset's OHLCV candles as a chart-ready time series, optionally with its SuperTrend line and trend-flip markers",
            description = "Returns the asset's stored candles oldest-to-newest. Every `time` (candles, supertrend, signals) is the bar's "
                    + "OPEN time in UTC epoch seconds, so the arrays can be passed straight to a charting library "
                    + "(e.g. TradingView Lightweight Charts `setData`; multiply by 1000 for millisecond-based libraries). "
                    + "Only finalized candles are stored: today's forming D1 bar and the current week's forming W1 bar are never included. "
                    + "Without from/to this returns the most recent `limit` bars. To load older history, repeat the call with "
                    + "to=<candles[0].time> while `hasMore` is true - `to` is exclusive, so pages never overlap. "
                    + "With include=supertrend the response also carries `supertrend` (one point per returned candle: line value and "
                    + "direction UP = bullish/line below price, DOWN = bearish/line above price) and `signals` (only the bars where the "
                    + "trend flipped). These are the exact values behind /api/v1/signals and the briefings - plot them rather than "
                    + "recomputing SuperTrend client-side, which would drift (it is recursive and depends on history outside the window). "
                    + "Stale data is still returned (200); check `stale`/`candlesThrough`.",
            queryParams = {
                    @OpenApiParam(name = "symbol", type = String.class, required = true,
                            description = "Exact, case-sensitive symbol of an active asset, e.g. BTCUSDT."),
                    @OpenApiParam(name = "timeframe", type = Timeframe.class, required = true,
                            description = "D1 = daily candles, W1 = weekly (Monday-open) candles."),
                    @OpenApiParam(name = "from", type = String.class,
                            description = "Inclusive lower bound on candle open time: UTC epoch seconds (e.g. 1790812800), an ISO-8601 instant "
                                    + "(2026-10-01T00:00:00Z) or a date (2026-10-01 = UTC midnight). Omit for no lower bound."),
                    @OpenApiParam(name = "to", type = String.class,
                            description = "Exclusive upper bound on candle open time, same formats as `from`. For paging pass the "
                                    + "previous response's candles[0].time verbatim. Omit for no upper bound (up to the latest stored candle)."),
                    @OpenApiParam(name = "limit", type = Integer.class,
                            description = "Maximum candles to return (the most recent inside the window). Default 500, max 5000."),
                    @OpenApiParam(name = "include", type = String.class,
                            description = "Comma-separated extras. Only `supertrend` is supported: adds the `supertrend` line and `signals` flip markers.")
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = CandlesResponse.class),
                            description = "The candle window; an empty `candles` array is valid when no candles fall inside it"),
                    @OpenApiResponse(status = "400", description = "Missing/invalid symbol, timeframe, from, to, limit or include, or from is not before to"),
                    @OpenApiResponse(status = "404", description = "No active asset has this symbol"),
                    @OpenApiResponse(status = "409", description = "More than one active asset (different providers) shares this symbol; details list the providers")
            }
    )
    private void getCandles(Context ctx) {
        String symbol = ctx.queryParamAsClass("symbol", String.class).check(s -> !s.isBlank(), "symbol must not be blank").get();
        Timeframe timeframe = ctx.queryParamAsClass("timeframe", Timeframe.class).get();
        int limit = ctx.queryParamAsClass("limit", Integer.class)
                .check(l -> l >= 1 && l <= MAX_LIMIT, "limit must be between 1 and " + MAX_LIMIT)
                .getOrDefault(DEFAULT_LIMIT);
        OffsetDateTime from = parseBound("from", ctx.queryParam("from"));
        OffsetDateTime to = parseBound("to", ctx.queryParam("to"));
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BadRequestResponse("from must be before to");
        }
        boolean includeSuperTrend = parseInclude(ctx.queryParam("include"));

        Asset asset = resolveActiveAsset(symbol);
        CandleChart chart = candleChartService.load(asset.id(), timeframe, from, to, limit, includeSuperTrend);
        FreshnessStatus status = freshnessService.evaluate(timeframe);
        ctx.json(toResponse(asset, timeframe, chart, status));
    }

    private Asset resolveActiveAsset(String symbol) {
        List<Asset> matches = assetDao.findActiveBySymbol(symbol);
        if (matches.isEmpty()) {
            throw new NotFoundResponse("No active asset with symbol " + symbol);
        }
        if (matches.size() > 1) {
            String providers = matches.stream().map(a -> a.provider().name()).collect(Collectors.joining(","));
            throw new ConflictResponse("More than one active asset has symbol " + symbol, Map.of("providers", providers));
        }
        return matches.getFirst();
    }

    /** Epoch seconds, ISO-8601 instant, or ISO-8601 date (UTC midnight); null when absent/blank. */
    static OffsetDateTime parseBound(String name, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            if (value.chars().allMatch(Character::isDigit)) {
                return Instant.ofEpochSecond(Long.parseLong(value)).atOffset(ZoneOffset.UTC);
            }
            if (value.length() == 10) {
                return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
            }
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException | NumberFormatException e) {
            throw new BadRequestResponse(name + " must be epoch seconds, an ISO-8601 instant or a date (yyyy-MM-dd), was: " + raw);
        }
    }

    private static boolean parseInclude(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        boolean superTrend = false;
        for (String part : raw.split(",")) {
            String value = part.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) {
                continue;
            }
            if (!value.equals(INCLUDE_SUPERTREND)) {
                throw new BadRequestResponse("Unsupported include value: " + part.trim() + " (supported: " + INCLUDE_SUPERTREND + ")");
            }
            superTrend = true;
        }
        return superTrend;
    }

    private CandlesResponse toResponse(Asset asset, Timeframe timeframe, CandleChart chart, FreshnessStatus status) {
        // Overlay rows are keyed by close time in the DB; give each the time of the returned candle
        // it belongs to, so every series shares one time axis by construction.
        Map<Instant, Long> timeByCloseTime = new HashMap<>();
        List<CandlesResponse.Bar> bars = chart.candles().stream().map(candle -> {
            long time = candle.openTime().toEpochSecond();
            timeByCloseTime.put(candle.closeTime().toInstant(), time);
            return toBar(candle, time);
        }).toList();

        List<CandlesResponse.SuperTrendPoint> superTrend = chart.superTrend() == null ? null : chart.superTrend().stream()
                .filter(row -> timeByCloseTime.containsKey(row.closeTime().toInstant()))
                .map(row -> new CandlesResponse.SuperTrendPoint(timeByCloseTime.get(row.closeTime().toInstant()), row.superTrend(), row.direction()))
                .toList();
        List<CandlesResponse.SignalMarker> signals = chart.flips() == null ? null : chart.flips().stream()
                .filter(flip -> timeByCloseTime.containsKey(flip.closeTime().toInstant()))
                .map(flip -> new CandlesResponse.SignalMarker(timeByCloseTime.get(flip.closeTime().toInstant()), flip.event()))
                .toList();

        FreshnessMetadata metadata = freshnessService.metadataFor(status);
        String tradingviewUrl = TradingViewUrl.generateUrl(asset.provider(), asset.symbol(), timeframe, asset.venue(), asset.tradingviewRef());
        return new CandlesResponse(asset.symbol(), asset.name(), asset.assetClass(), timeframe, tradingviewUrl, chart.hasMore(),
                bars, superTrend, signals, metadata.lastSuccessfulIngestionAt(), metadata.latestCandleDate(), !status.upToDate());
    }

    private static CandlesResponse.Bar toBar(Candle candle, long time) {
        return new CandlesResponse.Bar(time, candle.open(), candle.high(), candle.low(), candle.close(), candle.volume());
    }
}
