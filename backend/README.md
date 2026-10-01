# Basil backend

Planned Kotlin service using Ktor, PostgreSQL, Flyway, and the Exposed SQL DSL.
Use Gradle Kotlin DSL and a pinned wrapper when scaffolding the service.

The backend owns shared households and invitations, preserved and normalized
recipes, remembered barcodes and package sizes, stock movements, matching,
missing lists, purchases, and budgets. It calls Gemini and validates imported
or AI-assisted drafts, including results from local Apple inference.

Barcode additions and receipt imports must reconcile so the same package is
not added twice. Recipe costs distinguish ingredient portions from whole
packages bought. Recipe-site imports retain usable content after a site disappears.

Start as one deployable service with internal modules for households, recipes,
imports, stock, shopping, budgets, and cloud AI. Keep pure quantity and money rules
separate from HTTP and persistence. Maintain the external API in
[`contracts/`](../contracts/README.md).

See the [app plan](../docs/app-plan.md) for the proposed data model, invariants,
endpoints, and implementation milestones. No backend has been scaffolded yet.
