# Basil

A Kotlin app for keeping recipes, choosing servings, and buying what is missing.

The first working slice imports recipe pages, preserves the content locally,
scales ingredients, and compares them with a simple pantry. Android and iOS
share Kotlin Multiplatform application code and a Compose Multiplatform UI.

## Run the first slice

Use JDK 17 and Android SDK 35. From this worktree:

```sh
./gradlew :backend:run
./gradlew :backend:test :android:assembleDebug
```

On Windows use `gradlew.bat`. The backend listens on `127.0.0.1:8787` and keeps
its development vault in `backend/data/vault.json`. The debug Android app uses
`10.0.2.2:8787`, the emulator's address for the host. Open the root project in
Android Studio or install `android/build/outputs/apk/debug/android-debug.apk`.

This Windows environment needs its trusted certificate store for HTTPS.
For the current PowerShell session before building or importing:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :backend:run
```

The [iOS instructions](ios/README.md) describe building the same Kotlin UI on
macOS. iOS compilation and device behavior remain unverified in this Windows
workspace; macOS CI is configured but has not been run remotely.

## Try it

Paste a recipe link or share one from an Android browser. Choose servings and
ask what to buy. Add known ingredients in Pantry to reduce the missing list.

Real examples verified with the running Kotlin importer:

- [ICA pancakes](https://www.ica.se/recept/pannkakor-grundsmet-2083/)
- [Arla pancakes](https://www.arla.se/recept/pannkaka/)
- [Zeinas Kitchen pepper beef](https://zeinaskitchen.se/kinesisk-pepparbiff/)
- [Köket pancakes](https://www.koket.se/mormors-pannkakor)
- [BBC Good Food pancakes](https://www.bbcgoodfood.com/recipes/easy-pancakes)

Standard recipe data is preferred. Blog-text fallback preserves the recipe and
flags review; ambiguous yields can be set in the app. These checks cover the
specific pages above, not every recipe on each site.

## Current boundaries

This is a local development prototype using an atomic JSON vault. Authentication,
PostgreSQL household persistence, invitations, barcode scanning, receipt and
recipe-photo imports, budgets, offline synchronization, Gemini, and Apple
Intelligence remain implementation work. Keep the unauthenticated service local.

- [Product plan](docs/app-plan.md)
- [Worktree workflow](docs/development.md)
- [Backend](backend/README.md), [Android](android/README.md), [iOS](ios/README.md)
- [API contracts](contracts/README.md)

Work happens in `feature/recipe-foundation`. The user owns integration into
`main`.
