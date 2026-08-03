# Technology Stack
<!-- scope: backend/java — Java Spring Boot implementation only -->

**Analysis Date:** 2026-05-20

## Language & Runtime

- **Java 17** — source compatibility (build); Docker image uses JRE 21 (`eclipse-temurin:21-jre-alpine`)
- **SQL (PostgreSQL)** — schema managed via Flyway migrations (`backend/java/src/main/resources/db/migration/`)

## Build

- **Maven** (wrapper: `backend/java/mvnw`)
- Manifest: `backend/java/pom.xml`
- Dockerfile: multi-stage — `maven:3.9-eclipse-temurin-21-alpine` build → `eclipse-temurin:21-jre-alpine` runtime, exposes port 8080

## Frameworks

| Dependency | Version | Role |
|-----------|---------|------|
| Spring Boot | 4.0.2 | Application framework (parent POM) |
| Spring Web MVC (`spring-boot-starter-webmvc`) | managed | REST API layer |
| Spring Data JPA (`spring-boot-starter-data-jpa`) | managed | ORM + repository layer |
| Spring Validation (`spring-boot-starter-validation`) | managed | Bean validation (JSR-380) |
| Spring Actuator (`spring-boot-starter-actuator`) | managed | Health + metrics endpoints |
| Spring Flyway (`spring-boot-starter-flyway`) | managed | DB migration runner |
| `flyway-database-postgresql` | managed | PostgreSQL Flyway dialect |
| SpringDoc OpenAPI UI | 2.8.8 | Swagger UI at `/swagger-ui.html` |
| Lombok | managed | `@Slf4j`, boilerplate reduction |

## Key Dependencies

| Dependency | Role |
|-----------|------|
| `org.postgresql:postgresql` (runtime) | JDBC driver |
| `org.springframework.boot:spring-boot-starter-data-jpa` | JPA/Hibernate ORM |
| `org.flywaydb:flyway-database-postgresql` | Schema migrations |

## Testing Dependencies

| Dependency | Version | Role |
|-----------|---------|------|
| `spring-boot-starter-test` | managed | JUnit 5 + Mockito integration |
| Mockito | 5.14.2 | Mocking framework |
| `spring-boot-testcontainers` | managed | Testcontainers Spring integration |
| `testcontainers-junit-jupiter` | managed | JUnit 5 Testcontainers extension |
| `testcontainers-postgresql` | managed | PostgreSQL container |
| WireMock Standalone | 3.5.2 | HTTP stub server |
| JaCoCo Maven Plugin | 0.8.12 | Code coverage reports |
| Maven Surefire Plugin | 3.5.2 | Test runner (with Mockito agent) |

## Configuration

Primary config: `backend/java/src/main/resources/application.yaml`

| Property | Default | Notes |
|---------|---------|-------|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/columbo` | Override via `SPRING_DATASOURCE_URL` |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Schema owned by Flyway |
| `app.binance.base-url` | `https://api.binance.com` | |
| `app.coingecko.api-key` | `""` (blank) | Bound to `COINGECKO_API_KEY` env var |
| `app.coingecko.base-url` | `https://api.coingecko.com/api/v3` | |
| `app.market-pipeline.cron` | `0 5 0 * * *` | 00:05 UTC daily, Europe/Dublin |
| `app.ingestion.backfill-start` | `2025-11-11T00:00:00Z` | |

**Required production env vars:**
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `COINGECKO_API_KEY` (optional)

## Database Migrations

- Location: `backend/java/src/main/resources/db/migration/`
- 12 versioned Flyway migrations (V1–V12)
- PostgreSQL 15.5 locally via `backend/java/compose.yaml`

## Platform Requirements (Dev)

- Java 17+ JDK
- Maven 3.9+
- Docker (local PostgreSQL via `backend/java/compose.yaml`)

---

*Stack analysis: 2026-05-20*
