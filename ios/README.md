# Basil iOS app

Planned native iOS application using Swift, SwiftUI, structured concurrency,
URLSession for networking, and local storage selected against the final deployment
targets. Verify Apple Intelligence framework and device requirements before
choosing minimum OS versions; basic app support and Apple AI support can differ.

Recipes lead the app: share a recipe-site link, keep a normalized copy, choose
servings, and get a missing list. Add a camera scanner with visible Add and Use
modes, remembered barcodes, partial quantities, and Undo. Include recipe and
receipt scans and shared household data. Meal dates are optional.

Use Apple Intelligence through available on-device APIs, with Gemini also
available through the backend. Check capabilities at runtime and respect cloud
processing preferences. Both providers produce the same reviewed domain drafts.

Use the same versioned backend API as Android, cache recipes and household data,
and queue known-product scans and shopping changes offline. Synchronize on launch,
foreground, and restored connectivity, with background refresh as an optimization.

Keep presentation and local storage in the iOS project. The Kotlin backend
owns recipe matching and financial calculations. Store authentication tokens
in Keychain and call Gemini through the backend.

Build, simulator tests, and signing require macOS and Xcode. See the
[app plan](../docs/app-plan.md) and [API contract scope](../contracts/README.md).
No iOS application has been scaffolded yet.
