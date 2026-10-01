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

The build is verified on Windows. Barcode scanning, device share-flow QA,
offline storage and queued changes, AI, and budgets are not implemented yet.
See the [product plan](../docs/app-plan.md).
