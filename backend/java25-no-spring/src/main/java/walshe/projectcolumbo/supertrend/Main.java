package walshe.projectcolumbo.supertrend;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import walshe.projectcolumbo.supertrend.api.ApiServer;
import walshe.projectcolumbo.supertrend.api.CandleCoverageHandler;
import walshe.projectcolumbo.supertrend.api.IngestionTriggerHandler;
import walshe.projectcolumbo.supertrend.api.ScanHandler;
import walshe.projectcolumbo.supertrend.api.SignalsHandler;
import walshe.projectcolumbo.supertrend.api.SummaryHandler;
import walshe.projectcolumbo.supertrend.api.TrendAlignmentHandler;
import walshe.projectcolumbo.supertrend.api.WeeklyPullbackBriefingHandler;
import walshe.projectcolumbo.supertrend.api.WeeklyTrendBriefingHandler;
import walshe.projectcolumbo.supertrend.freshness.FreshnessService;
import walshe.projectcolumbo.supertrend.indicator.IndicatorComputationService;
import walshe.projectcolumbo.supertrend.ingestion.BackfillStartValidator;
import walshe.projectcolumbo.supertrend.ingestion.BinanceMarketDataProvider;
import walshe.projectcolumbo.supertrend.ingestion.CandleIngestionService;
import walshe.projectcolumbo.supertrend.ingestion.IngestionConfig;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.AssetLiquidityDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.DataSourceFactory;
import walshe.projectcolumbo.supertrend.persistence.IngestionRunDao;
import walshe.projectcolumbo.supertrend.persistence.MarketBreadthSnapshotDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.persistence.SignalStateDao;
import walshe.projectcolumbo.supertrend.persistence.SuperTrendIndicatorDao;
import walshe.projectcolumbo.supertrend.pipeline.DailyScheduler;
import walshe.projectcolumbo.supertrend.pipeline.PipelineOrchestrator;
import walshe.projectcolumbo.supertrend.pulse.MarketBreadthPulseService;
import walshe.projectcolumbo.supertrend.rollup.CandleRollupService;
import walshe.projectcolumbo.supertrend.shared.AssetVenue;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.ProvisionalTrendService;
import walshe.projectcolumbo.supertrend.signal.ScanService;
import walshe.projectcolumbo.supertrend.signal.SignalQueryService;
import walshe.projectcolumbo.supertrend.signal.SignalStateDetectionService;
import walshe.projectcolumbo.supertrend.signal.TrendAlignmentService;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

