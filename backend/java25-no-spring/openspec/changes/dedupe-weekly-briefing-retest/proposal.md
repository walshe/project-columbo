## Why

The user noticed the two weekly briefing endpoints might be reporting "the same stuff, slightly different wording." Comparing them confirmed it for one specific section: `WeeklyTrendBriefingFormatter`'s "Bullish/Bearish Retest" and `WeeklyPullbackBriefingFormatter`'s "Bullish Pullback/Bearish Bounce" both render `TrendAlignmentService.computeAlignment(7, assetClass).bullishRetest()`/`.bearishRetest()` - the exact same query, same parameters, same candidate list - under a different section title. This isn't "different wording for the same idea," it's literally the same data computed and shown twice. Everything else in both reports (Confluence, each report's own Scan list, Flips Forming, the pullback briefing's `watch:` annotations) is genuinely distinct. The README's own documentation of `/weekly-trend-briefing` already only describes Confluence and Flips Forming as its headline content - it never mentions Retest - so the code had drifted from its own docs.

This also unblocks a frontend clarity fix: Monty's Markets renders both reports together under "Weekly Trend & Regime" / "Weekly Pullback & Retest" sections, and having the same list appear under both was the exact confusion the user was trying to design away.

## What Changes

- `WeeklyTrendBriefingFormatter` no longer renders "Bullish Retest" / "Bearish Retest". The trend briefing keeps Confluence, its own confirming-move Scan list, and Flips Forming - a single "what's already confirmed, or about to be" thesis.
- `WeeklyPullbackBriefingFormatter` is unchanged - it already exclusively owns the retest data going forward, with its `watch:` provisional-divergence annotations intact.
- No change to `TrendAlignmentService`/`TrendAlignment` - both are still shared with the unrelated `/api/v1/summary/trend-alignment` endpoint, which legitimately shows retest as its own feature and is out of scope here.
- No README changes needed - the existing prose already only described Confluence + Flips Forming for `/weekly-trend-briefing`; removing the code's Retest section brings the code back in line with docs that were already correct.

## Capabilities

### New Capabilities
(none tracked as formal specs yet for the weekly briefing endpoints beyond the delta captured here - see specs/, matching how `fix-weekly-briefing-empty-asset-class-npe` captured its own `weekly-briefing` delta the same way)

### Modified Capabilities
- `weekly-briefing`: the trend briefing's headline content no longer includes retest candidates.

## Impact

- `WeeklyTrendBriefingFormatter.java` only - no handler, DTO, or service changes (the handler still computes the full `TrendAlignment` for confluence's sake; the formatter simply stops rendering two of its four fields).
- No test changes needed - the existing `WeeklyBriefingHandlerIntegrationTest` only asserts 200 + non-blank body, not section content.
- Frontend follow-up (Monty's Markets, separate app): once this ships, "Weekly Pullback & Retest" becomes the sole source of retest/pullback data, matching its name; "Weekly Trend & Regime" no longer needs a caveat about overlapping with the other section.
