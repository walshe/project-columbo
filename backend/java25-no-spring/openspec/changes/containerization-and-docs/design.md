## Context

`backend/java` ships a working `Dockerfile` + `compose.yaml`/`compose.prod.yaml` pairing: a Spring Boot fat jar (Maven Shade/Spring Boot repackaging bundles all dependencies into one jar automatically), copied into a slim JRE run stage, plus a dev-mode Postgres-only Compose file and a `prod`-profile-gated app service pulling a pre-built image from `ghcr.io`.

`backend/java25-no-spring` has no such setup. Its `pom.xml` uses plain `maven-jar-plugin` with a `Main-Class` manifest entry (`walshe.projectcolumbo.supertrend.Main`) but does **not** bundle dependencies — the jar it produces is a thin jar. During this session's manual end-to-end validation (group 16 of the rewrite), the only way to actually run it was `mvn dependency:build-classpath` + `java -cp target/classes:$(cat classpath-file) Main`, which isn't something a Dockerfile should have to replicate at container-run-time.

Its `README.md` also predates the rewrite's implementation entirely and still says "not yet implemented."

## Goals / Non-Goals

**Goals:**
- A `Dockerfile` that builds a runnable image via a normal `docker build .`, with no manual classpath assembly step baked into the entrypoint.
- A dev `compose.yaml` (Postgres only) and a `prod`-profile app service + `compose.prod.yaml` override, mirroring `backend/java`'s existing pattern and env-var-based configuration (`SUPERTREND_DB_*`, matching this module's `DataSourceFactory`/`IngestionConfig` env-var names, not `backend/java`'s Spring-property names).
- A rewritten `README.md` describing the module as it exists today: what it is, package layout, env vars, endpoints, and both the local-dev and fully-containerized ways to run it.

**Non-Goals:**
- CI/CD pipeline changes (image publishing to `ghcr.io` is referenced by `compose.prod.yaml`, matching `backend/java`'s pattern, but wiring up the actual publish workflow is out of scope here).
- Any change to application code, schema, or HTTP behavior.
- Multi-arch image builds, image size optimization beyond using slim/alpine base images, or Kubernetes/Helm manifests.

## Decisions

**Runnable-jar packaging: `maven-dependency-plugin`'s `copy-dependencies` + `maven-jar-plugin`'s `Class-Path` manifest, not a shaded/uber jar.** Considered `maven-shade-plugin`/`maven-assembly-plugin` (matches `backend/java`'s Spring Boot fat-jar mental model most closely) but rejected it: shading merges every dependency's classes into one archive, which can silently break on resource-merging edge cases (e.g. `META-INF/services` files, which Javalin/Jackson's SPI-based plugin discovery may rely on) unless explicitly configured with merge transformers — exactly the kind of hidden complexity this rewrite has otherwise avoided. Instead: `mvn package` copies every runtime-scope dependency jar into `target/lib/` (via `copy-dependencies`), and the main jar's manifest gets `Class-Path: lib/postgresql-....jar lib/...` entries generated automatically (`addClasspath`/`classpathPrefix=lib/`). `java -jar target/supertrend-core.jar` then resolves its classpath from the manifest with no shading, no merge conflicts, and as a side benefit, better Docker layer caching (the `lib/` layer only invalidates when dependencies change, not on every source edit).

**Base images**: `maven:3.9-eclipse-temurin-25-alpine` for the build stage, `eclipse-temurin:25-jre-alpine` for the run stage — verified to actually exist on Docker Hub (via `docker manifest inspect`, not assumed) before committing to them, given this session's earlier experience with web search fabricating nonexistent library versions.

**Dev Compose setup**: `compose.yaml` defines only a `postgres` service by default (image/health-check/volume pattern copied from `backend/java`'s), plus an `app` service gated behind Compose's `prod` profile (`profiles: ["prod"]`) so `docker compose up` in normal dev never starts it — the app is expected to run directly via `mvn compile exec:java`-equivalent or an IDE run configuration against the Dockerized Postgres, exactly like `backend/java`'s documented workflow. Env vars on the gated `app` service use this module's actual names (`SUPERTREND_DB_URL`, `SUPERTREND_DB_USER`, `SUPERTREND_DB_PASSWORD`, `SUPERTREND_BACKFILL_START`, `SUPERTREND_HTTP_PORT`) rather than copying `backend/java`'s `SPRING_DATASOURCE_*` names verbatim.

**Prod Compose override**: `compose.prod.yaml` pulls `ghcr.io/walshe/project-columbo-java25:latest` (a distinct image name/tag from `backend/java`'s `ghcr.io/walshe/project-columbo:latest`, since these are two independently-built, independently-deployed modules) and sets `build: null` to disable local building when running the prod override, matching `backend/java`'s exact structure.

**README rewrite**: describes current, accurate state only — no more "not yet implemented." Documents both run paths (local Postgres-only Compose + `mvn`, and fully-containerized `docker compose --profile prod up --build`) and lists every HTTP endpoint this module now exposes (`/api/v1/signals`, `/api/v1/assets/by-state`, `/api/v1/summary`, `/api/v1/summary/trend-alignment`, `POST /api/v1/scan`, `/api/v1/candles/coverage`, `POST /api/v1/internal/ingestion/run`, plus `/openapi` and `/swagger`).

## Risks / Trade-offs

- **[Risk]** The `Class-Path`-manifest approach requires `lib/*.jar` to sit in the exact relative path recorded in the manifest at runtime — if the Docker `COPY` layout doesn't mirror Maven's `target/lib/` structure exactly, the app fails at startup with `NoClassDefFoundError`. → **Mitigation**: copy both `target/*.jar` and `target/lib/` into the same relative structure in the run stage (`/app/app.jar` + `/app/lib/`), verified locally before merging (`docker build` + `docker run` smoke test against a throwaway Postgres, same pattern already used for this session's manual E2E validation).
- **[Risk]** No CI workflow currently publishes an image to `ghcr.io/walshe/project-columbo-java25`, so `compose.prod.yaml` as written can't actually pull a real image yet. → **Mitigation**: this is called out as an explicit non-goal; `compose.prod.yaml` is added now so the wiring exists and matches `backend/java`'s shape, with the image-publish workflow left as a clearly separate, later piece of work.
- **[Trade-off]** Introducing `maven-dependency-plugin` is a new build-time dependency (not a runtime one) — acceptable under this project's established library policy (pragmatic tooling is fine; this is standard, widely-used Maven core-plugin functionality, not a framework).

## Migration Plan

Not applicable in a deployment-cutover sense — these are new, additive files (`Dockerfile`, `compose.yaml`, `compose.prod.yaml`) plus a documentation rewrite. No existing running system depends on them yet. Rollback is a plain revert if the packaging change causes problems.
