# Basil shared contracts

Kotlin Multiplatform DTOs in [Models.kt](src/commonMain/kotlin/app/basil/contracts/Models.kt)
are used directly by the backend and shared mobile app. JSON quantities are
decimal strings; units in this first slice are grams, millilitres, or pieces.
Ambiguous ingredient amounts remain null with a review indicator.

Current HTTP operations:

- `GET /health`
- `GET /v1/recipes` and `POST /v1/recipes/import`
- `POST /v1/recipes/capture` and `GET /v1/media/{name}`
- `POST /v1/recipes/{id}/preview` and `PUT /v1/recipes/{id}/yield`
- `GET /v1/pantry`, `POST /v1/pantry`, and `DELETE /v1/pantry/{id}`

Domain and HTTP tests verify the shared payloads, scaling, and pantry shortages.
The running prototype uses local device sessions and scoped households. Domain
test fixtures can construct an unauthenticated application explicitly. Household IDs, membership,
record versions, stock operation IDs, money, AI drafts, and synchronization
contracts are added with their corresponding slices.

See the [product plan](../docs/app-plan.md) for those planned operations.

Recipes include an optional retained `imagePath` and `originalImageUrl`. The
original recipe URL is always `sourceUrl`. New fields have defaults so older
vaults remain readable. Browser capture payloads are not persisted.

## Library and shopping operations

- `POST /v1/recipes`, `PUT /v1/recipes/{id}`, `DELETE /v1/recipes/{id}`
- `PUT /v1/recipes/{id}/favourite`, `GET /v1/recipes/{id}/history`
- `GET /v1/recipes/export` returns a ZIP of current recipes and retained photos.
- `GET /v1/shopping` returns selections, combined items, warnings, and a version.
- `POST /v1/shopping/selections` pins the selected recipe revision and servings.
- `PUT /v1/shopping/selections/{id}` changes servings; `DELETE` removes the meal.
- `POST /v1/shopping/items` adds a manual item.
- `PUT /v1/shopping/items/{id}` checks/unchecks an item; `DELETE` removes a manual item.

See [Shopping.kt](src/commonMain/kotlin/app/basil/contracts/Shopping.kt).
Shopping mutations carry `expectedVersion` from the latest list. A stale version
returns HTTP 409. Create requests include client-generated IDs; identical retries
do not duplicate meals or manual items. Recipe edits use `expectedRevision`.
No shopping operation mutates pantry stock. Selected snapshots survive library
edits/deletion; removing a meal explicitly releases its shopping requirements.

## Products and stock

[Pantry.kt](src/commonMain/kotlin/app/basil/contracts/Pantry.kt) defines products
and recorded movements. Barcodes normalize to check-digit-validated GTIN-14 keys.

- `GET /v1/products`, `GET /v1/products/{barcode}` (lookup draft)
- `PUT /v1/products/{barcode}` confirms ingredient/package mapping.
- `POST /v1/stock` records Add or Use; `GET /v1/stock/movements` lists the audit.
- `POST /v1/stock/{id}/undo` records a compensating change.
- `POST /v1/recipes/{id}/cook` logs requirements with explicitly reconciled Use IDs.

Operation IDs deduplicate identical retries. Negative balances are rejected.
Open Food Facts lookup is a review draft, not an automatic inventory mutation.
Product mappings cannot change dimensions while existing stock uses them.

## Household sessions and synchronization

[Households.kt](src/commonMain/kotlin/app/basil/contracts/Households.kt) defines
local device sessions, membership and snapshots. `POST /session` creates a
private household. Every `/v1` call requires a Bearer token and `X-Basil-Household`.
Tokens are stored as hashes on the server. `GET /household` lists members;
`POST /household/invites` creates a one-use invitation with a 24-hour expiry.
`POST /households/join` accepts an invitation. Owners can delete invitations
and non-owner members through `/household/invites/{id}` and `/household/members/{id}`.
`GET /v1/sync?since={version}` returns one atomic household snapshot, or 204
when unchanged. Snapshots include recipes, stock, shopping, products and movements.
Queued stock requests retain their operation IDs. Shopping check-off replays
only against the same requirement; meaningful conflicts remain visible.
