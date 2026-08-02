package walshe.projectcolumbo.supertrend.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import walshe.projectcolumbo.supertrend.freshness.FreshnessService;
import walshe.projectcolumbo.supertrend.freshness.FreshnessStatus;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers {@code GET /api/v1/candles/coverage}: per-timeframe candle history extent and
 * freshness, keyed by timeframe name. {@code expectedLatest}/{@code upToDate} are delegated to
 * {@link FreshnessService} so this endpoint and every read endpoint's {@code stale} flag can
 * never disagree about what counts as a finalized candle.
 */
public final class CandleCoverageHandler {

    private final CandleDao candleDao;
    private final FreshnessService freshnessService;

    public CandleCoverageHandler(CandleDao candleDao, FreshnessService freshnessService) {
        this.candleDao = candleDao;
        this.freshnessService = freshnessService;
    }

    public void register(Javalin app) {
        app.get("/api/v1/candles/coverage", this::getCoverage);
    }

    private void getCoverage(Context ctx) {
        Map<Timeframe, CandleCoverage> coverage = new LinkedHashMap<>();
        for (Timeframe timeframe : Timeframe.values()) {
            coverage.put(timeframe, coverageFor(timeframe));
        }
        ctx.json(coverage);
    }

    private CandleCoverage coverageFor(Timeframe timeframe) {
        FreshnessStatus status = freshnessService.evaluate(timeframe);
        OffsetDateTime earliest = candleDao.findEarliestCloseTimeAcrossAllAssets(timeframe).orElse(null);
        long assetCount = candleDao.countDistinctAssetsForTimeframe(timeframe);

        return new CandleCoverage(earliest, status.actualLatestCloseTime(), status.expectedLatestCloseTime(), status.upToDate(), assetCount);
    }
}
