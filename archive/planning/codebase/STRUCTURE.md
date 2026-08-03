# STRUCTURE.md — Directory Layout & Organization
<!-- last_mapped: 2026-05-20 -->

## Root Layout

```
project-colombo/
├── backend/
│   └── java/                        ← Spring Boot Maven project
│       ├── pom.xml
│       ├── compose.yaml             ← Docker Compose (dev DB)
│       ├── Dockerfile
│       └── src/
│           ├── main/
│           │   ├── java/walshe/projectcolumbo/
│           │   └── resources/
│           │       ├── application.yaml
│           │       └── db/migration/ ← Flyway SQL migrations
│           └── test/
│               └── java/walshe/projectcolumbo/
├── docs/                            ← Project documentation
├── stories/                         ← User stories / requirements
├── strategies/                      ← Trading strategy notes
├── PRD.md
├── README.md
└── ARCITECTURE.md                   ← (note: typo in filename)
```

## Java Package Structure

Root package: `walshe.projectcolumbo`

```
walshe/projectcolumbo/
├── ProjectColumboApplication.java       ← @SpringBootApplication entry point

├── api/
│   ├── exception/
│   │   └── BadRequestException.java
│   └── v1/
│       ├── GlobalExceptionHandler.java  ← @RestControllerAdvice (ProblemDetail)
│       ├── IngestionController.java     ← POST /api/v1/internal/ingestion/run
│       ├── MarketPulseController.java   ← GET /api/v1/market-pulse
│       ├── MarketPulseQueryService.java
│       ├── SignalController.java        ← GET /api/v1/signals
│       ├── SignalQueryService.java
│       ├── dto/                         ← Request/response DTOs
│       ├── mapper/                      ← Entity-to-DTO mappers
│       ├── scan/
│       │   ├── ScanController.java      ← POST /api/v1/scan
│       │   ├── ScanService.java
│       │   ├── ScanValidator.java
│       │   └── dto/                     ← Scan-specific DTOs
│       ├── summary/
│       │   ├── SummaryController.java   ← GET /api/v1/summary
│       │   ├── SummaryService.java
│       │   ├── SummaryReportFormatter.java
│       │   └── dto/
│       └── util/
│           └── TradingViewUtil.java     ← TradingView URL generation

├── config/
│   ├── RestClientConfig.java
│   ├── TimeProvider.java                ← Interface for testable time
│   ├── UtcTimeProvider.java
│   └── WebConfig.java                   ← CORS config

├── ingestion/
│   ├── CandleIngestionService.java      ← Fetches + delegates persistence
│   ├── CandlePersistenceService.java    ← Upserts candles
│   ├── IngestionAlreadyRunningException.java
│   ├── IngestionOrchestrator.java       ← Run lifecycle management
│   ├── IngestionProperties.java         ← @ConfigurationProperties
│   ├── IngestionRun.java                ← JPA entity for run tracking
│   ├── IngestionRunRepository.java
│   ├── IngestionRunStatus.java          ← Enum: RUNNING/SUCCESS/PARTIAL/FAILED
│   ├── MarketPipelineScheduler.java     ← @Scheduled cron trigger
│   ├── MarketPipelineService.java       ← 4-phase pipeline coordinator
│   └── RunMode.java                     ← Enum: INCREMENTAL/FULL

├── marketdata/
│   ├── MarketDataProvider.java          ← Interface
│   ├── BinanceMarketDataProvider.java
│   ├── BinanceProperties.java
│   ├── CoinGeckoMarketDataProvider.java
│   ├── CoinGeckoProperties.java
│   └── CandleDto.java                   ← Raw fetch result

├── marketpulse/
│   └── MarketPulseService.java          ← Computes MarketBreadthSnapshot

└── persistence/
    ├── entity/
    │   ├── Asset.java                   ← symbol, provider, active flag
    │   ├── AssetLiquidityView.java      ← DB view projection
    │   ├── Candle.java                  ← OHLCV + timeframe + raw JSONB
    │   ├── MarketBreadthSnapshot.java
    │   ├── RsiIndicator.java
    │   ├── SignalState.java             ← TrendState + SignalEvent per asset
    │   └── SuperTrendIndicator.java
    ├── model/
    │   ├── IndicatorType.java           ← RSI, SUPERTREND
    │   ├── MarketProvider.java          ← BINANCE, COINGECKO
    │   ├── SignalEvent.java             ← BULLISH_CROSS, BEARISH_CROSS, NONE
    │   ├── SignalStateResult.java
    │   ├── SuperTrendDirection.java     ← UP, DOWN
    │   ├── SuperTrendResult.java        ← Record (closeTime, atr, upper, lower, value, direction)
    │   ├── Timeframe.java               ← D1, etc.
    │   └── TrendState.java             ← BULLISH, BEARISH, UNKNOWN
    ├── repository/
    │   ├── AssetLiquidityRepository.java
    │   ├── AssetRepository.java
    │   ├── CandleRepository.java
    │   ├── MarketBreadthSnapshotRepository.java
    │   ├── RsiRepository.java
    │   ├── SignalStateRepository.java
    │   ├── SignalStateRepositoryCustom.java  ← Custom query interface
    │   ├── SignalStateRepositoryImpl.java    ← JPQL/native SQL impl
    │   └── SuperTrendRepository.java
    └── service/
        ├── CandleFilters.java               ← Filter utilities
        ├── RsiCalculator.java               ← Pure RSI computation (Wilder's)
        ├── RsiComputationService.java       ← Orchestrates RSI for all assets
        ├── SignalStateCalculator.java       ← Pure signal state logic
        ├── SignalStateService.java          ← Orchestrates signal detection
        ├── SuperTrendCalculator.java        ← Pure SuperTrend computation
        └── SuperTrendService.java           ← Orchestrates SuperTrend for all assets
```

