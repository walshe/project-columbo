# Phase 1: W1 Candle Derivation - Research

**Researched:** 2026-05-20
**Domain:** Java Spring Boot — timeframe rollup, Flyway enum migration, JPA upsert
**Confidence:** HIGH

---

## Summary

Phase 1 adds W1 weekly candles to a system that currently produces only D1 daily candles. The
work is entirely within `backend/java/` and has two distinct concerns: (1) a Flyway migration
that extends the `timeframe` PostgreSQL enum to include `'W1'`, and (2) a new
`CandleRollupService` that groups completed D1 candles by calendar week (Mon–Sun UTC) and
persists one W1 candle per week per asset into the existing `candle` table.

The existing codebase already provides every building block needed. `CandlePersistenceService`
demonstrates the insert/update/skip upsert pattern. `CandleRepository` already has a
`findByAssetAndTimeframeAndCloseTime` method that forms the idempotency check. `CandleFilters`
shows the boundary-filtering idiom. `SuperTrendService` and `RsiComputationService` demonstrate
the `processAllActiveAssets(Timeframe, ...)` pattern that the rollup service should mirror.
The `Timeframe` enum needs one new constant (`W1`) with a string value of `"1W"` to follow the
existing `D1("1D")` convention.

The primary risk area is the PostgreSQL enum migration. Extending a live enum with `ALTER TYPE
... ADD VALUE` has restrictions: the new value cannot be added inside a transaction block in
older Postgres versions, and Flyway by default wraps each migration in a transaction. The
standard solution is to use `ALTER TYPE ... ADD VALUE IF NOT EXISTS` outside a transaction, or
to use a `flyway:mixed` approach. The project currently targets Postgres 16 (as seen in
`TestcontainersConfiguration`), which does support `ALTER TYPE ... ADD VALUE` non-transactionally
via a migration configured with `@Transactional(false)` / `executeInTransaction = false` in
Flyway.

**Primary recommendation:** Create `V13__add_w1_timeframe.sql` using `ALTER TYPE timeframe ADD
VALUE IF NOT EXISTS 'W1'` with `executeInTransaction = false`, add `W1("1W")` to the `Timeframe`
enum, then implement `CandleRollupService` following the `processAllActiveAssets` pattern.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CNDL-01 | Derive W1 candles by rolling up D1 candles (Monday open → Sunday close, UTC) | `CandleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc` provides the D1 source; week grouping by `openTime.with(DayOfWeek.MONDAY)` |
| CNDL-02 | Partial weeks (current incomplete week) are not stored | Filter: include only week groups where `closeTime` of the group's Sunday candle is before UTC midnight today — same idiom as `CandleFilters.finalizedBeforeUtcMidnightToday` |
| CNDL-03 | Rollup is incremental — only derive new W1 candles from D1 candles added since last rollup | Check `CandleRepository.findLatestCloseTime(assetId, "W1")` to find last stored W1 and skip already-rolled-up weeks |
| CNDL-04 | Rollup mechanism is timeframe-generic (no D1/W1 literals in core logic) | Service accepts `sourceTimeframe` and `targetTimeframe` parameters; week boundary supplied as `weekStartDay` (DayOfWeek) |
| CNDL-05 | DB schema supports W1 as a valid Timeframe value (Flyway migration) | `ALTER TYPE timeframe ADD VALUE IF NOT EXISTS 'W1'` in `V13__add_w1_timeframe.sql` with `executeInTransaction = false` |

</phase_requirements>

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| DB enum extension (W1) | Database / Storage | — | DDL change to PostgreSQL `timeframe` enum via Flyway |
| Java enum extension (W1) | API / Backend | — | `Timeframe.W1("1W")` constant for type-safe references |
| Week boundary calculation | API / Backend | — | Pure Java — `TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)` |
| Incomplete-week guard | API / Backend | — | Compare last D1 close in group against UTC midnight today |
| D1 candle fetching | Database / Storage | API / Backend | Existing `CandleRepository` query |
| W1 candle upsert | Database / Storage | API / Backend | Existing `candle` table with UNIQUE constraint as idempotency key |
| Incremental detection | API / Backend | Database / Storage | Query latest stored W1 close_time; skip already-complete weeks |
| Service orchestration | API / Backend | — | `CandleRollupService.rollupForAllActiveAssets(source, target, weekStart)` |

