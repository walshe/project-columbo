# CONVENTIONS.md — Code Style & Patterns
<!-- last_mapped: 2026-05-20 -->

## Language & Runtime

- Java 17, Spring Boot 4.0.2
- Maven build (`mvnw`)
- Lombok used for `@Slf4j` logging on services; **not used on JPA entities** (manual getters/setters)

## Dependency Injection

Constructor injection everywhere — no field `@Autowired`. Controllers and services declare all deps as final fields set via a single constructor:

```java
// Pattern used throughout
@Service
public class MarketPipelineService {
    private final CandleIngestionService candleIngestionService;
    private final SuperTrendService superTrendService;

    public MarketPipelineService(CandleIngestionService candleIngestionService,
                                 SuperTrendService superTrendService, ...) {
        this.candleIngestionService = candleIngestionService;
        this.superTrendService = superTrendService;
    }
}
```

## Controller Visibility

Controllers are **package-private** (no `public` modifier on the class):

```java
// backend/java/src/main/java/walshe/projectcolumbo/api/v1/MarketPulseController.java
@RestController
@RequestMapping("/api/v1")
class MarketPulseController {  // ← no public
```

## Error Handling

- `GlobalExceptionHandler` (`@RestControllerAdvice`) handles exceptions at the API layer
- Uses Spring 6 `ProblemDetail` (RFC 9457) for all error responses
- Handler is scoped to specific controller classes via `assignableTypes`
- Custom exceptions: `BadRequestException` (400), `IngestionAlreadyRunningException` (409)

```java
@ExceptionHandler(BadRequestException.class)
ProblemDetail handleBadRequestException(BadRequestException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setTitle("Bad Request");
    return pd;
}
```

## Financial Calculations

All monetary and indicator values use `BigDecimal`:
- Scale: 10 decimal places
- Rounding: `RoundingMode.HALF_UP`
- Calculators are pure, stateless `@Component` classes with no Spring dependencies

```java
// backend/java/src/main/java/walshe/projectcolumbo/persistence/service/SuperTrendCalculator.java
private static final int SCALE = 10;
private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
```

## PostgreSQL Enum Mapping

Native PostgreSQL enums are mapped via Hibernate's `@JdbcTypeCode`:

```java
@Enumerated(EnumType.STRING)
@JdbcTypeCode(SqlTypes.NAMED_ENUM)
private Timeframe timeframe;
```

Used on: `Timeframe`, `MarketProvider`, `IndicatorType`, `TrendState`, `SignalEvent`.

## Testable Time

`TimeProvider` interface wraps `OffsetDateTime.now()` to allow test doubles:

```java
public interface TimeProvider {
    OffsetDateTime now();
}
```

`UtcTimeProvider` is the production impl. Tests inject a fixed or controllable impl.

## Logging

- SLF4J throughout; `LoggerFactory.getLogger(ClassName.class)` on plain classes
- `@Slf4j` (Lombok) on service classes that use it (`ScanService`)
- Pipeline phases log start/end with elapsed ms via `System.currentTimeMillis()`
- Errors logged with `logger.error("...", e)` including full stack trace

## JPA Entity Style

- Plain getters/setters (no Lombok on entities)
- `@ManyToOne(fetch = FetchType.LAZY)` for associations
- No `@Builder` or `@AllArgsConstructor` — entities use no-arg constructor + setters
- `created_at` columns are `insertable = false, updatable = false` (DB default)

## API Versioning

All endpoints under `/api/v1/`. Internal endpoints under `/api/v1/internal/`.

## Response Patterns

- Return `ResponseEntity<T>` from controllers
- Use `Optional.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build())` for single-item lookups
- Collections return 200 with empty list (not 404)

## Incremental vs Full Recalc

Calculators support both modes via a `fullRecalc` boolean:

```java
superTrendService.processAllActiveAssets(timeframe, 10, new BigDecimal("2.0"), false);
rsiComputationService.computeForActiveAssets(timeframe, 14, false);
// false = incremental (default pipeline); true = full recalc
```

## Validation

- Bean Validation (`spring-boot-starter-validation`) for request DTOs
- Custom validator classes (e.g., `ScanValidator`) for complex business rules

---

*Last mapped: 2026-05-20*