## Test Structure

Mirrors main package layout under `src/test/java/walshe/projectcolumbo/`:

```
test/
├── TestcontainersConfiguration.java    ← Shared Postgres 16-alpine container
├── TestProjectColumboApplication.java  ← Test bootstrap
├── ProjectColumboApplicationTests.java ← Smoke test
├── api/v1/                             ← Controller unit + integration tests
│   ├── ApiIntegrationTest.java
│   ├── IngestionControllerTest.java
│   ├── MarketPulseQueryServiceTest.java
│   ├── SignalQueryServiceTest.java
│   ├── scan/
│   │   ├── ScanIntegrationTest.java
│   │   ├── ScanServiceTest.java
│   │   └── ScanValidatorTest.java
│   └── summary/
│       └── SummaryControllerTest.java
├── config/TimeProviderTest.java
├── ingestion/                          ← Pipeline integration tests (heavy)
│   ├── CandleIngestionIntegrationTest.java
│   ├── CandleIngestionServiceTest.java
│   ├── CandlePersistenceServiceTest.java
│   ├── IncrementalIngestionIntegrationTest.java
│   ├── IngestionOrchestratorIntegrationTest.java
│   ├── IngestionOrchestratorTest.java
│   ├── IngestionRunRepositoryTest.java
│   ├── MarketPipelineIntegrationTest.java
│   └── MarketPipelineServiceTest.java
├── marketdata/
│   ├── BinanceMarketDataProviderTest.java
│   ├── CoinGeckoMarketDataProviderIT.java  ← disabled live integration test
│   └── CoinGeckoMarketDataProviderTest.java
├── marketpulse/MarketPulseServiceTest.java
└── persistence/
    ├── repository/                         ← @DataJpaTest-style repo tests
    └── service/                            ← Pure unit tests for calculators
```

## Naming Conventions

| Pattern | Example |
|---------|---------|
| Controllers | `<Domain>Controller` — package-private, no `public` modifier |
| Services (pipeline) | `<Domain>Service` — `@Service` |
| Calculators (pure) | `<Indicator>Calculator` — `@Component`, stateless |
| DTOs | `<Domain>Dto`, `<Domain>Request`, `<Domain>Response` |
| Entities | PascalCase, no suffix |
| Repos | `<Entity>Repository` extending `JpaRepository` |
| Custom repos | `<Entity>RepositoryCustom` + `<Entity>RepositoryImpl` |
| Integration tests | `*IntegrationTest.java` or `*IT.java` |
| Unit tests | `*Test.java` |

## Where to Add New Code

| What | Where |
|------|-------|
| New API endpoint | `api/v1/<domain>/` — new controller + query service + DTOs |
| New indicator | `persistence/service/<Name>Calculator.java` (pure) + `<Name>Service.java` (orchestrator) |
| New entity | `persistence/entity/` + `persistence/repository/` |
| New pipeline phase | Add to `MarketPipelineService.runDaily()` after existing phases |
| New market data source | Implement `MarketDataProvider` in `marketdata/` |
| DB migration | `src/main/resources/db/migration/V{N}__description.sql` |

---

*Last mapped: 2026-05-20*
