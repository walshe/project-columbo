# External Integrations
<!-- scope: backend/java — Java Spring Boot implementation only -->

**Analysis Date:** 2026-05-20

## Market Data APIs

### Binance REST API (Primary)
- **Purpose:** Fetches daily OHLCV candles (klines) for crypto assets
- **Endpoint:** `https://api.binance.com/api/v3/klines`
- **Client:** Spring `RestClient` — `backend/java/src/main/java/walshe/projectcolumbo/marketdata/BinanceMarketDataProvider.java`
- **Auth:** None (public endpoint)
- **Rate limiting:** 200ms delay between per-asset requests (`Thread.sleep`)
- **Config:** `app.binance.base-url` in `application.yaml`
- **Error handling:** Returns empty list for invalid symbol (Binance error code `-1121`)
- **Interface:** Implements `MarketDataProvider` — swappable with other providers

### CoinGecko REST API (Secondary)
- **Purpose:** Alternative OHLC data provider (daily candles)
- **Endpoint:** `https://api.coingecko.com/api/v3/coins/{id}/ohlc?vs_currency=usd&days=365`
- **Client:** Spring `RestClient` — `backend/java/src/main/java/walshe/projectcolumbo/marketdata/CoinGeckoMarketDataProvider.java`
- **Auth:** Optional — API key sent as `x-cg-demo-api-key` header; blank = unauthenticated (free tier)
- **Auth env var:** `COINGECKO_API_KEY`
- **Rate limiting:** 500ms `Thread.sleep()` between requests
- **Config:** `app.coingecko.base-url` and `app.coingecko.api-key` in `application.yaml`
- **Note:** Live integration test (`CoinGeckoMarketDataProviderIT`) is disabled — requires API key

## Database

- **PostgreSQL** — primary and only data store
- **Client:** Spring Data JPA / Hibernate ORM
- **Repositories:** `backend/java/src/main/java/walshe/projectcolumbo/persistence/repository/`
- **Schema management:** Flyway (V1–V12 migrations in `backend/java/src/main/resources/db/migration/`)
- **Local dev:** PostgreSQL 15.5 via Docker (`backend/java/compose.yaml`)
- **Connection config:** `spring.datasource.*` in `application.yaml`

### Key Tables

| Table | Purpose |
|-------|---------|
| `asset` | Tracked crypto assets with provider and active flag |
| `candle` | Daily OHLCV keyed by `(asset_id, timeframe, close_time)`; upserted with ON CONFLICT |
| `indicator_supertrend` | SuperTrend values per asset/candle |
| `rsi_indicator` | RSI values per asset/candle |
| `signal_state` | Per-asset trend state (BULLISH/BEARISH/UNKNOWN) and signal events |
| `market_breadth_snapshot` | Aggregated market pulse counts per date |
| `ingestion_run` | Pipeline run tracking (status, metrics, error samples) |
| `asset_liquidity_view` | DB view — 7-day average volume per asset |

## Scheduling

- **Mechanism:** Spring `@Scheduled`
- **Class:** `backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineScheduler.java`
- **Cron:** `0 5 0 * * *` — 00:05 UTC daily, Europe/Dublin timezone
- **Manual trigger:** `POST /api/v1/internal/ingestion/run`

## Monitoring & Observability

- **Health/Metrics:** Spring Boot Actuator — `/actuator/health` and standard metrics endpoints
- **Logging:** SLF4J + Logback (Spring Boot default); Flyway debug logging enabled
- **API Docs:** SpringDoc OpenAPI — Swagger UI at `/swagger-ui.html`
- **Coverage:** JaCoCo reports at `target/site/jacoco/` after `mvn test`
- **Error tracking:** None (no Sentry, Datadog, or equivalent)

## Authentication

- **Java API:** None — all REST endpoints (`/api/v1/*`, `/api/v1/internal/*`) are unauthenticated
- **CORS:** Not configured (WebConfig only registers a `Timeframe` string converter)

## CI/CD

- **CI pipeline:** None detected
- **Docker:** `backend/java/Dockerfile` — production image; started via `docker compose --profile prod up -d --build`

---

*Integration audit: 2026-05-20*
