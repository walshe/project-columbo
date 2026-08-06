## 1. Schema

- [x] 1.1 Add `V14__add_asset_venue.sql`: `CREATE TYPE asset_venue AS ENUM ('SPOT', 'FUTURES')`; add `venue asset_venue NOT NULL DEFAULT 'SPOT'` to `asset`; `UPDATE` venue to `FUTURES` for `asset_class IN ('STOCK','ETF','COMMODITY')` and for symbols `HYPEUSDT`/`CCUSDT`/`MUSDT`/`KASUSDT`; re-activate (`active = true`) any of those same rows that are currently inactive.

## 2. Domain model

- [x] 2.1 Add `AssetVenue` enum (`SPOT`, `FUTURES`) to `walshe.projectcolumbo.supertrend.shared`.
- [x] 2.2 Add `venue` field to the `Asset` record with `Objects.requireNonNull`.
- [x] 2.3 Update `AssetTest` for the new constructor param / null-check.

## 3. Persistence

- [x] 3.1 `AssetDao`'s row mapper reads `venue`; `findAllActive`/`findAllActive(AssetClass)` SELECTs include the column.
- [x] 3.2 Update `AssetDaoIntegrationTest` seed helper and assertions for the new column.

## 4. Market data provider

- [x] 4.1 `BinanceMarketDataProvider`: make base URL *and* klines path venue-derived — spot defaults to `https://api.binance.com` + `/api/v3/klines`, futures to `https://fapi.binance.com` + `/fapi/v1/klines`. Keep the constructor override hook for tests, now per-venue.
- [x] 4.2 Update `BinanceMarketDataProviderTest` for both venues' default URI construction and the trailing-slash-stripping behavior on each.

## 5. Ingestion routing

- [x] 5.1 `CandleIngestionService`: replace the single `MarketDataProvider provider` constructor param with `Map<AssetVenue, MarketDataProvider> providersByVenue`; resolve the provider per asset via `asset.venue()` in `ingestForAsset`.
- [x] 5.2 Update `CandleIngestionServiceTest`'s `FakeMarketDataProvider`/`ingestionService(...)` helper for the map-based constructor; add a case proving a `FUTURES`-venue asset routes to the futures fake and a `SPOT`-venue asset to the spot fake in the same run.

## 6. Composition root & config

- [x] 6.1 `Main.java`: construct two `BinanceMarketDataProvider` instances (spot, futures) from two new env vars (`SUPERTREND_BINANCE_SPOT_BASE_URL`, `SUPERTREND_BINANCE_FUTURES_BASE_URL`, replacing `SUPERTREND_BINANCE_BASE_URL`), wire both into `CandleIngestionService`'s venue map.

## 7. E2E test

- [x] 7.1 `PipelineEndToEndIT`: add WireMock stub mappings under `/fapi/v1/klines` (mirroring the existing `/api/v3/klines` ones), set both new env vars to point at the same WireMock instance.
- [x] 7.2 Add an assertion proving a real `FUTURES`-venue asset (e.g. one of the seeded stock symbols) actually ingests and produces signals, not just that `assetCount` is a particular number — the old single-endpoint stub setup let this exact bug through undetected.
- [x] 7.3 Update the `assetCount`/error-count assertions for the now-corrected active/inactive split (fewer invalid-symbol deactivations than before this fix, once the 4 crypto + 140 stock/ETF assets stop being wrongly deactivated).

## 8. Docs

- [x] 8.1 `README.md`: replace `SUPERTREND_BINANCE_BASE_URL` in the env var table with the two new variables; note venue routing in the HTTP/ingestion description if relevant.
- [x] 8.2 `developer-notes.md`: document the spot-vs-futures host+path distinction as a "known gotcha" (this is exactly the kind of thing that section exists for), and update the `ingestion`/`shared` package-tour bullets for `AssetVenue`.

## 9. Verification

- [x] 9.1 Run full `mvn test` suite, confirm no regressions.
- [x] 9.2 Run `mvn verify -Pe2e`, confirm the new futures-routing assertion passes.
- [x] 9.3 Self-review via the `java-code-review` skill before opening the PR.
