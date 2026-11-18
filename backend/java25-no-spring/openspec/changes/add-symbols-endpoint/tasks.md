## 1. SymbolsHandler

- [x] 1.1 Create `SymbolsHandler.java` with `GET /api/v1/symbols`; inject `AssetDao`; return `assetDao.findAllActive()` mapped to symbols, sorted ascending, as a `List<String>` via `ctx.json(...)`
- [x] 1.2 Add `@OpenApi` annotation: summary, description, and `200` response documented as a JSON array of strings

## 2. Wire into Main

- [x] 2.1 Instantiate `SymbolsHandler` in `Main.java` and call `.register(app)` alongside the other handlers

## 3. Test coverage

- [x] 3.1 Add an integration test for `SymbolsHandler` asserting: active symbols are returned sorted, inactive symbols are excluded, empty list when no active assets
