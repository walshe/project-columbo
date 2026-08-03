## Why

`backend/java25-no-spring` is now functionally complete (all 16 groups of `supertrend-core-java25-rewrite` merged) but has no way to run it the way `backend/java` runs — no Dockerfile, no `docker compose` setup, and no dev-database bootstrap other than pointing `SUPERTREND_DB_*` env vars at a manually-managed Postgres. Its `README.md` is also still the placeholder written before implementation started ("not yet implemented... nothing to run yet"), which is now actively misleading. This change brings the module's operational story up to parity with `backend/java` (Dockerfile + dev/prod compose) and rewrites the README to describe the module as it actually exists today.

## What Changes

- Add a multi-stage `Dockerfile`: build stage compiles with Maven against this module's pinned Java 25 toolchain, run stage ships a slim JRE 25 image. Unlike `backend/java`'s Spring Boot fat jar, this module's `maven-jar-plugin` produces a thin jar with a `Main-Class` manifest but no bundled dependencies — the run stage must also carry the resolved dependency jars (or an assembled/shaded jar) so `java -jar app.jar` actually has a classpath, not just the fat-jar copy `backend/java`'s Dockerfile relies on.
- Add `compose.yaml`: a `postgres` service for local dev (matching `backend/java`'s image/health-check/volume pattern), and an `app` service gated behind a `prod` Compose profile so it never starts during normal dev workflows (dev runs the app directly via `mvn`/IDE against the Dockerized Postgres, same as `backend/java`).
- Add `compose.prod.yaml`: the prod-only override pulling a pre-built image from `ghcr.io`, paired with `compose.yaml` the same way `backend/java` does.
- Rewrite `README.md` to reflect current reality: what the module is, its package layout, how to run it locally (Postgres via Compose + app via `mvn`), how to run it fully containerized, the required `SUPERTREND_*` env vars, and the list of HTTP endpoints it now exposes.

## Capabilities

### New Capabilities
- `containerized-deployment`: building and running this module as a Docker image, and standing up a local dev/prod environment via `docker compose`, mirroring `backend/java`'s existing setup.

### Modified Capabilities
(none — no existing runtime-behavior specs change; this only adds a deployment capability and updates documentation)

## Impact

- New files: `Dockerfile`, `compose.yaml`, `compose.prod.yaml` (module root, alongside `pom.xml`).
- Rewritten: `README.md`.
- No changes to application code, schema, or any existing HTTP endpoint behavior.
- `pom.xml` may need a packaging adjustment (e.g. `maven-assembly-plugin`/`maven-shade-plugin` for a runnable fat jar, or a `Class-Path` manifest entry pointing at a `lib/` directory copied into the image) so the Docker run stage doesn't need the `mvn dependency:build-classpath` workaround used for manual testing during development.
