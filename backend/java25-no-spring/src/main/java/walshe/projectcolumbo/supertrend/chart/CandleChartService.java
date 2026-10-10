package walshe.projectcolumbo.supertrend.chart;

import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendResult;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.PersistenceException;
import walshe.projectcolumbo.supertrend.persistence.SignalStateDao;
import walshe.projectcolumbo.supertrend.persistence.SuperTrendIndicatorDao;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalState;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Loads one asset's candle window for charting, plus (optionally) the stored SuperTrend line and
 * flips covering exactly the same candles - all on one pooled connection per request.
 */
public final class CandleChartService {

    private final DataSource dataSource;
    private final CandleDao candleDao;
    private final SuperTrendIndicatorDao superTrendIndicatorDao;
    private final SignalStateDao signalStateDao;

    public CandleChartService(DataSource dataSource, CandleDao candleDao,
                              SuperTrendIndicatorDao superTrendIndicatorDao, SignalStateDao signalStateDao) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.candleDao = Objects.requireNonNull(candleDao, "candleDao must not be null");
        this.superTrendIndicatorDao = Objects.requireNonNull(superTrendIndicatorDao, "superTrendIndicatorDao must not be null");
        this.signalStateDao = Objects.requireNonNull(signalStateDao, "signalStateDao must not be null");
    }

    /**
     * @param fromOpenInclusive lower bound on candle open time; null = unbounded
     * @param toOpenExclusive   upper bound on candle open time; null = unbounded
     * @param limit             maximum candles returned - the most recent ones inside the window
     * @param includeSuperTrend whether to also load the SuperTrend line and flips for the returned candles
     */
    public CandleChart load(long assetId, Timeframe timeframe, OffsetDateTime fromOpenInclusive,
                            OffsetDateTime toOpenExclusive, int limit, boolean includeSuperTrend) {
        try (Connection connection = dataSource.getConnection()) {
            List<Candle> fetched = candleDao.findLatestInOpenTimeWindow(connection, assetId, timeframe,
                    fromOpenInclusive, toOpenExclusive, limit);
            // The DAO returns up to limit + 1 rows, oldest first: an extra row means older history exists.
            boolean hasMore = fetched.size() > limit;
            List<Candle> candles = hasMore ? fetched.subList(1, fetched.size()) : fetched;

            if (!includeSuperTrend) {
                return new CandleChart(candles, hasMore, null, null);
            }
            if (candles.isEmpty()) {
                return new CandleChart(candles, hasMore, List.of(), List.of());
            }
            OffsetDateTime firstClose = candles.getFirst().closeTime();
            OffsetDateTime lastClose = candles.getLast().closeTime();
            List<SuperTrendResult> superTrend = superTrendIndicatorDao.findBetween(connection, assetId, timeframe, firstClose, lastClose);
            List<SignalState> flips = signalStateDao.findFlipsBetween(connection, assetId, timeframe, firstClose, lastClose);
            return new CandleChart(candles, hasMore, superTrend, flips);
        } catch (SQLException e) {
            throw new PersistenceException("Failed to acquire connection to load chart data for asset " + assetId + " " + timeframe, e);
        }
    }

    /**
     * @param superTrend null when not requested
     * @param flips      null when not requested
     */
    public record CandleChart(List<Candle> candles, boolean hasMore, List<SuperTrendResult> superTrend, List<SignalState> flips) {
        public CandleChart {
            Objects.requireNonNull(candles, "candles must not be null");
        }
    }
}
