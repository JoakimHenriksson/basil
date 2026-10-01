# Implementation backlog

Work proceeds one item at a time. Reconcile product choices after a general
basic flow exists. These statuses describe local prototype functionality,
not production readiness.

1. Recipe library: implemented and locally verified. Manual recipes, editing,
   saved revisions, search, favourites, normalization, and photo ZIP export.
2. Shopping: implemented and locally verified. Persist recipe selections and servings, pin recipe revisions,
   allocate pantry stock once, combine shortages, manual items, and check-off.
3. Pantry convenience: implemented and locally verified (camera QA pending). Remember products, barcode Add/Use, partial
   quantities, Undo, and cooking reconciliation.
4. Household sharing and offline synchronization: basic local flow implemented. Identity, membership,
   durable storage, invitations, conflicts, caches, and queued operations.
5. Costs: basic local flow implemented. Reviewed receipt/photo/text scans,
   price learning, stock linkage, corrections, refunds, monthly budgets, and
   separate portion/package estimates.
6. Pantry suggestions: next. Saved ready/close recipes, Gemini and supported
   Apple Intelligence drafts, with shared validation and deterministic shortages.

## Verification gaps

Backend tests and Android builds pass. The Android create/edit/favourite/search
journey passes on an emulator. iOS requires macOS
and Xcode. Live Gemini needs a configured backend key. Live Android WebView
navigation is blocked by the emulator's trust of this environment's HTTPS
certificates; SSL errors remain rejected. Android fixture tests exercise the
actual WebView without bypassing certificate checks.

See [the product plan](app-plan.md) and [development workflow](development.md).

Shopping verification: backend allocation, pinned snapshots, persistence, retry
deduplication, stale edits, and changed check-off tests pass. Android selected
servings, extras, meal removal, check-off, and refresh journey passes. Offline
shopping remains in item 4; iOS device verification remains pending.

Pantry verification: package mapping, barcode check digits, retries after stock
depletion, partial/multiple-package Use, Undo, atomic insufficient-stock failure,
and explicit cooking reconciliation pass backend tests. Android typed-barcode
Add/Use/Undo journey passes. Optical decoding, real product-data coverage, and
iOS camera behavior remain unverified.

Household verification: isolation across every food/media endpoint, expiring
one-use invitations, owner-only membership changes, durable revocation, and
complete legacy-vault adoption pass backend checks. Shared mobile tests cover
restart/replay, independent shopping actions, conflicts, and cache clearing.
Android recipe/shopping regression and two-session invitation/shared-recipe
journeys pass. Production account identity, recovery, token lifecycle,
PostgreSQL, iOS Keychain, and physical/multi-device QA remain release work.

Costs verification: reconciled totals, duplicate receipts, multiple scan links,
atomic stock/spending acceptance, price correction history, portion/package
rounding, refunds, missing prices, and discount uncertainty pass backend checks.
Android receipt review/budget/correction journey and real bundled OCR fixture
pass. Headed recipe-text extraction passes. Real receipt formats, physical
camera quality, Gemini general text extraction, and iOS Vision remain QA gaps.