---

## Standard Stack

### Core (all already in pom.xml — no new dependencies needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot Data JPA | 4.0.2 (via parent) | ORM / repository layer | Already used throughout the project |
| Flyway (PostgreSQL dialect) | managed by Spring Boot parent | Schema migrations | Existing pattern — V1–V12 already present |
| PostgreSQL 16 | runtime (Testcontainers: `postgres:16-alpine`) | Database | Existing target |
| JUnit Jupiter + Testcontainers | managed by Spring Boot test | Integration tests | Existing pattern across 35+ test files |
| Mockito 5.14.2 | in pom.xml | Unit test mocking | Existing pattern |
| Lombok | in pom.xml | Boilerplate reduction | Used elsewhere (e.g., `@RequiredArgsConstructor`) |

[VERIFIED: codebase grep] — all libraries confirmed in `backend/java/pom.xml`.

### Supporting

None required beyond what is already on the classpath. `java.time` (JDK 17) provides all week
boundary logic (`DayOfWeek`, `TemporalAdjusters`, `OffsetDateTime`).

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Pure Java `java.time` week grouping | SQL GROUP BY EXTRACT(WEEK ...) | Java grouping keeps logic testable without a DB; SQL approach would be a single query but harder to unit-test and couples rollup logic to the DB |
| Single `V13` migration file | Recreate enum (drop/create) | Cannot drop a type that columns reference — `ADD VALUE` is the only safe migration path |

**Installation:** No new packages. This phase installs zero external dependencies.

---

## Package Legitimacy Audit

No external packages are introduced in this phase. The section is omitted per the "skip if no new
packages" rule.

---

## Architecture Patterns

### System Architecture Diagram

```
[Daily D1 candles in candle table]
         |
         v
CandleRollupService.rollupForAllActiveAssets(D1, W1, MONDAY)
         |
         +---> AssetRepository.findByActiveTrue()
         |          |
         |          v
         |     per-asset loop
         |          |
         |          v
         |     CandleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, D1)
         |          |
         |          v
         |     Group D1 candles into ISO weeks (Mon open_time → Sun close_time)
         |          |
         |          v
         |     Filter: discard current (incomplete) week  <-- CNDL-02
         |          |
         |          v
         |     Filter: discard weeks already stored as W1 <-- CNDL-03
         |          |
         |          v
         |     Aggregate: open=Mon.open, high=max(daily.high),
         |                low=min(daily.low), close=Sun.close, vol=sum(daily.vol)
         |          |
         |          v
         |     CandleRepository.findByAssetAndTimeframeAndCloseTime(asset, W1, weekCloseTime)
         |          |
         |      if absent --> save new Candle(W1)           INSERT path
         |      if present --> compare, update if changed   UPDATE path
         |          |
         v
[W1 candles persisted in candle table]
```

### Recommended Project Structure

New file locations:

```
backend/java/src/main/java/walshe/projectcolumbo/
├── rollup/
│   └── CandleRollupService.java          # NEW: core rollup logic
│
├── persistence/
│   └── model/
│       └── Timeframe.java                # MODIFY: add W1("1W")
│
└── (no other new packages for Phase 1)

backend/java/src/main/resources/db/migration/
└── V13__add_w1_timeframe.sql             # NEW: ALTER TYPE timeframe ADD VALUE 'W1'

backend/java/src/test/java/walshe/projectcolumbo/
└── rollup/
    ├── CandleRollupServiceTest.java       # NEW: unit tests (pure Java)
    └── CandleRollupIntegrationTest.java   # NEW: Testcontainers integration test
```

The `rollup` package mirrors the existing pattern where `ingestion`, `persistence`, and
`marketpulse` each own their concern in a top-level package.

### Pattern 1: Flyway Migration for PostgreSQL Enum Extension

**What:** Adding a new value to a live `ENUM` type without dropping and recreating it.

**When to use:** Any time a PostgreSQL native enum needs a new variant and existing rows must
be preserved.

**Critical constraint:** `ALTER TYPE ... ADD VALUE` cannot run inside a PostgreSQL transaction
(it is a DDL statement that commits immediately). Flyway wraps each migration in a transaction
by default. The solution is to mark the migration as non-transactional.

