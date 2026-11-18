package walshe.projectcolumbo.supertrend.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;

import java.util.List;
import java.util.Objects;

/** Registers {@code GET /api/v1/symbols}: the full sorted list of active asset symbols. */
public final class SymbolsHandler {

    private final AssetDao assetDao;

    public SymbolsHandler(AssetDao assetDao) {
        this.assetDao = Objects.requireNonNull(assetDao, "assetDao must not be null");
    }

    public void register(Javalin app) {
        app.get("/api/v1/symbols", this::getSymbols);
    }

    @OpenApi(
            path = "/api/v1/symbols",
            methods = HttpMethod.GET,
            summary = "List every active asset symbol",
            description = "Returns a sorted JSON array of every currently-active asset symbol string — the same population "
                    + "that can be passed to GET /api/v1/candles or GET /api/v1/signals?symbols=... . "
                    + "Use this as a cheap discovery call to know what symbols are available before fetching candles or signals. "
                    + "No query parameters are accepted; the full list is always returned.",
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = String[].class),
                            description = "Sorted array of active symbol strings, e.g. [\"BTCUSDT\", \"ETHUSDT\", ...]. Empty array when no assets are active.")
            }
    )
    private void getSymbols(Context ctx) {
        List<String> symbols = assetDao.findAllActive().stream()
                .map(asset -> asset.symbol())
                .sorted()
                .toList();
        ctx.json(symbols);
    }
}
