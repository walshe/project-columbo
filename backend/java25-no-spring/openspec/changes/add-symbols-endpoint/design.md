# Design: Add /symbols endpoint

## Context
Columbo is an internal microservice — no public exposure, no auth required. The endpoint is a simple list projection over active assets.

## Approach
`GET /api/v1/symbols` → `SymbolsHandler` → `AssetDao.findAllActive()` → sorted `List<String>` → `ctx.json(...)`.

No new DAO method needed — `findAllActive()` already exists. No new DB query or migration.

## Implementation notes
- `SymbolsHandler` follows the same pattern as all other handlers: constructor injection, `.register(Javalin)`.
- `@OpenApi` annotation documents the response as `String[]` for the generated Swagger spec.
- The handler is registered first in `Main.java` (before `SignalsHandler`) since it has no dependencies on services.