```sql
-- V13__add_w1_timeframe.sql
-- executeInTransaction = false  ← required comment or Flyway annotation
ALTER TYPE timeframe ADD VALUE IF NOT EXISTS 'W1';
```

To tell Flyway this migration must not run in a transaction, the project must either:

**Option A** (simplest — file naming convention with Flyway config): Add
`spring.flyway.mixed=true` to `application.properties` and use the standard SQL file — but
this weakens the safety of all other migrations.

**Option B** (recommended): Create the migration as a Java-based Flyway migration that calls
`ALTER TYPE timeframe ADD VALUE IF NOT EXISTS 'W1'` via JDBC with `Connection.setAutoCommit(true)`.

**Option C** (cleanest for a single SQL file): Flyway 9+ supports a `executeInTransaction`
flag per SQL file via a comment header:

```sql
-- flyway:executeInTransaction=false
ALTER TYPE timeframe ADD VALUE IF NOT EXISTS 'W1';
```

[ASSUMED] — The exact Flyway comment-header syntax for `executeInTransaction=false` needs
verification against the Flyway version bundled with Spring Boot 4.0.2. The flag exists in
Flyway Community and Teams editions, but the exact per-file override mechanism may require
using a Java migration class instead of a SQL file.

**Safe fallback (always works):** Java-based migration:

```java
// V13__add_w1_timeframe.java  (or named with __ convention)
@Component
public class V13__add_w1_timeframe implements JdbcMigration {
    @Override
    public void migrate(Connection connection) throws Exception {
        connection.setAutoCommit(true);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TYPE timeframe ADD VALUE IF NOT EXISTS 'W1'");
        }
    }
}
```

[ASSUMED] — Flyway's Java migration API (`JdbcMigration` or `BaseJavaMigration`) naming
conventions should be verified against the Flyway version in use.

### Pattern 2: Week Grouping with `java.time`

**What:** Group a sorted list of D1 candles into ISO weeks (Monday–Sunday UTC).

**When to use:** Whenever source candles must be folded into a coarser timeframe.

```java
// Source: java.time standard library (JDK 17) — [VERIFIED: JDK docs]
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

// Key: Monday 00:00:00 UTC for the week each candle belongs to
Map<OffsetDateTime, List<Candle>> byWeek = candles.stream()
    .collect(Collectors.groupingBy(
        c -> c.getOpenTime()
              .withOffsetSameInstant(ZoneOffset.UTC)
              .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
              .withHour(0).withMinute(0).withSecond(0).withNano(0),
        TreeMap::new,   // preserves chronological order
        Collectors.toList()
    ));
```

### Pattern 3: Incomplete-Week Guard

**What:** Reject a week group if its Sunday candle has not yet been finalized.

**When to use:** Before persisting any rollup output to prevent partial W1 candles.

```java
// A week is complete if it has exactly 7 D1 candles AND
// the last candle's close_time is before UTC midnight today.
// [VERIFIED: codebase — mirrors CandleFilters.finalizedBeforeUtcMidnightToday]
OffsetDateTime todayUtcMidnight = CandleFilters.utcMidnightToday(OffsetDateTime.now());
boolean isComplete = weekCandles.size() == 7
    && weekCandles.get(weekCandles.size() - 1).getCloseTime().isBefore(todayUtcMidnight);
```

**Note on "exactly 7":** If an asset has a missing daily candle for a week (exchange outage,
delisting), `size() == 7` will reject that week entirely. This is the safest default.
[ASSUMED] — Whether missing-day tolerance is required is not specified in requirements;
treating "missing day = incomplete week" is the most conservative approach and matches CNDL-02.

### Pattern 4: Idempotent Upsert (mirrors CandlePersistenceService)

**What:** Insert-or-update W1 candle using the existing UNIQUE constraint as the idempotency
key.

```java
// Idempotency key: (asset_id, timeframe='W1', close_time=Sunday 23:59:59 UTC)
// [VERIFIED: codebase — V1 migration: UNIQUE (asset_id, timeframe, close_time)]
candleRepository.findByAssetAndTimeframeAndCloseTime(asset, Timeframe.W1, weekCloseTime)
    .ifPresentOrElse(
        existing -> { if (hasChanged(existing, rolled)) { update(existing, rolled); save; stats.updated++; }
                      else stats.skipped++; },
        () -> { save(newCandle); stats.inserted++; }
    );
```

