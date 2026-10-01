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

## Browser captures and photos

`POST /v1/recipes/capture` accepts `CaptureRequest(url, html)` from the rendered
browser page. It does not re-fetch the recipe page. Structured recipes take
priority, followed by configured Gemini extraction or deterministic text import.
Captured HTML is bounded to 2 MB and is processed transiently.

Photos are copied with bounded public-only HTTPS requests (5 MB maximum,
JPEG/PNG/WebP). `GET /v1/media/{name}` serves the retained photo. Storage uses
content-hashed filenames, atomic writes, and a directory beside the JSON vault.
Photo failure never discards a successfully extracted recipe.

Configure `GEMINI_API_KEY` and optionally `BASIL_GEMINI_MODEL` in the backend
environment. Gemini receives relevant page text, excluding forms and scripts.
It produces a validated draft; no AI arithmetic or stock operations are used.
API keys are passed in headers and provider response bodies are not logged.
