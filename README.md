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
its household registry and vaults in `backend/data/households/`. The first
device adopts the existing `backend/data/vault.json` if present. The debug Android app uses
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

## Pantry Add and Use

In Pantry choose Add or Use, then scan an EAN/UPC barcode or type it. Remembered
products apply one package immediately; Use defaults to one package or the first
tracked remainder. Enter a partial amount before scanning when needed. New
products use an Open Food Facts draft when available; confirm the ingredient
and package amount once. A 425 g tomato tin then becomes 425 g crushed tomatoes.
Recent changes offer Undo. Undo refuses to remove stock already used later.

Log cooked meal consumes measured recipe requirements. Explicitly select any
Use records already belonging to this meal; they count once. Similar amounts
are never silently assumed to be the same use. Undo the cooking log before a
linked scan. Unknown required quantities need review before consumption.
Android Add/Use/Undo is checked with a typed barcode; optical camera decoding
requires physical-device QA. The Kotlin iOS camera adapter is unverified on Windows.
Open Food Facts coverage for actual household products remains to be checked.

## Receipts, meal costs and budgets

Costs accepts a receipt photo through on-device text recognition, or pasted text.
Review item prices, purchase date and the printed total before recording it.
The item sum must match. Spending-only is the default; ingredient details can
learn a price, explicitly add stock, or link one or more earlier barcode Adds.
Links must match ingredient and total quantity, so a scanned purchase is counted
once. Correct purchase changes prices/date with an audit and keeps stock effects.
Refunds can be entered as negative spending lines without adding stock.

Set a monthly grocery limit (SEK by default). The purchase date determines the
budget month. Estimate meal cost on a recipe or whole packages in Shopping.
Missing prices/quantities are shown; they are never invented. Unallocated receipt
discounts record spending but don't teach verified product prices. Decimal cost
calculations round to minor units with HALF_UP; package counts round upward.

Recipe photos also use the native text reader. Review the extracted recipe in
the editor before saving. With Gemini configured, general recipe text can be
extracted. Otherwise Ingredients/Method (or Swedish) headings are supported.
Android OCR reads a fixture receipt image; actual camera/photo quality and the
iOS Vision adapter require device validation. No scanned photo is sent to Gemini;
recipe text is sent only when you choose Extract scanned recipe.

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

## Households and offline use

The first connection creates a device session and private household. In Household,
show members and create a one-use invitation for another device. The owner can
revoke invitations and remove members. Food, shopping, stock changes, and retained
photos are scoped to membership. Active apps check for updates every 30 seconds;
Sync household also refreshes explicitly.

Recipes, pantry, shopping, products and audit records are cached on the device.
Known-product scans, shopping extras and check-off can be queued offline. Pending
changes stay visible until accepted. Shopping replay merges independent extras
and checks unchanged requirements; changed requirements remain conflicts.
Stock balances stay authoritative until the queued action is accepted. Recipe
editing, imports, unknown products, fresh calculations, and photos need a connection.
Revocation clears the active cached household and pending actions upon reconnection.

Android encrypts its device store with an Android Keystore key. iOS uses app-private
file-protected storage; Keychain credential storage still needs release work and
macOS validation. This is device identity for a local prototype. Managed login,
account recovery, token lifecycle, PostgreSQL and production hosting remain work.
Back up the complete household directory, including `households.json` and media.
`BASIL_HOUSEHOLDS` overrides that directory for an isolated development instance.

## Current boundaries

This is a local development prototype using an atomic JSON vault. Production identity,
PostgreSQL household persistence, Gemini meal suggestions, and Apple
Intelligence remain implementation work. Keep the development service local until production identity and hosting are ready.

- [Product plan](docs/app-plan.md)
- [Worktree workflow](docs/development.md)
- [Backend](backend/README.md), [Android](android/README.md), [iOS](ios/README.md)
- [API contracts](contracts/README.md)

The costs slice lives in `feature/costs`, based on `feature/household-sync`. The user owns integration into
`main`.
