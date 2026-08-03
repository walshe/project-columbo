# TESTING.md — Test Structure & Practices
<!-- last_mapped: 2026-05-20 -->

## Framework Stack

| Tool | Version | Role |
|------|---------|------|
| JUnit 5 | (Spring Boot managed) | Test runner |
| Mockito | 5.14.2 | Mocking |
| Spring Boot Test | 4.0.2 | `@SpringBootTest`, `@MockitoBean` |
| Testcontainers | (Spring Boot managed) | Real PostgreSQL in tests |
| WireMock Standalone | 3.5.2 | HTTP stub server for external APIs |
| JaCoCo | 0.8.12 | Code coverage reports |

## Two Test Tiers

### Unit Tests (`*Test.java`)

Pure Java tests with no Spring context. Used for stateless calculators and service logic:

- `SuperTrendCalculatorTest`, `SuperTrendCalculatorIncrementalTest`
- `RsiCalculatorLogicTest`
- `SignalStateCalculatorTest`, `SignalStateServiceLogicTest`, `SignalStateUpdateUnknownTest`
- `CandleFiltersTest`
- `ScanValidatorTest`, `ScanServiceTest`
- `MarketPipelineServiceTest`, `IngestionOrchestratorTest`
- Provider tests: `BinanceMarketDataProviderTest`, `CoinGeckoMarketDataProviderTest`

Mocking pattern (Mockito, no Spring):
```java
@ExtendWith(MockitoExtension.class)
class SomeServiceTest {
    @Mock SomeDependency dep;
    @InjectMocks SomeService sut;
}
```

### Integration Tests (`*IntegrationTest.java` / `*IT.java`)

Full Spring context + real PostgreSQL via Testcontainers. Used for pipeline, persistence, and API flows:

- `MarketPipelineIntegrationTest` — end-to-end pipeline (ingest → indicators → signals → pulse)
- `CandleIngestionIntegrationTest`, `IncrementalIngestionIntegrationTest`
- `IngestionOrchestratorIntegrationTest`
- `ApiIntegrationTest`
- `ScanIntegrationTest`
- Repository tests: `RsiRepositoryTest`, `SignalStateRepositoryTest`, `SuperTrendRepositoryTest`, `IngestionRunRepositoryTest`

Standard setup:
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MarketPipelineIntegrationTest {
    // Real DB, @MockitoBean for external HTTP providers
}
```

## Shared Test Infrastructure

**`TestcontainersConfiguration`** — reusable `@TestConfiguration` that provides a `PostgreSQLContainer` bean with `@ServiceConnection`:

```java
// backend/java/src/test/java/walshe/projectcolumbo/TestcontainersConfiguration.java
@Bean
@ServiceConnection
public PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
}
```

All integration tests `@Import(TestcontainersConfiguration.class)` — no duplication of container setup.

**`TestProjectColumboApplication`** — test bootstrap for launching the full app in integration tests.

## External API Mocking

- `MarketDataProvider` implementations are mocked via `@MockitoBean` in integration tests — real HTTP never goes out
- WireMock (`wiremock-standalone`) is available for HTTP-level stubbing when needed
- CoinGecko live test (`CoinGeckoMarketDataProviderIT`) is **disabled** (requires API key)

```java
@MockitoBean(name = "binanceMarketDataProvider")
private MarketDataProvider binanceProvider;

@BeforeEach
void setUp() {
    when(binanceProvider.getProviderName()).thenReturn("BINANCE");
    when(binanceProvider.fetchDailyCandles(eq("BTCUSDT"), any(), any())).thenReturn(candles);
}
```

## Test Data Setup

Integration tests use `@BeforeEach` to delete all data in dependency order before each test:

```java
@BeforeEach
void setUp() {
    signalStateRepository.deleteAll();
    superTrendRepository.deleteAll();
    rsiRepository.deleteAll();
    candleRepository.deleteAll();
    marketBreadthSnapshotRepository.deleteAll();
    ingestionRunRepository.deleteAll();
    assetRepository.deleteAll();
}
```

## Coverage

JaCoCo configured via Maven plugin — runs during `mvn test`, report generated at `target/site/jacoco/`. No enforced minimum threshold in `pom.xml`.

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
</plugin>
```

## Running Tests

```bash
cd backend/java

# All tests
./mvnw test

# Skip tests
./mvnw package -DskipTests

# Single class
./mvnw test -Dtest=MarketPipelineIntegrationTest
```

Requires Docker running (for Testcontainers PostgreSQL).

## Key Patterns

| Pattern | Where used |
|---------|-----------|
| Real DB via Testcontainers | All `*IntegrationTest` classes |
| `@MockitoBean` for HTTP providers | Integration tests with external API calls |
| `@BeforeEach` full delete | Integration tests — each test starts clean |
| Stateless calculator unit tests | `RsiCalculatorLogicTest`, `SuperTrendCalculator*Test` |
| Idempotency verification | `MarketPipelineIntegrationTest.shouldBeIdempotentOnRerun` |
| Failure scenario tests | `shouldMarkRunAsFailedOnException` pattern |

---

*Last mapped: 2026-05-20*
