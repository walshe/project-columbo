---
plan: "04-01"
phase: 04-multi-timeframe-scan
status: complete
completed: 2026-05-24
---

# Summary: Plan 04-01 — Input DTO Evolution

## What was done

Added an optional per-condition `timeframe` field to `ScanCondition` (as its first record component, nullable, no @NotNull) and removed `@NotNull` from `ScanRequest.timeframe` to make the top-level timeframe optional. Also updated the `@Schema` description on `ScanRequest.timeframe` to reflect its new role as a fallback default.

An unplanned fix was required: `SummaryService.java` (a main-source file) constructs `ScanCondition` using positional constructor args and needed `null` prepended as the new first argument at all four call sites. This was necessary to satisfy the "main sources compile cleanly" acceptance criterion.

## Files modified

- `backend/java/src/main/java/walshe/projectcolumbo/api/v1/scan/dto/ScanCondition.java` — added `Timeframe timeframe` as first component with `@Schema` annotation; added `import walshe.projectcolumbo.persistence.model.Timeframe`
- `backend/java/src/main/java/walshe/projectcolumbo/api/v1/scan/dto/ScanRequest.java` — removed `@NotNull(message = "timeframe is required")` from `timeframe`; updated `@Schema` description
- `backend/java/src/main/java/walshe/projectcolumbo/api/v1/summary/SummaryService.java` — added `null` as first arg to all four `new ScanCondition(...)` positional constructor calls

## Verification

```
cd backend/java && ./mvnw compile -q
EXIT: 0
```

Main sources compile cleanly with no errors.

## Requirements satisfied

SCAN-01, SCAN-02, SCAN-04