### Pattern 5: Incremental Detection

**What:** Skip weeks already stored as W1 to avoid redundant aggregation.

```java
// [VERIFIED: codebase — CandleRepository.findLatestCloseTime already exists]
Optional<Object> latestW1 = candleRepository.findLatestCloseTime(asset.getId(), Timeframe.W1.name());
OffsetDateTime lastStoredW1CloseTime = latestW1.map(obj -> {
    if (obj instanceof Instant i) return i.atOffset(ZoneOffset.UTC);
    return (OffsetDateTime) obj;
}).orElse(null);

// Only aggregate weeks with closeTime > lastStoredW1CloseTime
```

### Anti-Patterns to Avoid

- **Hardcoding D1 and W1 in `CandleRollupService`:** The service must accept `sourceTimeframe`
  and `targetTimeframe` as parameters per CNDL-04. No `Timeframe.D1` or `Timeframe.W1`
  literals in the core grouping/aggregation logic.
- **Using `Timeframe.D1` inside `CandlePersistenceService`:** The existing `mapToEntity`
  method hardcodes `Timeframe.D1`. Do NOT extend this method for rollup use — create a
  separate builder in `CandleRollupService` that accepts the target timeframe as a parameter.
- **Running the enum `ALTER TYPE` inside a Flyway transaction:** Will fail on PostgreSQL with
  `ERROR: ALTER TYPE ... ADD VALUE cannot run inside a transaction block`.
- **Grouping candles by `close_time` day-of-week instead of `open_time`:** D1 candles have
  `close_time` just before midnight (e.g., `23:59:59.999`). Grouping by close_time works but
  is less intuitive — grouping by `open_time` with `MONDAY` adjuster is clearer and maps to
  the "Monday open" definition.
- **Not sorting D1 candles before grouping:** The `open` of the W1 candle must be the `open`
  of the Monday candle. If candles are unsorted, `weekCandles.get(0).getOpen()` is wrong.
  Use `findByAssetAndTimeframeOrderByCloseTimeAsc` (already in `CandleRepository`).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Week-start calculation | Custom modulo arithmetic on epoch ms | `TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)` | Handles DST, UTC offset, leap years correctly |
| Upsert / idempotency | `ON CONFLICT DO UPDATE` native SQL | `findByAssetAndTimeframeAndCloseTime + save` | Existing pattern; Hibernate handles optimistic locking; avoids native query fragility |
| Flyway enum migration | Drop/recreate enum | `ALTER TYPE ... ADD VALUE IF NOT EXISTS` | Cannot drop types referenced by live columns |
| "Is week complete?" | Date range math from scratch | `size() == 7 && lastClose.isBefore(utcMidnight)` | Two-condition guard is sufficient; avoids clock dependency |

---

## Common Pitfalls

### Pitfall 1: Flyway Transaction Wrapping Breaks Enum Migration

**What goes wrong:** `ALTER TYPE timeframe ADD VALUE 'W1'` throws `ERROR: ALTER TYPE ... ADD
VALUE cannot run inside a transaction block` and the migration fails to apply.

**Why it happens:** Flyway wraps SQL migrations in a `BEGIN/COMMIT` block by default.
PostgreSQL `ALTER TYPE ... ADD VALUE` is not transactional.

**How to avoid:** Use a Java-based Flyway migration (`BaseJavaMigration`) that calls
`connection.setAutoCommit(true)` before executing the DDL, OR configure Flyway's per-file
`executeInTransaction=false` if the Flyway version supports it.

**Warning signs:** Migration fails at startup with `ALTER TYPE ... ADD VALUE cannot run inside
a transaction block`. The fix is never to retry the migration in a transaction — fix the
migration class.

### Pitfall 2: New Enum Value Not Visible After `ALTER TYPE`

**What goes wrong:** Hibernate / JPA does not recognize `W1` as a valid `Timeframe` even after
the migration runs, because the JPA `@Enumerated(EnumType.STRING)` on `Candle.timeframe` is
backed by the Java enum. If `Timeframe.W1` is not added to the Java enum, reads of W1 rows
will fail deserialization.

**Why it happens:** PostgreSQL enum and Java enum are decoupled. Both must be updated.

**How to avoid:** Add `W1("1W")` to `Timeframe.java` in the same commit as the migration.

