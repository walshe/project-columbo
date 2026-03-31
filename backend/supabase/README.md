# Project Colombo - Supabase Backend

A parallel Supabase implementation of the Project Colombo market intelligence engine. Ingests daily crypto candles from Binance, computes SuperTrend(10, 2.0) indicators, detects signal state transitions, and aggregates market breadth snapshots.

This implementation coexists with the Java/Spring Boot backend — both operate independently against their own databases.

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (required for local Supabase)
- [Deno](https://deno.land/) v1.40+ (`curl -fsSL https://deno.land/install.sh | sh`)
- [Supabase CLI](https://supabase.com/docs/guides/cli/getting-started) v1.100+ (`npm install -g supabase` or `brew install supabase/tap/supabase`)

## Project Structure

```
backend/supabase/
├── config.toml                          # Supabase CLI config
├── seed.sql                             # 60 crypto assets (USDT-suffixed)
├── migrations/                          # PostgreSQL schema (10 migration files)
│   ├── 00001_create_enums.sql
│   ├── 00002_create_asset.sql
│   ├── 00003_create_candle.sql
│   ├── 00004_create_indicator_supertrend.sql
│   ├── 00005_create_signal_state.sql
│   ├── 00006_create_market_breadth_snapshot.sql
│   ├── 00007_create_ingestion_run.sql
│   ├── 00008_create_indexes.sql
│   ├── 00009_create_asset_liquidity_view.sql
│   └── 00010_create_rpc_functions.sql
├── functions/                           # Supabase Edge Functions (Deno/TypeScript)
│   ├── _shared/                         # Shared modules (not deployed)
│   │   ├── types.ts                     # TypeScript types mirroring Java entities
│   │   ├── decimal.ts                   # decimal.js config (SCALE=10, HALF_UP)
│   │   ├── supertrend-calculator.ts     # SuperTrend algorithm (port of Java)
│   │   ├── signal-state-calculator.ts   # Signal detection (port of Java)
│   │   ├── binance-client.ts            # Binance REST API client
│   │   ├── candle-filters.ts            # Finalized candle boundary logic
│   │   ├── pipeline-lock.ts             # Concurrency protection
│   │   └── supabase-client.ts           # Supabase client factory
│   ├── ingest-candles/index.ts          # Phase 1: Fetch & persist candles
│   ├── compute-supertrend/index.ts      # Phase 2: Calculate SuperTrend
│   ├── detect-signals/index.ts          # Phase 3: Detect state transitions
│   ├── compute-market-pulse/index.ts    # Phase 4: Aggregate breadth snapshots
│   ├── run-pipeline/index.ts            # Orchestrator (phases 1-4 sequential)
│   ├── trigger-pipeline/index.ts        # HTTP entry point (POST)
│   └── get-summary/index.ts            # Summary reports (JSON/MD/HTML)
└── tests/                               # Deno unit tests
    ├── supertrend-calculator.test.ts
    └── signal-state-calculator.test.ts
```

---

## Running Locally

### 1. Start local Supabase

```bash
cd backend/supabase
supabase start
```

This starts Docker containers for PostgreSQL, PostgREST, GoTrue, and other Supabase services. On first run it pulls images (~2-3 min).

After startup you'll see output like:

```
╭──────────────────────────────────────╮
│ 🔧 Development Tools                 │
├─────────┬────────────────────────────┤
│ Studio  │ http://127.0.0.1:54323     │
│ Mailpit │ http://127.0.0.1:54324     │
╰─────────┴────────────────────────────╯

╭──────────────────────────────────────────────────────╮
│ 🌐 APIs                                              │
├────────────────┬─────────────────────────────────────┤
│ Project URL    │ http://127.0.0.1:54321              │
│ REST           │ http://127.0.0.1:54321/rest/v1      │
│ GraphQL        │ http://127.0.0.1:54321/graphql/v1   │
│ Edge Functions │ http://127.0.0.1:54321/functions/v1 │
╰────────────────┴─────────────────────────────────────╯

╭───────────────────────────────────────────────────────────────╮
│ ⛁ Database                                                    │
├─────┬─────────────────────────────────────────────────────────┤
│ URL │ postgresql://postgres:postgres@127.0.0.1:54322/postgres │
╰─────┴─────────────────────────────────────────────────────────╯

╭──────────────────────────────────────────────────────────────╮
│ 🔑 Authentication Keys                                       │
├─────────────┬────────────────────────────────────────────────┤
│ Publishable │ sb_publishable_...                              │
│ Secret      │ sb_secret_...                                   │
╰─────────────┴────────────────────────────────────────────────╯
```

**Save the `SERVICE_ROLE_KEY`** — you'll need it for Edge Functions and API calls. The pretty table shows `sb_secret_...` but Kong (the API gateway) needs the JWT format. Get it with:

```bash
supabase status -o json | grep SERVICE_ROLE_KEY
```

This returns the JWT (starts with `eyJhbG...`) which is what you need for `Authorization: Bearer` headers and `.env.local`.

### 2. Apply migrations and seed data

```bash
supabase db reset
```

This drops and recreates the database, runs all migrations (`00001`–`00010`), and applies `seed.sql` (60 crypto assets).

### 3. Verify the schema

```bash
# Connect to local Postgres
psql postgresql://postgres:postgres@127.0.0.1:54322/postgres

# Check tables
\dt

# Check seed data
SELECT count(*) FROM asset WHERE active = true;
-- Expected: 60

# Check RPC functions
\df get_signals
\df get_market_pulse_latest
\df get_market_pulse_history
```

### 4. Run unit tests

```bash
cd backend/supabase
deno test tests/ --allow-all
```

Expected: **25 tests pass** (14 SuperTrend + 11 Signal State).

### 5. Serve Edge Functions locally

```bash
supabase functions serve --env-file .env.local
```

Create `.env.local` first — use the JWT-format service role key (not the `sb_secret_...` display key):

```bash
# Get the JWT key
supabase status -o json | grep SERVICE_ROLE_KEY

# Create .env.local with it
cat > .env.local << 'EOF'
SUPABASE_URL=http://127.0.0.1:54321
SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU
BINANCE_BASE_URL=https://api.binance.com
BACKFILL_START=2025-11-11T00:00:00Z
EOF
```

> **Note:** The JWT key is stable across restarts for a given local project. The `sb_secret_...` shown in the startup table is a display alias — always use the JWT (`eyJhbG...`) for API calls and `.env.local`.

### 6. Trigger the pipeline

```bash
# Run the full 4-phase pipeline
curl -X POST http://127.0.0.1:54321/functions/v1/trigger-pipeline \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU" \
  -H "Content-Type: application/json" \
  -d '{"provider": "BINANCE", "timeframe": "D1"}'
```

You can also run individual phases for debugging:

```bash
# Phase 1 only: Ingest candles
curl -X POST http://127.0.0.1:54321/functions/v1/ingest-candles \
  -H "Authorization: Bearer <SERVICE_ROLE_KEY>"

# Phase 2 only: Compute SuperTrend
curl -X POST http://127.0.0.1:54321/functions/v1/compute-supertrend \
  -H "Authorization: Bearer <SERVICE_ROLE_KEY>"

# Phase 3 only: Detect signals
curl -X POST http://127.0.0.1:54321/functions/v1/detect-signals \
  -H "Authorization: Bearer <SERVICE_ROLE_KEY>"

# Phase 4 only: Compute market pulse
curl -X POST http://127.0.0.1:54321/functions/v1/compute-market-pulse \
  -H "Authorization: Bearer <SERVICE_ROLE_KEY>"
```

### 7. Query the API

```bash
# Get signals (via RPC)
curl "http://127.0.0.1:54321/rest/v1/rpc/get_signals" \
  -H "apikey: <ANON_KEY>" \
  -H "Content-Type: application/json" \
  -d '{"p_timeframe": "D1", "p_indicator_type": "SUPERTREND", "p_sort": "LAST_FLIP_DESC"}'

# Filter bullish only
curl "http://127.0.0.1:54321/rest/v1/rpc/get_signals" \
  -H "apikey: <ANON_KEY>" \
  -H "Content-Type: application/json" \
  -d '{"p_timeframe": "D1", "p_indicator_type": "SUPERTREND", "p_state_filter": "BULLISH", "p_sort": "LIQUIDITY_DESC"}'

# Get latest market pulse
curl "http://127.0.0.1:54321/rest/v1/rpc/get_market_pulse_latest" \
  -H "apikey: <ANON_KEY>" \
  -H "Content-Type: application/json" \
  -d '{"p_timeframe": "D1", "p_indicator_type": "SUPERTREND"}'

# Get market pulse history
curl "http://127.0.0.1:54321/rest/v1/rpc/get_market_pulse_history" \
  -H "apikey: <ANON_KEY>" \
  -H "Content-Type: application/json" \
  -d '{"p_timeframe": "D1", "p_indicator_type": "SUPERTREND", "p_from": "2025-11-01T00:00:00Z", "p_to": "2025-12-31T00:00:00Z"}'

# Get summary report (JSON)
curl "http://127.0.0.1:54321/functions/v1/get-summary?timeframe=D1&format=json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU"

# Get summary report (Markdown)
curl "http://127.0.0.1:54321/functions/v1/get-summary?timeframe=D1&format=markdown" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU"
```

### 8. Stop local Supabase

```bash
supabase stop        # Stop containers (preserves data)
supabase stop --no-backup  # Stop and discard data
```

---

## Deploying to Supabase Cloud

### 1. Create a Supabase project

Go to [supabase.com/dashboard](https://supabase.com/dashboard) and create a new project. Note your:
- **Project ref** (e.g. `abcdefghijklmnop`)
- **Project URL** (e.g. `https://abcdefghijklmnop.supabase.co`)
- **Secret key** (Settings > API — this is the service role key)
- **Publishable key** (Settings > API — this is the anon key)

### 2. Link your local project

```bash
cd backend/supabase
supabase link --project-ref <your-project-ref>
```

You'll be prompted for your database password.

### 3. Push migrations

```bash
supabase db push
```

This applies all migrations (`00001`–`00010`) to your cloud database.

### 4. Seed the database

The seed file doesn't run automatically on cloud. Run it manually:

```bash
# Option A: Via psql (using the connection string from Dashboard > Settings > Database)
psql "<your-cloud-connection-string>" -f seed.sql

# Option B: Via Supabase SQL Editor in the Dashboard
# Copy the contents of seed.sql and run it
```

### 5. Set Edge Function secrets

```bash
supabase secrets set BINANCE_BASE_URL=https://api.binance.com
supabase secrets set BACKFILL_START=2025-11-11T00:00:00Z
```

> `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are automatically available in Edge Functions on Supabase Cloud.

### 6. Deploy Edge Functions

```bash
# Deploy all functions
supabase functions deploy ingest-candles
supabase functions deploy compute-supertrend
supabase functions deploy detect-signals
supabase functions deploy compute-market-pulse
supabase functions deploy run-pipeline
supabase functions deploy trigger-pipeline
supabase functions deploy get-summary
```

Or deploy all at once:

```bash
supabase functions deploy
```

### 7. Test the deployment

```bash
# Trigger the pipeline
curl -X POST https://<project-ref>.supabase.co/functions/v1/trigger-pipeline \
  -H "Authorization: Bearer <SERVICE_ROLE_KEY>" \
  -H "Content-Type: application/json" \
  -d '{"provider": "BINANCE", "timeframe": "D1"}'

# Query signals
curl "https://<project-ref>.supabase.co/rest/v1/rpc/get_signals" \
  -H "apikey: <ANON_KEY>" \
  -H "Content-Type: application/json" \
  -d '{"p_timeframe": "D1", "p_indicator_type": "SUPERTREND", "p_sort": "LAST_FLIP_DESC"}'
```

### 8. Set up daily scheduling (optional)

**Option A: pg_cron (Supabase Pro plan)**

In the SQL Editor, enable `pg_cron` and `pg_net` extensions, then:

```sql
-- Enable extensions (if not already)
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pg_net;

-- Schedule daily pipeline at 00:15 UTC
SELECT cron.schedule(
  'daily-market-pipeline',
  '15 0 * * *',
  $$
  SELECT net.http_post(
    url := 'https://<project-ref>.supabase.co/functions/v1/trigger-pipeline',
    headers := '{"Authorization": "Bearer <SERVICE_ROLE_KEY>", "Content-Type": "application/json"}'::jsonb,
    body := '{"provider": "BINANCE", "timeframe": "D1"}'::jsonb
  );
  $$
);

-- Verify schedule
SELECT * FROM cron.job;
```

**Option B: GitHub Actions (free tier)**

Create `.github/workflows/daily-pipeline.yml`:

```yaml
name: Daily Market Pipeline
on:
  schedule:
    - cron: '15 0 * * *'  # 00:15 UTC daily
  workflow_dispatch:       # Manual trigger

jobs:
  trigger:
    runs-on: ubuntu-latest
    steps:
      - name: Trigger pipeline
        run: |
          curl -X POST "${{ secrets.SUPABASE_URL }}/functions/v1/trigger-pipeline" \
            -H "Authorization: Bearer ${{ secrets.SUPABASE_SERVICE_ROLE_KEY }}" \
            -H "Content-Type: application/json" \
            -d '{"provider": "BINANCE", "timeframe": "D1"}'
```

Add `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` as repository secrets.

---

## API Reference

| Endpoint | Method | Auth | Description |
|---|---|---|---|
| `/functions/v1/trigger-pipeline` | POST | Secret key | Run the full 4-phase pipeline |
| `/functions/v1/ingest-candles` | POST | Secret key | Phase 1: Fetch & persist candles |
| `/functions/v1/compute-supertrend` | POST | Secret key | Phase 2: Calculate SuperTrend |
| `/functions/v1/detect-signals` | POST | Secret key | Phase 3: Detect signal transitions |
| `/functions/v1/compute-market-pulse` | POST | Secret key | Phase 4: Aggregate market breadth |
| `/functions/v1/get-summary?timeframe=D1&format=json` | GET | Secret key | Summary report (json/markdown/html) |
| `/rest/v1/rpc/get_signals` | POST | Publishable key | Query signal states with filtering/sorting |
| `/rest/v1/rpc/get_market_pulse_latest` | POST | Publishable key | Latest market breadth snapshot |
| `/rest/v1/rpc/get_market_pulse_history` | POST | Publishable key | Historical breadth snapshots |

### RPC Parameters

**get_signals:**
```json
{
  "p_timeframe": "D1",
  "p_indicator_type": "SUPERTREND",
  "p_state_filter": "BULLISH",       // optional: BULLISH | BEARISH | UNKNOWN | null
  "p_sort": "LAST_FLIP_DESC"          // ASSET_ASC | LAST_FLIP_ASC | LAST_FLIP_DESC | TREND_STATE_ASC | LIQUIDITY_DESC
}
```

**get_market_pulse_latest:**
```json
{
  "p_timeframe": "D1",
  "p_indicator_type": "SUPERTREND"
}
```

**get_market_pulse_history:**
```json
{
  "p_timeframe": "D1",
  "p_indicator_type": "SUPERTREND",
  "p_from": "2025-11-01T00:00:00Z",
  "p_to": "2025-12-31T00:00:00Z"
}
```

---

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `SUPABASE_URL` | Yes | (auto on cloud) | Supabase project URL |
| `SUPABASE_SERVICE_ROLE_KEY` | Yes | (auto on cloud) | Secret key from `supabase start` (bypasses RLS) |
| `BINANCE_BASE_URL` | No | `https://api.binance.com` | Binance API base URL |
| `BACKFILL_START` | No | `2025-11-11T00:00:00Z` | Historical backfill start date |

---

## Architecture Notes

- **Deterministic computation**: Uses `decimal.js` (not native JS floats) for all SuperTrend arithmetic, matching Java BigDecimal(SCALE=10, HALF_UP)
- **Idempotent operations**: All upserts use conflict detection — safe to re-run
- **Finalized candle rule**: Never processes current day's incomplete candle (UTC midnight boundary)
- **Incremental processing**: Only computes what's missing since last run
- **Concurrency protection**: Prevents overlapping pipeline runs via `ingestion_run` table
- **Audit trail**: Every run logged with inserted/updated/skipped/error counts and duration

### Comparison with Java Backend

| Aspect | Java | Supabase |
|---|---|---|
| Runtime | Spring Boot 4.x / JVM | Edge Functions / Deno |
| Database | PostgreSQL (Flyway) | PostgreSQL (Supabase migrations) |
| Arithmetic | BigDecimal | decimal.js |
| Indicators | SuperTrend + RSI | SuperTrend only |
| Scan API | Yes (AND/OR logic) | Not implemented |
| Scheduling | @Scheduled (cron) | pg_cron or external |
| Auth | None | None (RLS permissive reads) |
