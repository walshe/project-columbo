# ARCHITECTURE.md — System Design & Patterns
<!-- last_mapped: 2026-05-20 -->

## Pattern

Layered Spring Boot monolith with a domain-driven package structure. Core flow is a **scheduled data pipeline** that runs nightly, with a REST API for querying results. The pipeline is the primary business logic; the API is read-heavy.

## Layers

```
HTTP ──► api/v1/            Controllers, DTOs, Mappers, GlobalExceptionHandler
          │
          ▼
         ingestion/          Pipeline orchestration and run tracking
          │
          ├──► marketdata/   External data provider abstraction (Binance, CoinGecko)
          │
          ├──► persistence/  JPA entities, Spring Data repositories, calculators
          │     ├── entity/       JPA entity classes
          │     ├── model/        Enums and value types
          │     ├── repository/   Spring Data repos + custom impls
          │     └── service/      Pure calculator components
          │
          └──► marketpulse/  Market breadth aggregation

         config/             Cross-cutting beans (RestClient, TimeProvider, CORS)
```

## 4-Phase Pipeline

`MarketPipelineService.runDaily()` is the central coordinator. Each phase runs sequentially inside a single `IngestionRun` record:

```
Phase 1: INGESTION       CandleIngestionService.ingestDaily()
                         → fetches from Binance via MarketDataProvider
                         → persists via CandlePersistenceService

Phase 2: INDICATOR       SuperTrendService.processAllActiveAssets()     (ATR 10, multiplier 2.0)
                         RsiComputationService.computeForActiveAssets()  (period 14)

Phase 3: SIGNAL          SignalStateService.detectDaily()
                         → compares new indicators to prior state
                         → emits BULLISH_CROSS / BEARISH_CROSS / NONE events

Phase 4: MARKET_PULSE    MarketPulseService.computeDaily()
                         → aggregates signal states into MarketBreadthSnapshot
```

Trigger paths:
- **Scheduled**: `MarketPipelineScheduler` fires at `0 5 0 * * *` (00:05 UTC daily)
- **Manual**: `POST /api/v1/internal/ingestion/run`

## Entry Points

| Entry | Class | Purpose |
|-------|-------|---------|
| `main()` | `ProjectColumboApplication` | Spring Boot bootstrap |
| Scheduler | `MarketPipelineScheduler` | Cron-triggered pipeline |
| REST | `IngestionController` | Manual pipeline trigger |
| REST | `MarketPulseController` | Query market breadth |
| REST | `SignalController` | Query signal states |
| REST | `ScanController` | Multi-condition asset scan |
| REST | `SummaryController` | Market summary report |

## Data Flow

```
Binance API
    │
    ▼
CandleDto (raw fetch)
    │
    ▼
Candle (JPA entity, persisted with raw JSONB payload)
    │
    ├──► SuperTrendIndicator (ATR-based trend band)
    │
    ├──► RsiIndicator (Wilder's smoothing RSI)
    │
    ▼
SignalState (per-asset trend state + event: BULLISH_CROSS / BEARISH_CROSS / NONE)
    │
    ▼
MarketBreadthSnapshot (aggregate: % bullish, % bearish, % unknown per timeframe+indicator)
```

## Key Abstractions

| Abstraction | Interface/Base | Implementations |
|------------|----------------|-----------------|
| Market data provider | `MarketDataProvider` | `BinanceMarketDataProvider`, `CoinGeckoMarketDataProvider` |
| Time | `TimeProvider` | `UtcTimeProvider` (prod), test doubles |
| Custom repo | `SignalStateRepositoryCustom` | `SignalStateRepositoryImpl` |

## Concurrency Model

Single-threaded pipeline with a concurrency guard: `IngestionOrchestrator` and `MarketPipelineService` both check for an existing `RUNNING` `IngestionRun` before starting, throwing `IngestionAlreadyRunningException` if one exists. No async execution within the pipeline.

## Configuration

Property binding via `@ConfigurationProperties`:
- `IngestionProperties` — `app.ingestion.*` (backfill start date)
- `BinanceProperties` — `app.binance.*` (base URL)
- `CoinGeckoProperties` — `app.coingecko.*` (API key, base URL)
- Pipeline cron: `app.market-pipeline.cron`

---

*Last mapped: 2026-05-20*