**Warning signs:** `IllegalArgumentException: Unknown timeframe: W1` at runtime when fetching
W1 rows.

### Pitfall 3: `source` Field on Rolled-Up Candles

**What goes wrong:** The `Candle` entity has a `source` field typed as `MarketProvider` (not
nullable). Rolled-up W1 candles are derived, not fetched from a market provider. Setting
`source = MarketProvider.BINANCE` is misleading.

**Why it happens:** The DB schema uses `provider NOT NULL` for the `source` column with no
`'DERIVED'` variant.

**How to avoid:** Two options: (1) add `DERIVED` to the `provider` enum in a separate
migration and use it for rolled-up candles — cleanest semantically, but touches the provider
enum; (2) reuse `BINANCE` as the source (the source of the underlying D1 data) — simplest and
matches what the D1 candles already carry. Option 2 is the pragmatic choice for Phase 1 since
the rollup derives from BINANCE D1 data anyway.

[ASSUMED] — The preferred approach for `source` on derived candles is not specified. Option 2
(inherit source from D1 candles) is assumed as the default.

**Warning signs:** `NOT NULL constraint violation` on `source` column when inserting W1 candles.

### Pitfall 4: Week Close Time Definition

**What goes wrong:** The W1 candle's `close_time` must be consistent and used as the
idempotency key (`UNIQUE (asset_id, timeframe, close_time)`). If `close_time` is derived
differently on different runs (e.g., `Sun 23:59:59.999` vs `Mon 00:00:00.000 - 1ms`), the
unique constraint fails to deduplicate.

**Why it happens:** D1 candles have close_time just before midnight
(`open_time + 24h - 1ms`). The last D1 candle in the week defines the W1 close_time. As long
as the W1 `close_time` is taken directly from `weekCandles.last().getCloseTime()`, it is
stable. Do NOT recalculate it as `Monday + 7 days - 1ms`.

**How to avoid:** Always take `close_time` from the last D1 candle in the group, not from
calendar arithmetic.

### Pitfall 5: `CandleRepository.findLatestCloseTime` Returns `Object`, Not `OffsetDateTime`

**What goes wrong:** `findLatestCloseTime` is a native query returning `Optional<Object>`.
Direct cast to `OffsetDateTime` fails at runtime because JDBC returns a `java.sql.Timestamp`
or `java.time.Instant` depending on the driver.

**Why it happens:** Existing code pattern — see `CandleIngestionService.ingestForAsset` for
the correct handling.

**How to avoid:** Follow the existing pattern in `CandleIngestionService`:

```java
// [VERIFIED: codebase — CandleIngestionService.ingestForAsset]
.map(obj -> {
    if (obj instanceof Instant instant) return instant.atOffset(ZoneOffset.UTC);
    return (OffsetDateTime) obj;
})
```

---

## Code Examples

### Candle W1 Aggregation (core rollup logic)

```java
// Source: derived from existing CandlePersistenceService + CandleFilters patterns [VERIFIED: codebase]
private Candle aggregateWeek(Asset asset, List<Candle> weekCandles, Timeframe targetTimeframe) {
    // weekCandles MUST be sorted by closeTime ASC before calling this method
    Candle monday = weekCandles.get(0);
    Candle sunday = weekCandles.get(weekCandles.size() - 1);

    BigDecimal high = weekCandles.stream()
        .map(Candle::getHigh).max(BigDecimal::compareTo).orElseThrow();
    BigDecimal low = weekCandles.stream()
        .map(Candle::getLow).min(BigDecimal::compareTo).orElseThrow();
    BigDecimal volume = weekCandles.stream()
        .map(Candle::getVolume).reduce(BigDecimal.ZERO, BigDecimal::add);

    Candle w1 = new Candle();
    w1.setAsset(asset);
    w1.setTimeframe(targetTimeframe);
    w1.setOpenTime(monday.getOpenTime());
    w1.setCloseTime(sunday.getCloseTime()); // taken from data, not recalculated
    w1.setOpen(monday.getOpen());
    w1.setHigh(high);
    w1.setLow(low);
    w1.setClose(sunday.getClose());
    w1.setVolume(volume);
    w1.setSource(monday.getSource()); // inherit source from D1 candles
    return w1;
}
```

### Flyway Migration (safe form)