/** Composition root: wires every DAO/service/handler by hand (no DI container) and starts the HTTP server + daily scheduler. */
public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final int DEFAULT_HTTP_PORT = 8080;

    public static void main(String[] args) {
        LOG.info("SuperTrend Core starting (Java {})", Runtime.version());

        Clock clock = Clock.systemUTC();
        DataSource dataSource = DataSourceFactory.create();
        SchemaMigrator.migrate(dataSource);

        IngestionConfig ingestionConfig = IngestionConfig.fromEnvironment();
        new BackfillStartValidator(clock).validate(ingestionConfig.backfillStart());

        AssetDao assetDao = new AssetDao(dataSource);
        CandleDao candleDao = new CandleDao(dataSource);
        SuperTrendIndicatorDao superTrendIndicatorDao = new SuperTrendIndicatorDao(dataSource);
        SignalStateDao signalStateDao = new SignalStateDao(dataSource);
        MarketBreadthSnapshotDao marketBreadthSnapshotDao = new MarketBreadthSnapshotDao(dataSource);
        IngestionRunDao ingestionRunDao = new IngestionRunDao(dataSource);
        AssetLiquidityDao assetLiquidityDao = new AssetLiquidityDao(dataSource);

        HttpClient httpClient = HttpClient.newHttpClient();
        BinanceMarketDataProvider spotProvider = binanceSpotBaseUrl()
                .map(baseUrl -> new BinanceMarketDataProvider(httpClient, AssetVenue.SPOT, baseUrl))
                .orElseGet(() -> new BinanceMarketDataProvider(httpClient, AssetVenue.SPOT));
        BinanceMarketDataProvider futuresProvider = binanceFuturesBaseUrl()
                .map(baseUrl -> new BinanceMarketDataProvider(httpClient, AssetVenue.FUTURES, baseUrl))
                .orElseGet(() -> new BinanceMarketDataProvider(httpClient, AssetVenue.FUTURES));
        CandleIngestionService candleIngestionService = new CandleIngestionService(
                assetDao, candleDao,
                Map.of(AssetVenue.SPOT, spotProvider, AssetVenue.FUTURES, futuresProvider),
                ingestionConfig, clock);
        IndicatorComputationService indicatorComputationService =
                new IndicatorComputationService(assetDao, candleDao, superTrendIndicatorDao);
        CandleRollupService candleRollupService = new CandleRollupService(assetDao, candleDao, clock);
        SignalStateDetectionService signalStateDetectionService =
                new SignalStateDetectionService(assetDao, candleDao, signalStateDao);
        MarketBreadthPulseService marketBreadthPulseService =
                new MarketBreadthPulseService(assetDao, signalStateDao, marketBreadthSnapshotDao);
        PipelineOrchestrator pipelineOrchestrator = new PipelineOrchestrator(
                assetDao, ingestionRunDao, candleIngestionService, indicatorComputationService,
                candleRollupService, signalStateDetectionService, marketBreadthPulseService, clock);

        FreshnessService freshnessService = new FreshnessService(candleDao, ingestionRunDao, clock);
        SignalQueryService signalQueryService = new SignalQueryService(assetDao, signalStateDao, candleDao, assetLiquidityDao);
        TrendAlignmentService trendAlignmentService = new TrendAlignmentService(signalQueryService, clock);
        ScanService scanService = new ScanService(signalQueryService, clock);
        ProvisionalTrendService provisionalTrendService = new ProvisionalTrendService(assetDao, candleDao, clock);

        Javalin app = ApiServer.create();
        new SignalsHandler(signalQueryService, freshnessService).register(app);
        new TrendAlignmentHandler(trendAlignmentService, freshnessService, clock).register(app);
        new SummaryHandler(signalQueryService, marketBreadthSnapshotDao, freshnessService, clock).register(app);
        new ScanHandler(scanService).register(app);
        new CandleCoverageHandler(candleDao, freshnessService).register(app);
        new IngestionTriggerHandler(pipelineOrchestrator).register(app);
        new WeeklyTrendBriefingHandler(pipelineOrchestrator, marketBreadthSnapshotDao, signalQueryService, trendAlignmentService, scanService, provisionalTrendService, clock).register(app);
        new WeeklyPullbackBriefingHandler(pipelineOrchestrator, marketBreadthSnapshotDao, signalQueryService, trendAlignmentService, scanService, provisionalTrendService, clock).register(app);
        app.start(httpPort());

        DailyScheduler dailyScheduler = new DailyScheduler(pipelineOrchestrator, Provider.BINANCE, Timeframe.D1, clock);
        dailyScheduler.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(dailyScheduler, app, dataSource), "shutdown-hook"));

        LOG.info("SuperTrend Core ready.");
    }

    private static void shutdown(DailyScheduler dailyScheduler, Javalin app, DataSource dataSource) {
        LOG.info("SuperTrend Core shutting down...");
        dailyScheduler.stop();
        app.stop();
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                LOG.warn("Failed to close data source cleanly", e);
            }
        }
    }

    private static int httpPort() {
        String value = System.getenv("SUPERTREND_HTTP_PORT");
        return (value == null || value.isBlank()) ? DEFAULT_HTTP_PORT : Integer.parseInt(value);
    }

    /** Overridable so an end-to-end test can point Binance spot calls at a stub server instead of the real API. */
    private static Optional<String> binanceSpotBaseUrl() {
        return envOrEmpty("SUPERTREND_BINANCE_SPOT_BASE_URL");
    }

    /** Overridable so an end-to-end test can point Binance futures calls at a stub server instead of the real API. */
    private static Optional<String> binanceFuturesBaseUrl() {
        return envOrEmpty("SUPERTREND_BINANCE_FUTURES_BASE_URL");
    }

    private static Optional<String> envOrEmpty(String name) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
    }
}
