## 1. Enum

- [x] 1.1 Add `PCT_CHANGE_ASC` and `PCT_CHANGE_DESC` constants to `SignalSort`, with a comment documenting the trend-relative convention (DESC for bullish lists, ASC for bearish) and nulls-last behaviour

## 2. Service

- [x] 2.1 Add `PCT_CHANGE_ASC` and `PCT_CHANGE_DESC` cases to the sort `switch` in `SignalQueryService.listSignals`, using `Comparator.comparing(SignalStateDto::pctChangeSinceFlip, Comparator.nullsLast(...))` (natural order for ASC, reverse for DESC)

## 3. Tests

- [x] 3.1 Add a `SignalQueryServiceTest` case covering `PCT_CHANGE_DESC` (largest gain first) and `PCT_CHANGE_ASC` (largest drop first), including an asset with `null` pctChangeSinceFlip asserted to sort last in both directions
- [x] 3.2 Run the affected test suite and confirm green
