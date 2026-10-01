# Implementation backlog

Work proceeds one item at a time. Reconcile product choices after a general
basic flow exists. These statuses describe local prototype functionality,
not production readiness.

1. Recipe library: implemented and locally verified. Manual recipes, editing,
   saved revisions, search, favourites, normalization, and photo ZIP export.
2. Shopping: next. Persist recipe selections and servings, pin recipe revisions,
   allocate pantry stock once, combine shortages, manual items, and check-off.
3. Pantry convenience: pending. Remember products, barcode Add/Use, partial
   quantities, Undo, and cooking reconciliation.
4. Household sharing and offline synchronization: pending. Identity, membership,
   durable storage, invitations, conflicts, caches, and queued operations.
5. Costs and suggestions: pending. Recipe estimates, receipt and recipe scans,
   price learning, budgets, Gemini and supported Apple suggestions.

## Verification gaps

Backend tests and Android builds pass. The Android create/edit/favourite/search
journey passes on an emulator. iOS requires macOS
and Xcode. Live Gemini needs a configured backend key. Live Android WebView
navigation is blocked by the emulator's trust of this environment's HTTPS
certificates; SSL errors remain rejected. Android fixture tests exercise the
actual WebView without bypassing certificate checks.

See [the product plan](app-plan.md) and [development workflow](development.md).
