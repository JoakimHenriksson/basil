# Basil Android app

Kotlin Android host for the shared [Kotlin app](../mobile/src/commonMain/kotlin/app/basil/mobile/BasilApp.kt).
It supports recipe-link sharing through Android's Share menu, importing and
browsing saved recipes, choosing yields and servings, displaying missing lists,
and simple pantry entry.

Build from the worktree root with JDK 17 and Android SDK 35:

```sh
./gradlew :android:assembleDebug
```

Install `android/build/outputs/apk/debug/android-debug.apk`. The debug app
connects to the host backend through `http://10.0.2.2:8787` on an emulator.
Override the address with `-Pbasil.apiUrl=https://your-development-service`
when building. Local HTTP is enabled only in the debug manifest.

The build is verified on Windows. Recipe editing/search/favourites, combined
shopping, typed-barcode Add/Use/Undo, household invitations, offline action
queues, receipt review/correction, budgets and saved pantry matches have local
verification. Bundled ML Kit OCR reads a receipt-image fixture. ZXing optical
camera behavior, real device sharing, real receipt formats and live Gemini need
QA. Apple inference is specific to iOS.
See the [product plan](../docs/app-plan.md).

The built-in WebView supports search, HTTPS navigation, Back/Forward, and
Save recipe. Save captures the loaded DOM on demand with no native JavaScript
bridge. File access and mixed content are disabled. The browser stays open after
saving; the saved detail displays the retained photo and Open original.

## Rendered-page device test

Start the backend with an isolated `BASIL_DATA` vault, then boot an Android
emulator and run `:android:connectedDebugAndroidTest`. The test loads a fixture
into the real WebView, lets JavaScript insert its recipe data, taps Save recipe,
checks that browsing remains open, and checks the saved copy and original link.
It adds a test recipe to the backend vault.

This Windows host's Gradle device-result transport failed during verification;
the test was run directly with ADB after `:android:assembleDebugAndroidTest`:

```sh
adb install -r android/build/outputs/apk/debug/android-debug.apk
adb install -r android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk
adb shell am instrument -w app.basil.android.test/androidx.test.runner.AndroidJUnitRunner
```

The emulator does not trust this environment's HTTPS certificates, so live-site
WebView navigation remains unverified here. Certificate errors are surfaced and
rejected. Real-site backend imports and photo downloads use the Windows trust
store and were checked separately.
