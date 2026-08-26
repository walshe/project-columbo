## 1. Fix

- [x] 1.1 Replace `Collectors.toMap`-based `regimePulses` construction with a plain loop into a `LinkedHashMap` in `WeeklyTrendBriefingHandler.buildReport`
- [x] 1.2 Apply the identical fix to `WeeklyPullbackBriefingHandler.buildReport`

## 2. Tests

- [x] 2.1 Add `WeeklyBriefingHandlerIntegrationTest` seeding only a CRYPTO asset (no STOCK/ETF) and asserting both endpoints return 200
- [x] 2.2 Verify the test fails against the pre-fix code (via `git stash`) and passes with the fix restored
- [x] 2.3 Run the full test suite to confirm no regressions
