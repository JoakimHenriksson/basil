# Basil

A Kotlin app for keeping recipes, choosing servings, and buying what is missing.

The first working slice imports recipe pages, preserves the content locally,
scales ingredients, and compares them with a simple pantry. Its built-in browser
captures the page you are viewing and saves a local copy of the recipe photo. Android and iOS
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

Tap **Browse recipes** to search or visit a recipe site inside Basil. Tap
**Save recipe** to preserve the currently loaded page as a recipe, including its
photo when available. Saving leaves you on the page; **View saved** opens your copy.
Every copy retains the original URL and an **Open original** link.

You can also paste a recipe link or share one from an Android browser. Choose servings and
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

## Recipe library

Write a recipe manually or edit a saved copy. Edits keep the source and photo,
retain prior revisions, and reject stale saves. Search titles, ingredients, and
notes, or filter favourites. Export recipes opens the platform share sheet with
a ZIP containing `recipes.json` (schema version 1) and retained `media/` photos.
The export contains current recipes; back up the vault for revision history.

Ingredient ranges scale both endpoints; shortages use the upper amount and say
so. Teaspoons and tablespoons use metric 5 ml and 15 ml measures. Cups, fluid
ounces, and unspecified packages still need review. Original lines remain saved.

## Shopping

Choose servings on a recipe and tap **Add to shopping**. The Shopping tab keeps
selected meals, combines their requirements, and allocates pantry stock once.
Adjust servings, remove meals, add extras, or check items off. Selected recipes
keep their saved revision even after editing or deleting the library copy.
Changed requirements reset check-off; manual items survive recalculation.
Checking off an item does not add stock. Record the purchase in Pantry.
Refresh after another client changes the list; stale writes are rejected.
Offline shopping is part of the later synchronization slice.

## Gemini extraction

Structured recipe data is used first. For other pages, the backend can use Gemini
to extract a recipe from relevant page text. Set `GEMINI_API_KEY` in the backend
process environment; optionally set `BASIL_GEMINI_MODEL` (default:
`gemini-2.5-flash`). Keys stay on the backend. No page is sent until you choose
Save recipe. Gemini imports carry a review warning, preserve original ingredient
wording, and use Kotlin normalization and scaling rules.

Without a key, the existing page-text importer remains available. If Gemini is
unavailable, a readable text recipe can still be saved with a warning. Google
API requests are verified with fake responses; a live Gemini extraction has not
been run in this workspace.

Photos are copied to a `media/` directory beside the vault and served by Basil.
Back up both the vault and that directory. A failed photo download keeps the
recipe and displays a warning. SVGs and authenticated photos are not supported.
Full pages, browser cookies, and browsing history are not stored in the vault.

Android rendered-page capture is checked on an emulator with a JavaScript fixture.
Live WebView navigation is still unverified because the emulator rejects this
host environment's HTTPS certificates. Real ICA recipe and photo preservation
was checked through the backend. The iOS browser requires macOS validation.

## Current boundaries

This is a local development prototype using an atomic JSON vault. Authentication,
PostgreSQL household persistence, invitations, barcode scanning, receipt and
recipe-photo imports, budgets, offline synchronization, Gemini meal suggestions, and Apple
Intelligence remain implementation work. Keep the unauthenticated service local.

- [Product plan](docs/app-plan.md)
- [Worktree workflow](docs/development.md)
- [Backend](backend/README.md), [Android](android/README.md), [iOS](ios/README.md)
- [API contracts](contracts/README.md)

The shopping slice lives in `feature/shopping`, based on `feature/recipe-library`. The user owns integration into
`main`.
