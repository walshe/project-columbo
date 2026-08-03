## ADDED Requirements

### Requirement: Docker image builds and runs standalone
The module SHALL provide a `Dockerfile` that builds a runnable container image via a plain `docker build .`, with no manual classpath-assembly step required at container-run-time. The built image SHALL start `walshe.projectcolumbo.supertrend.Main` and connect to Postgres using the `SUPERTREND_DB_*`/`SUPERTREND_BACKFILL_START`/`SUPERTREND_HTTP_PORT` environment variables already read by `DataSourceFactory`/`IngestionConfig`/`Main`.

#### Scenario: Image builds successfully
- **WHEN** `docker build .` is run in `backend/java25-no-spring/`
- **THEN** the build completes successfully and produces a runnable image

#### Scenario: Container starts and serves HTTP traffic
- **WHEN** the built image is run with `SUPERTREND_DB_*` env vars pointing at a reachable Postgres instance
- **THEN** the container starts, migrates the schema, and responds on its configured HTTP port (e.g. `GET /api/v1/candles/coverage` returns 200)

### Requirement: Dev Compose setup runs Postgres only by default
`compose.yaml` SHALL define a `postgres` service usable for local development, and SHALL NOT start the application service unless the `prod` Compose profile is explicitly requested.

#### Scenario: Default compose up starts only Postgres
- **WHEN** `docker compose up` is run with no profile specified
- **THEN** only the `postgres` service starts; the `app` service does not start

#### Scenario: Prod profile starts the app service too
- **WHEN** `docker compose --profile prod up --build` is run
- **THEN** both `postgres` and `app` services start, and the app successfully connects to the `postgres` service

### Requirement: Prod Compose override pulls a pre-built image
`compose.prod.yaml`, layered on top of `compose.yaml`, SHALL override the `app` service to use a pre-built image reference instead of building locally.

#### Scenario: Prod override disables local build
- **WHEN** `docker compose -f compose.yaml -f compose.prod.yaml --profile prod up -d` is run
- **THEN** the `app` service uses the configured image reference rather than building from the local `Dockerfile`

### Requirement: README accurately describes current, runnable state
`README.md` SHALL describe the module's actual current state (not a "not yet implemented" placeholder), including its package layout, required environment variables, the list of HTTP endpoints it exposes, and both the local-dev (Postgres via Compose + app via `mvn`) and fully-containerized (`docker compose --profile prod up --build`) ways to run it.

#### Scenario: README no longer claims the module is unimplemented
- **WHEN** `README.md` is read
- **THEN** it does not contain placeholder language claiming the module is unimplemented or has nothing to run, and instead documents real run instructions that work as written
