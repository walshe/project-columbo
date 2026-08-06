## 1. Domain model

- [x] 1.1 Add `ScanSort` enum (`SYMBOL_ASC`, `LIQUIDITY_DESC`) to the `signal` package, mirroring `SignalSort`'s shape and javadoc rationale for descending-only.
- [x] 1.2 Add `avgVolume7d` (`BigDecimal`) to `ScanResult`; add `Objects.requireNonNull` in its compact constructor.
- [x] 1.3 Add optional `sort` (`ScanSort`) to `ScanRequest`.

## 2. Scan service

- [x] 2.1 `ScanService.matchesForCondition`: populate an `avgVolumeBySymbol` map (`Map<String, BigDecimal>`) the same way `assetClassBySymbol` is already populated, reading `candidate.avgVolume7d()`.
- [x] 2.2 `ScanService.combine`: pass `avgVolumeBySymbol.get(symbol)` into each `ScanResult`; branch the existing `.sorted(...)` on `request.sort()` (`null`/`SYMBOL_ASC` → today's symbol-ascending comparator, `LIQUIDITY_DESC` → `Comparator.comparing(ScanResult::avgVolume7d).reversed()`), applied before `limit` truncation (unchanged position).

## 3. HTTP layer

- [x] 3.1 Register `ScanSort` with Javalin's enum query/body validation the same way `SignalSort` is registered in `ApiServer`, if required for request-body deserialization (confirm whether Jackson needs this or handles enums in request bodies without registration - `ScanRequest` is deserialized via `ctx.bodyAsClass`, not a query param, so check whether this step is actually needed before doing it).
- [x] 3.2 Update `ScanHandler`'s `@OpenApi` docs (`ScanRequest`/`ScanResponse` referenced schemas) to reflect the new fields.

## 4. Tests

- [x] 4.1 `ScanServiceTest` (pure `combine`): add cases for `avgVolume7d` appearing once per matched asset (not per condition) on a multi-condition AND match; add cases for `LIQUIDITY_DESC` sort ordering and for it combined with `limit`; confirm `SYMBOL_ASC`/null sort is unchanged from current passing tests.
- [x] 4.2 `ScanServiceIntegrationTest`: add a case proving `avgVolume7d` flows through from real seeded liquidity data.
- [x] 4.3 `ScanHandlerIntegrationTest`: add a case posting `sort=LIQUIDITY_DESC` and asserting response ordering; confirm existing tests (no `sort` field) still pass unchanged.

## 5. Docs

- [x] 5.1 `README.md`: update the `/scan` endpoint row (or add a note) documenting the new `sort` request field and `avgVolume7d` response field.

## 6. Verification

- [x] 6.1 Run full `mvn test` suite, confirm no regressions.
- [ ] 6.2 Self-review via the `java-code-review` skill before opening the PR.
