# Basil

Basil helps a busy household choose food, keep favourite recipes, scale servings,
and shop for what is missing. Barcode Add and Use actions keep pantry tracking
quick; recipe costs and scanned receipts connect food with the grocery budget.
The project is currently at the planning stage.

The proposed architecture uses a Kotlin backend, a native Android app in Kotlin
and Jetpack Compose, and a native iOS app in Swift and SwiftUI. Gemini runs through
the backend; Apple Intelligence is an option on supported iPhones, with Gemini
also available. Household sharing and recipe-site preservation are core scope.

- [App plan](docs/app-plan.md): the food flow, recipe imports, scanning, household
  sharing, costs, AI, and delivery milestones.
- [Development workflow](docs/development.md): worktree layout and conventions
  copied from `~/src/cogitatibus`.
- [Backend](backend/README.md), [Android](android/README.md),
  [iOS](ios/README.md), and [API contracts](contracts/README.md): component scopes.

Work in a branch worktree such as `feature/app-plan/`. The `main/` checkout is
reserved for reference, and the user handles integration into `main`.
