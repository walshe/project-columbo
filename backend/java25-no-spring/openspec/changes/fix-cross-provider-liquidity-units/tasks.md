## 1. Ingestion

- [x] 1.1 `TiingoMarketDataProvider.toCandle`: store `volume` as `adjVolume * adjClose` (dollar-notional) instead of raw `adjVolume`.
- [x] 1.2 Update `TiingoMarketDataProviderTest`'s existing volume assertion for the new value; add a dedicated test isolating the unit-conversion itself (distinct shares/price inputs, asserting the multiplied dollar figure).

## 2. Backfill

- [x] 2.1 Add `V21__backfill_tiingo_dollar_volume.sql`: one-time `UPDATE candle SET volume = volume * close` for every `TIINGO`-provider asset's candles, using each row's own already-adjusted stored `close`.

## 3. Verification

- [x] 3.1 Run full `mvn test` suite, confirm no regressions. Passed: 258 tests.
- [x] 3.2 Run `mvn verify -Pe2e` locally: inconclusive due to environment load. `PipelineEndToEndIT` failed/hung with `HikariPool - Connection is not available ... (total=10, active=10, idle=0, waiting=194+)` cascading across both Binance-tokenized assets (`AAOIUSDT`, `AMDUSDT`, `GMEUSDT`, `DAIUSDT`, `SKHYNIXUSDT`, ...) and a Tiingo asset (`MA`) - a real regression in the Tiingo-only volume change could not affect Binance assets at all, so this is host resource contention (confirmed separately: ~66-427MB free host RAM, ~15 unrelated Docker containers already running) exhausting the pool under the pipeline's ~247-virtual-thread fan-out against a 10-connection pool, not a code defect. Deferring to GitHub Actions' clean e2e runner as the authoritative check for this PR.
- [ ] 3.3 Self-review before opening the PR.
- [ ] 3.4 After deploy: spot-check `v_asset_liquidity`/a weekly briefing to confirm at least one Tiingo asset now appears in a liquidity-gated scan section where warranted.
