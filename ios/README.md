# Basil iOS host

The iOS app displays the same Kotlin Multiplatform and Compose Multiplatform UI
as Android. UI, networking, and application behavior live in `mobile/`.
The small platform entry point in `Host/` creates the Kotlin view controller.

Build on an Apple Silicon Mac with JDK 17, Xcode, and XcodeGen:

```sh
bash gradlew :mobile:linkDebugFrameworkIosSimulatorArm64
cd ios
xcodegen generate
xcodebuild -project Basil.xcodeproj -scheme Basil -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

On an Intel Mac, use `:mobile:linkDebugFrameworkIosX64` and pass
`KOTLIN_TARGET=iosX64` to xcodebuild. Build the Kotlin framework separately;
the host has no external build script and keeps Xcode script sandboxing enabled.

The simulator uses the local backend at `http://127.0.0.1:8787`. The initial
host targets iOS 17. Apple suggestions are gated to iOS 26 and eligible devices
with a ready model, supported locale, and Apple Intelligence enabled. iOS share extension, physical-device
configuration, signing, and release packaging also remain work.

Windows cannot compile or run this target. The macOS CI job is configured but
has not been executed remotely. See the [product plan](../docs/app-plan.md).

The recipe browser uses a Kotlin WKWebView bridge and shares the browser toolbar
and capture logic with Android. It supports navigation and on-demand rendered
page capture. The saved detail displays the retained photo and Open original.
This bridge still requires compilation and runtime validation on macOS.

## Native inference adapter

[AppleModelAdapter.swift](Host/AppleModelAdapter.swift) is the bridge to Apple's
Swift-only Foundation Models API. It registers an adapter with the Kotlin app,
requests a typed recipe draft, and returns JSON. Shared Kotlin validates the
same schema as Gemini and requires review before saving. Apple failure never
silently uses Gemini. All pantry/quantity/financial rules remain Kotlin.

Build the adapter with an SDK exposing Foundation Models (Xcode 26 or later).
The pinned Kotlin 2.0.21/Compose 1.7.3 toolchain has not been verified against that
SDK; resolve compatibility on macOS before claiming iOS parity. This Windows
workspace has not compiled the Kotlin UIKit/AVFoundation/Vision adapters or the
Swift inference bridge. Earlier SDK builds exclude Foundation Models via
`canImport` and show a capability message. Camera/photo descriptions are in
`project.yml`; real device signing, model availability and OCR need QA.

API references checked against Apple's documentation:
[LanguageModelSession](https://developer.apple.com/documentation/foundationmodels/languagemodelsession),
[SystemLanguageModel](https://developer.apple.com/documentation/foundationmodels/systemlanguagemodel),
and [supportsLocale](https://developer.apple.com/documentation/foundationmodels/systemlanguagemodel/supportslocale(_:)).