```java
// V13__add_w1_timeframe.java — Java-based migration avoids transaction wrapper issue
// [VERIFIED: Flyway supports BaseJavaMigration] [ASSUMED: exact class name per version]
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.Statement;

public class V13__add_w1_timeframe extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        // executeInTransaction defaults to false for Java migrations in Flyway 7+
        try (Statement stmt = context.getConnection().createStatement()) {
            stmt.execute("ALTER TYPE timeframe ADD VALUE IF NOT EXISTS 'W1'");
        }
    }

    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `Timeframe.D1` only | Add `W1("1W")` | All downstream code that iterates `Timeframe.values()` will automatically include W1 — audit callers to confirm none break |
| No rollup service | New `CandleRollupService` | Additive; D1 pipeline unchanged |

**Deprecated/outdated:**
- Nothing in scope is deprecated. The project is on Spring Boot 4.0.2 (very recent as of
  research date) — no legacy patterns to avoid.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `flyway:executeInTransaction=false` comment header syntax works for the Flyway version bundled with Spring Boot 4.0.2 | Pitfall 1, Code Examples | Migration fails at startup — use Java migration class as safe fallback |
| A2 | Java-based Flyway migration class `canExecuteInTransaction() = false` works with Spring Boot auto-configured Flyway | Code Examples | Migration runs in a transaction, ALTER TYPE fails — requires `spring.flyway.locations` to include the Java class location |
| A3 | W1 `source` field should inherit `MarketProvider` from the source D1 candles (BINANCE), not require a new `DERIVED` provider enum value | Pitfall 3, Code Examples | `source` column carries misleading provenance — may need a new migration for a `DERIVED` provider if semantic accuracy is required |
| A4 | "Exactly 7 D1 candles" is the completeness criterion (zero tolerance for missing days) | Pattern 3, CNDL-02 | Assets with exchange gaps (e.g., delisted mid-week, exchange outage) will permanently miss that W1 candle |
| A5 | `Timeframe.W1` string value should be `"1W"` (mirroring `D1("1D")` convention) | Standard Stack | `@JsonValue` / `@JsonCreator` on `Timeframe` will serialize/deserialize as `"1W"` — any API client expecting `"W1"` would need updating |

---

## Open Questions (RESOLVED)

1. **Flyway migration strategy for `executeInTransaction=false`** *(RESOLVED: plain SQL file)*
   - What we know: `ALTER TYPE ... ADD VALUE` cannot run in a transaction on PostgreSQL.
   - Resolution: Use plain SQL file mirroring V8 (`V8__add_unknown_to_trend_state_enum.sql`),
     which successfully added an enum value without any transaction directive. Flyway 11's
     PostgreSQL module handles this non-transactionally by default. Java-based migration class
     not required.

2. **`source` column for derived W1 candles** *(RESOLVED: inherit BINANCE from source candles)*
   - What we know: `source provider NOT NULL` — a `MarketProvider` enum value is required.
   - Resolution: Inherit `source` from the first D1 source candle in the week group (i.e.,
     `BINANCE`). No new `DERIVED` enum value added — Phase 1 simplicity wins (assumption A3).

3. **W1 `close_time` value for partial weeks with exactly 7 candles but Sunday missing**
     *(RESOLVED: exactly-7-candle completeness guard)*
   - Resolution: Plans use an "exactly 7 D1 candles per week" guard. If any day is missing,
     the week is considered incomplete and no W1 candle is emitted (CNDL-02).

---

## Environment Availability

Step 2.6: SKIPPED (no new external dependencies — all tools already in use by the project).

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Testcontainers integration tests | [ASSUMED: yes, existing tests pass] | — | — |
| Maven | Build + test | [ASSUMED: yes, existing CI] | — | — |
| PostgreSQL 16 | Testcontainers | postgres:16-alpine pulled by tests | 16-alpine | — |

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Spring Boot Test + Testcontainers + Mockito 5.14.2 |
| Config file | `pom.xml` (maven-surefire-plugin 3.5.2, jacoco 0.8.12) |
| Quick run command | `cd backend/java && mvn test -pl . -Dtest=CandleRollupServiceTest -q` |
| Full suite command | `cd backend/java && mvn test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CNDL-01 | D1 candles Mon–Sun produce W1 with correct O/H/L/C/V | unit | `mvn test -Dtest=CandleRollupServiceTest#rollup_producesCorrectOHLCV` | ❌ Wave 0 |
| CNDL-01 | open = Monday open, close = Sunday close | unit | `mvn test -Dtest=CandleRollupServiceTest#rollup_usesCorrectOpenClose` | ❌ Wave 0 |
| CNDL-02 | Incomplete current week produces no W1 candle | unit | `mvn test -Dtest=CandleRollupServiceTest#rollup_skipIncompleteWeek` | ❌ Wave 0 |
| CNDL-03 | Running rollup twice skips already-stored weeks | integration | `mvn test -Dtest=CandleRollupIntegrationTest#rollup_isIdempotent` | ❌ Wave 0 |
| CNDL-03 | Incremental: new D1 candles produce new W1, old ones skipped | integration | `mvn test -Dtest=CandleRollupIntegrationTest#rollup_isIncremental` | ❌ Wave 0 |
| CNDL-04 | Service works with arbitrary source/target Timeframe params | unit | `mvn test -Dtest=CandleRollupServiceTest#rollup_isParameterized` | ❌ Wave 0 |
| CNDL-05 | W1 is a valid DB enum value after migration | integration | `mvn test -Dtest=CandleRollupIntegrationTest#w1_timeframeExistsInDb` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `cd backend/java && mvn test -Dtest=CandleRollupServiceTest -q`
- **Per wave merge:** `cd backend/java && mvn test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/walshe/projectcolumbo/rollup/CandleRollupServiceTest.java` — unit tests for CNDL-01, CNDL-02, CNDL-04
- [ ] `src/test/java/walshe/projectcolumbo/rollup/CandleRollupIntegrationTest.java` — Testcontainers integration tests for CNDL-03, CNDL-05

