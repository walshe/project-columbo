## 1. Schema

- [x] 1.1 Add `V18__add_tradingview_ref.sql`: `ALTER TABLE asset ADD COLUMN tradingview_ref VARCHAR` (nullable).
- [x] 1.2 Verify all 47 `V17`-seeded Tiingo tickers against TradingView's own symbol search (not Tiingo's `exchangeCode` metadata, and not general knowledge) to get each one's exact `EXCHANGE:SYMBOL` reference, watching for symbol-format mismatches (e.g. `BRK-A` vs. `BRK.A`).
- [x] 1.3 Add `V19__seed_tiingo_tradingview_refs.sql`: `UPDATE` the 47 rows with their verified refs, keyed by `(symbol, provider = 'TIINGO')`.

## 2. Domain model

- [x] 2.1 Add `tradingviewRef` field to the `Asset` record; update its Javadoc to explain why it's not derived from `symbol` at read time.
- [x] 2.2 Update `AssetDao`'s `findAllActive` SELECT + row mapper to read `tradingview_ref`.

## 3. Chart link generation

- [x] 3.1 `TradingViewUrl.generateUrl`: add a `tradingviewRef` parameter; for `AssetVenue.EXCHANGE`, build the chart URL directly from it when present, return `null` when absent. `SPOT`/`FUTURES` construction unchanged.
- [x] 3.2 Update `SignalQueryService`'s call site to pass `asset.tradingviewRef()`.
- [x] 3.3 Update `TradingViewUrlTest` for the new parameter across every existing call site; add cases for: verified ref used for `EXCHANGE`, absent ref still `null` for `EXCHANGE`, `SPOT`/`FUTURES` unaffected by a (nonsensical) ref being passed.
- [x] 3.4 Update every other `Asset` constructor call site in tests (`AssetTest`, `SignalQueryServiceTest`) for the new trailing field; add an `AssetTest` case proving `tradingviewRef` is optional.

- [x] 3.5 Add a `PersistenceIntegrationTest` case asserting all 47 Tiingo assets have a non-null `tradingviewRef`, with exact-value spot-checks for the trickiest ones (`BRK-A`, `SSNLF`, `601398`) — guards against a typo in `V19`'s VALUES list silently matching zero rows for a symbol (an `UPDATE` with no matching `WHERE` rows doesn't error).

- [x] 3.6 Self-review via `pr_agent` on PR #68: flagged that `generateUrl` only null-checked `tradingviewRef`, not blank — a future asset seeded with an empty string would produce a dead link (`?symbol=&interval=1D`). Fixed to treat blank the same as null.

## 4. Verification

- [x] 4.1 Run full `mvn test` suite, confirm no regressions.
- [x] 4.2 Run `mvn verify -Pe2e`, confirm no regressions (the e2e stub assets have no `tradingview_ref` seeded, so this only proves the new column/field don't break anything for `SPOT`/`FUTURES`/`EXCHANGE` assets without one). Passed.
- [ ] 4.3 After deploy, spot-check `/api/v1/signals` for a couple of the 47 Tiingo assets (e.g. `AAPL`, `SSNLF`, `601398`) and confirm `tradingviewUrl` resolves to a real, correct TradingView chart.
