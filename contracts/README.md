# Basil API contracts

This directory will hold the versioned OpenAPI definition for `/v1`, shared
request and response examples, and compatibility fixtures used by the backend,
Android, and iOS projects. The contract is planned and has not been written yet.

Specify quantities with decimal values and units, money with integer minor
units and an ISO currency code, UTC timestamps, local household dates, record
versions, idempotency keys, and machine-readable error codes. Household identity
must be checked by the backend for every operation.

Include fixtures for normalized site and photo recipes, servings and fractions,
recipe shortages, barcode Add and Use, recipe costs, receipt reconciliation with
prior stock additions, missing prices, invitations, duplicate requests, and
concurrent offline changes. Gemini and Apple drafts map to the same recipe format.
Both mobile apps must interpret the same fixture identically. Generate client
types only after validating the chosen tooling for both Kotlin and Swift.

See the [app plan](../docs/app-plan.md) for the proposed API surface and rules.
