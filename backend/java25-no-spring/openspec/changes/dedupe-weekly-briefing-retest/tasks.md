## 1. De-duplicate

- [x] 1.1 Remove "Bullish/Bearish Retest" rendering from `WeeklyTrendBriefingFormatter` (confirmed it duplicated `WeeklyPullbackBriefingFormatter`'s headline data byte-for-byte - same `TrendAlignmentService` call, same params)
- [x] 1.2 Confirm no test asserts on the removed section text (`WeeklyBriefingHandlerIntegrationTest` only checks 200 + non-blank body)
- [x] 1.3 Confirm README already documents the target end-state (it only ever described Confluence + Flips Forming for `/weekly-trend-briefing`) - no doc changes needed
- [x] 1.4 Full non-e2e suite green (270 tests)

## 2. Frontend follow-up (Monty's Markets, separate app - not part of this backend change)

- [ ] 2.1 Update "Weekly Trend & Regime" / "Weekly Pullback & Retest" section copy to reflect that retest data now lives exclusively in the pullback section
