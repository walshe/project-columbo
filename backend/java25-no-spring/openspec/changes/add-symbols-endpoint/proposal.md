# Proposal: Add /symbols endpoint

## What
Add `GET /api/v1/symbols` — returns a sorted JSON array of every active asset symbol (e.g. `["BTCUSDT","ETHUSDT",...]`). No query parameters. No auth — the API is an internal microservice.

## Why
Monty (the AI chat agent consuming this API) needs a cheap discovery call to know which symbols are available before fetching candles or signals. Without this, Monty has to infer symbols from `/signals` responses, which requires a full signal fetch.

## Capabilities
- `symbol-discovery`: A dedicated endpoint returns all active symbols as a sorted string array.

## Implementation touches
- `SymbolsHandler.java` — new handler with `@OpenApi` annotation; delegates to `AssetDao.findAllActive()`.
- `Main.java` — wire `SymbolsHandler` alongside other handlers.
- `SymbolsHandlerIntegrationTest.java` — covers sorted results, inactive exclusion, and empty list.
