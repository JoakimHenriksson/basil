# Basil development workflow

Basil follows the worktree conventions in `~/src/cogitatibus/AGENTS.md` and
`CLAUDE.md`: a bare repository at the workspace root, a reference checkout at
`main/`, and branch worktrees whose directories match their branch names.

## Workspace layout

```text
~/src/basil/
  .git/                    Bare Git repository
  AGENTS.md                Workspace agent instructions
  CLAUDE.md                Matching workspace instructions
  main/                    Reference worktree for main
  feature/
    app-plan/              Current planning worktree
    recipe-foundation/     First Kotlin implementation worktree
    recipe-browser/        Browser capture, photos, and Gemini extraction
    <name>/                Future feature worktrees
  bugfix/
    <name>/                Created when a bug fix needs a worktree
  release/
    <name>/                Created when preparing a release
```

`bugfix/` and `release/` are conventions; Git creates their directories when
the first corresponding worktree is added. Each worktree contains the complete
project. Do not nest one worktree inside another.

The root instruction files apply when an agent starts at `~/src/basil`.
Copies within the planning worktree are intended to be versioned so they travel
with the project. Keep both instruction formats aligned when the workflow changes.

## Creating a worktree

Run from `~/src/basil` after `main` has an initial commit:

```sh
git worktree add -b feature/pantry feature/pantry main
git worktree add -b bugfix/quantity-conversion bugfix/quantity-conversion main
git worktree add -b release/first-beta release/first-beta main
git worktree list
```

Choose one scoped branch per task, then run project commands from that worktree.
`main/` is read-only for reference. Do not check out, edit, merge, or commit there.
The user owns all integration into `main`, including fast-forward merges.

## Starting an empty repository

At setup, Basil had a bare repository and an unborn `main` checkout, with no
commits. The first planning worktree was therefore created with:

```sh
git worktree add --orphan -b feature/app-plan feature/app-plan
```

The planning documents were committed locally on `feature/app-plan`, and
`feature/recipe-foundation` was created from that commit. Until the user establishes
`main`, use a committed task branch as the base for the next worktree:

```sh
git worktree add -b feature/<name> feature/<name> feature/app-plan
```

The user owns adoption of the bootstrap history into `main`.
Once `main` exists, create subsequent worktrees from it using the normal
command above. Do not create more orphan branches for routine development.

## Project layout inside a worktree

```text
backend/                   Kotlin backend
android/                   Native Android app
mobile/                    Kotlin Multiplatform UI and networking
ios/                       iOS host for the Kotlin app
contracts/                 Shared Kotlin Multiplatform API DTOs
docs/                      Product plan and architecture decisions
```

The foundation worktree contains a working local recipe slice, build files,
backend tests, an Android app, and a shared Kotlin UI with an iOS host and CI.
Deployment, authentication, shared households, and remaining product features
are planned work. See the root README for executable commands.

## Development environment

Use a pinned JDK and committed Gradle wrappers for the backend and Android.
The first implementation pins JDK 17 and Gradle 8.10.2. Use Docker Compose
for a local PostgreSQL instance when production persistence is introduced. Each active
worktree needs its own configuration, database or schema, and service ports.

Android development requires Android Studio and an emulator or physical device.
iOS builds, simulator checks, and signing require macOS and Xcode. Windows can
host backend and Android development; macOS CI must cover the iOS app.

Keep local settings and provider keys in ignored files or environment variables.
Commit example configuration with empty placeholders. Keep Gemini credentials
in backend configuration and secure mobile tokens with each platform's storage.

## Verification and review

Before implementation, pin the actual toolchain and document executable build
commands in each component README. Backend and Android CI should use their
Gradle wrappers; iOS CI should use an Xcode build and test scheme on macOS.

Run `:backend:test` for domain, import, persistence, and HTTP checks and
`:android:assembleDebug` for the Android build. iOS needs a macOS build as described
in its README. PostgreSQL migration checks become required when production
persistence is introduced. Run relevant checks before committing a change.

Keep generated files and secrets out of commits. Present changes in their task
worktree for review; do not merge or push without the user's authorization.

The recipe-browser slice is based on `feature/recipe-foundation`. Backend tests
cover rendered captures, photo retention, and fake Gemini responses. An Android
instrumented fixture covers Save recipe and the preserved source link; see the
Android README for the isolated backend setup and device commands.
