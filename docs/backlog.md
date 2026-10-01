# Implementation backlog

The sequential basic-functionality pass is implemented locally. This is the
reconciliation checkpoint requested by the user; production readiness and iOS
parity are still open.

| Item | Local result | Evidence |
| --- | --- | --- |
| Recipe library | Manual/imported recipes, editing/revisions, search, favourites, scaling, photo ZIP export | Backend tests and Android create/edit/find journey |
| Shopping | Pinned meal revisions/servings, shared stock allocation, combined shortages, extras, check-off | Allocation/persistence/conflict tests and Android journey |
| Pantry | Remembered barcode-to-ingredient packages, Add/Use, partial amounts, Undo, explicit cooking reconciliation | Domain tests and Android typed-barcode journey |
| Household/offline | Device sessions, isolated vaults, invitations, revocation, cached snapshots, pending scans/extras/check-off | Access/restart/replay tests and two-session Android sharing journey |
| Costs | Receipt OCR/text drafts, reconciled spending, scan links, correction history, prices, monthly budgets, portion/package estimates | Money/stock invariants, Android receipt journey and real bundled OCR fixture |
| Suggestions | Saved ready/close matches, Gemini recipe drafts, Apple adapter, shared draft validation | Ranking/fake-Gemini tests and Android pantry-to-ready-meal journey |

## Remaining verification and release work

- Compile and run iOS on macOS. Check Kotlin/Compose compatibility with the Xcode
  SDK used for Foundation Models; the current Windows-verified toolchain is
  pinned. Apple suggestions require iOS 26, a supported device/language, enabled
  Apple Intelligence and a ready system model. Apple adapter, Vision OCR, camera,
  browser, export and file-protected storage have not been compiled here.
- Test Gemini against a configured backend key. Extraction and generation have
  fake-response coverage; no live Gemini request has been made.
- Check physical barcode cameras, package/product coverage and real receipts.
  Android OCR has a fixture check, not broad retailer-layout coverage. Unmapped
  quantities, units, receipt discounts and missing prices remain explicit.
- Validate live Android recipe browsing on a trusted device. This emulator
  rejects the environment's HTTPS certificates; errors stay rejected. Rendered
  WebView fixtures and real-site backend imports have been checked separately.
- Replace prototype device identity with managed login, account recovery and
  token lifecycle. Add PostgreSQL/migrations, release storage/backup/retention,
  iOS Keychain, invitations/member usability, platform sharing, device endpoint
  configuration, accessibility and distribution checks.
- Broaden offline edits/caching after reviewing the current flow. Queued known
  scans, extras and check-off are supported; imports, recipe edits, unknown
  products, fresh calculations and photos require a connection. Stock remains
  authoritative while pending operations are visible. Revoked caches clear on
  reconnection; downloaded content cannot be erased while a device stays offline.

## Product choices to reconcile

Try the save/select/shop/eat flow before adding more. Review package/remainder
Use defaults, Swedish product coverage and unit aliases, receipt correction
convenience, household/device naming and account recovery, and the amount of
pantry measurement the app should require. AI drafts need review; both providers
use the same Kotlin rules and never directly change stock or actual spending.

All work is committed on task branches. Main integration belongs to the user.
See [the product plan](app-plan.md), [development workflow](development.md), and
[run instructions](../README.md).
