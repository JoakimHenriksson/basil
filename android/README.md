# Basil Android app

Planned native Android application using Kotlin, Jetpack Compose, coroutines,
Room for local storage, WorkManager for queued synchronization, and Hilt for
dependency injection. Pin the Android and Kotlin toolchain at implementation.

Recipes lead the app: share a recipe-site link, keep a normalized copy, choose
servings, and get a missing list. Add a camera scanner with visible Add and Use
modes, remembered barcodes, partial quantities, and Undo. Support recipe and
receipt scans, household sharing, and Gemini-assisted suggestions.

Read the versioned backend API and cache recipes, known products, pantry, and
shopping data. Queue known-product scans and shopping changes offline and show
pending changes and synchronization conflicts explicitly. Meal dates are optional.

Keep presentation and local storage in the Android project. The Kotlin backend
owns recipe matching and financial calculations. Store authentication tokens
using Android Keystore-backed storage and call Gemini through the backend.

See the [app plan](../docs/app-plan.md) and [API contract scope](../contracts/README.md).
No Android application has been scaffolded yet.
