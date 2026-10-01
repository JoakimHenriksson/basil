# Basil Kotlin backend

Ktor service with recipe-page import, durable local recipe storage, simple pantry
entry, serving scaling, and missing-ingredient calculations. Quantities use
decimal arithmetic; original recipe text and instructions are preserved.

From the worktree root:

```sh
./gradlew :backend:run
./gradlew :backend:test
```

Use `gradlew.bat` on Windows. The root README documents this machine's HTTPS
certificate configuration. Settings are `BASIL_PORT` (default 8787),
`BASIL_HOST` (default 127.0.0.1), and `BASIL_DATA` (default data/vault.json,
relative to the backend directory).

The vault writes a temporary file and atomically replaces the saved state.
Duplicate imports of the same final source URL reuse the saved recipe.
A missing source page does not affect previously stored content.

Tests cover structured and blog imports, fractions, unknown units, stock
allocation, persistence, private-address rejection, and the HTTP recipe flow.

See [API contracts](../contracts/README.md) and the [product plan](../docs/app-plan.md).
This slice has no authentication or shared-household persistence. PostgreSQL,
migrations, identities, and the remaining features are future implementation.