---

## Security Domain

This phase is data-pipeline-internal with no new HTTP endpoints, user input, or authentication
changes. The applicable ASVS categories are limited:

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | — |
| V3 Session Management | No | — |
| V4 Access Control | No | — |
| V5 Input Validation | Minimal | Week boundary inputs are derived from DB timestamps, not user input |
| V6 Cryptography | No | — |

No new security surface is introduced in Phase 1.

---

## Sources

### Primary (HIGH confidence — codebase verified)

- `backend/java/src/main/java/walshe/projectcolumbo/persistence/model/Timeframe.java` — exact current enum structure
- `backend/java/src/main/resources/db/migration/V1__create_asset_timeframe_candle.sql` — DB schema, unique constraint, timeframe enum definition
- `backend/java/src/main/resources/db/migration/V6__create_ingestion_run.sql` — ingestion_run table schema
- `backend/java/src/main/java/walshe/projectcolumbo/ingestion/CandlePersistenceService.java` — upsert pattern
- `backend/java/src/main/java/walshe/projectcolumbo/persistence/service/CandleFilters.java` — finalization boundary idiom
- `backend/java/src/main/java/walshe/projectcolumbo/persistence/service/SuperTrendService.java` — processAllActiveAssets pattern
- `backend/java/src/main/java/walshe/projectcolumbo/persistence/repository/CandleRepository.java` — available queries
- `backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineService.java` — pipeline phase ordering
- `backend/java/pom.xml` — full dependency inventory, Spring Boot 4.0.2, JUnit 5, Testcontainers

### Secondary (MEDIUM confidence — PostgreSQL and Flyway knowledge)

- PostgreSQL documentation: `ALTER TYPE ... ADD VALUE` behavior outside transactions [ASSUMED: Postgres 16]
- Flyway `BaseJavaMigration.canExecuteInTransaction()` — documented in Flyway API; applicability to the exact version bundled with Spring Boot 4.0.2 is [ASSUMED]

### Tertiary (LOW confidence)

- None. All critical decisions are grounded in the existing codebase.

---

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — all libraries read directly from pom.xml
- Architecture: HIGH — directly mirrors existing patterns in the codebase
- Pitfalls: HIGH (Pitfall 1, 2, 4, 5) / MEDIUM (Pitfall 3) — codebase-verified except for Flyway transaction flag syntax
- Validation Architecture: HIGH — mirrors existing test structure

**Research date:** 2026-05-20
**Valid until:** 2026-06-20 (stable domain — Spring Boot 4.0.2, PostgreSQL 16, no fast-moving external services)
