## 1. Runnable-jar packaging

- [x] 1.1 Add `maven-dependency-plugin` (`copy-dependencies` goal bound to `package`) to copy runtime-scope dependency jars into `target/lib/`
- [x] 1.2 Configure `maven-jar-plugin`'s archive/manifest for `addClasspath=true`, `classpathPrefix=lib/` so the built jar's manifest references `lib/*.jar`
- [x] 1.3 Verify `java -jar target/supertrend-core.jar` runs standalone (no `dependency:build-classpath` workaround) given `target/lib/` sits alongside the jar — confirmed live: schema migrated, served real HTTP traffic

## 2. Dockerfile

- [x] 2.1 Multi-stage `Dockerfile`: build stage on `maven:3.9-eclipse-temurin-25-alpine`, run stage on `eclipse-temurin:25-jre-alpine`
- [x] 2.2 Build stage: cache the dependency layer separately from source (copy `pom.xml` first, resolve, then copy `src`), matching `backend/java`'s Dockerfile pattern
- [x] 2.3 Run stage: copy the built jar and `target/lib/` into the same relative layout, `ENTRYPOINT ["java", "-jar", "app.jar"]`, `EXPOSE` the app's HTTP port
- [x] 2.4 Verified with a real `docker build` + `docker run` smoke test against a throwaway Postgres on a shared Docker network: container started, migrated schema, served `GET /api/v1/candles/coverage` (200)

## 3. Compose files

- [x] 3.1 `compose.yaml`: `postgres` service (image/health-check/volume pattern from `backend/java`'s compose.yaml, adjusted database name/credentials for this module)
- [x] 3.2 `compose.yaml`: `app` service gated behind the `prod` Compose profile, env vars using this module's actual names (`SUPERTREND_DB_URL`/`SUPERTREND_DB_USER`/`SUPERTREND_DB_PASSWORD`/`SUPERTREND_BACKFILL_START` - the last one required here since, unlike `backend/java`, there's no baked-in application-config default), `depends_on` the `postgres` service's health check
- [x] 3.3 `compose.prod.yaml`: override the `app` service to pull `ghcr.io/walshe/project-columbo-java25:latest` instead of building locally
- [x] 3.4 Verified live: default `docker compose up` started only `postgres`; `docker compose --profile prod up --build` started both, app connected successfully and served real HTTP traffic

## 4. README rewrite

- [x] 4.1 Replace the "not yet implemented" placeholder with an accurate description of the module (what it is, package layout)
- [x] 4.2 Document required env vars (`SUPERTREND_DB_URL`/`SUPERTREND_DB_USER`/`SUPERTREND_DB_PASSWORD`, `SUPERTREND_BACKFILL_START`, `SUPERTREND_HTTP_PORT`)
- [x] 4.3 Document the list of HTTP endpoints exposed (signals, assets/by-state, summary, summary/trend-alignment, scan, candles/coverage, internal/ingestion/run, openapi, swagger)
- [x] 4.4 Document both run paths: local dev (Postgres via `docker compose up` + app via `mvn compile exec:java` - live-verified to work with zero pom.xml changes needed) and fully containerized (`docker compose --profile prod up --build`)
