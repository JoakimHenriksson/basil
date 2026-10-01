# Basil shared contracts

Kotlin Multiplatform DTOs in [Models.kt](src/commonMain/kotlin/app/basil/contracts/Models.kt)
are used directly by the backend and shared mobile app. JSON quantities are
decimal strings; units in this first slice are grams, millilitres, or pieces.
Ambiguous ingredient amounts remain null with a review indicator.

Current HTTP operations:

- `GET /health`
- `GET /v1/recipes` and `POST /v1/recipes/import`
- `POST /v1/recipes/{id}/preview` and `PUT /v1/recipes/{id}/yield`
- `GET /v1/pantry`, `POST /v1/pantry`, and `DELETE /v1/pantry/{id}`

Domain and HTTP tests verify the shared payloads, scaling, and pantry shortages.
This prototype API is local and unauthenticated. Household IDs, membership,
record versions, stock operation IDs, money, AI drafts, and synchronization
contracts are added with their corresponding slices.

See the [product plan](../docs/app-plan.md) for those planned operations.
