# Basil

Pantry tracker, recipe keeper, shopping planner, and grocery budget tracker.
Kotlin backend, native Android and iOS frontends, Gemini, and Apple Intelligence.

## Worktree workflow

**Always work in a git worktree.** Never check out, edit, merge, or commit
directly in `main/`. Before making a change, create a worktree on a new branch
so its on-disk path mirrors the branch name.

Run from the bare repository root, `~/src/basil`:

```sh
git worktree add -b <hierarchy>/<name> <hierarchy>/<name> main
```

Hierarchies:

- `bugfix/<name>` for bug fixes.
- `feature/<name>` for features and planning.
- `release/<name>` for release preparation.

The `main/` worktree tracks `main` and is read-only for reference.
The user owns all integration to `main`.

For an empty repository with no initial commit, create the first worktree
with `git worktree add --orphan -b feature/app-plan feature/app-plan`.
The normal command becomes available after the user establishes `main`.

## Project boundaries

- `backend/`: Kotlin service and authoritative business rules.
- `android/`: Kotlin and Jetpack Compose Android app.
- `ios/`: Swift and SwiftUI iOS app.
- `contracts/`: versioned API contract shared by both apps.
- `docs/`: product plan, architecture, and development workflow.

Recipes and getting fed with little effort lead the product. Recipe-site
preservation, barcode Add and Use, costs from recipes and receipts, and household
sharing are core scope. Apple inference runs in supported iOS clients; Gemini
runs through the backend. Validate both providers' drafts using shared rules.

Read `docs/app-plan.md` and `docs/development.md` in the active worktree before
implementation. Keep Gemini credentials on the backend. Inventory calculations,
recipe matching, and financial totals must remain deterministic.

## Verification

Run the checks appropriate to each changed component once its tooling exists.
Android and backend checks use the pinned Gradle wrapper. iOS checks require
macOS and Xcode. Documentation-only work requires checking links and formatting.
Do not report application tests as passing before an application exists.
