# Milestone 3 — Income and Protection

Verified **27 October 2026**. APK **0.3.0**, version code **3**. Milestone 3 is complete; Milestone 4 has not been started.

Historical verification record. See [Milestone 4 progress](PROGRESS_MILESTONE_4.md) for the current APK and schema. The installation script has since been updated for 0.3.0→0.4.0; retained Milestone 3 evidence still documents 0.2.0→0.3.0.

## Implemented

* Income sources, dated expected net-income changes, actual monthly receipts, and optional salary gross/deductions validation. Expected salary remains separate from actual income.
* Monthly summaries by currency with manual-total or recorded-source income, explicit completeness, essential/discretionary/other outflows, actual premiums, loan principal/interest and investment contributions. Surplus before investments and remaining cash expose their inputs and retained revisions.
* Dated emergency targets and allocations to existing bank cash/liquid assets. Availability caps, missing valuation/FX status, stale-input warnings, releases and reference-month coverage ratios prevent reserve duplication or overstated available funds.
* Term/health policies, individual/shared family-floater limits, effective terms and renewal history, recurring/one-off/limited-pay premiums, anchored due dates and cumulative partial/advance settlement corrections. In-app date attention does not assume automatic renewal, grace periods, insurer acceptance or claim eligibility.
* Native Home links, income/cash-flow forms and histories, active Protect destination, emergency progress, policy details, privacy-aware read views and expanded explicitly fictional demo records. Forecast and Tools remain labelled unavailable.

## Architecture and schema

The same three modules, dependencies, application ID, database path, Keystore alias and signing identity remain. Additive schema **3→4** creates nine normalized household tables without altering existing financial tables. Schema 1/2 migration paths remain registered. Dated/monthly financial corrections append explained revisions; metadata edits retain before/after audit. Related writes and audits commit atomically, and book reads remain coherent.

Household records never mutate bank balances or investment positions automatically. Monthly summaries are descriptive totals; premium settlements are cumulative observations per installment. Emergency funds earmark existing wealth; insurance limits are not current assets. See [architecture and exact policies](MILESTONE_3_ARCHITECTURE.md).

## Automated verification

| Check | Result |
| --- | --- |
| `:core:finance:test` | **51 passed**: 10 bank, 23 portfolio and 18 household fixtures |
| `:core:database:connectedDebugAndroidTest` | **28 passed**, zero failures/errors/skips, API 36 x86_64 |
| `:app:connectedDebugAndroidTest` | **9 distinct tests passed**: eight in the regression run, plus the corrected workflow in a successful targeted rerun; zero outstanding failures |
| `:app:assembleDebug` | **BUILD SUCCESSFUL**, APK generated |
| `:app:lintDebug` | **0 errors, 1 advisory** about newer Gradle; compatible version remains pinned |
| Actual 0.2.0 fixture and in-place 0.3.0 update | **OK (1 test)** seeding actual old APK; **OK (1 test)** retaining bank history and investment units/cost after same-signed `install -r` |
| Updated standalone launcher | **Status: ok** |
| Native fictional UI rendering | Captured after explicit demo selection and visually inspected; no overlapping/clipped reserve content |

New fixtures cover expected-versus-received income, source/manual reconciliation, missing versus explicit zero income, salary identities, correction history and exact reopen, reserve caps and FX, unchanged net worth, leap-year/month-end due anchoring, partial/advance/overpayments, unknown premiums, limited-pay/one-off schedules, no automatic renewal, future terms, rollback and populated encrypted schema 3 migration. The migration retains bank amounts, investment units/quotes, linked cash, UUIDs, notes and audit rows. Earlier bank/portfolio regression fixtures remain.

There are **88 distinct passing regression tests**. [Retained test evidence](verification/milestone-3-tests.json) records the source suites and rerun. The UI workflow checks salary receipts, confirmed source-derived cash flow, reserve earmarking, shared health cover, premium settlement and activity recreation, ending with the original bank balance unchanged. The privacy fixture checks obscured reserve/policy read views. Financial expected results were not changed to accommodate failures. After correcting a final UI selector that matched both current balance and retained history, the workflow rerun and build/lint command completed successfully.

The two direct installation tests are additional to those 88 regression checks. They are parameter-gated and are not counted as passing during an ordinary connected suite. Retained installation evidence: [hashes/signing/result](verification/milestone-3-apk-upgrade.json), [old-APK seed](verification/milestone-3-old-apk-seed.txt), [bank/holding retention](verification/milestone-3-retained-bank-and-holding.txt), and [fictional native screenshot](images/milestone-3-demo-protect.png).

[Verify-Regression.ps1](../scripts/Verify-Regression.ps1) restricts instrumentation to the named WealthOS emulator and runs database/UI instrumentation in separate invocations. The optional fixture reset discards only known synthetic emulator installations. [Verify-ApkUpgrade.ps1](../scripts/Verify-ApkUpgrade.ps1) requires an empty isolated AVD, compares signing certificates, seeds the actual old APK, updates with `install -r`, and checks retained records; it never resets the installation being upgraded. Resume mode preserves an already-seeded fixture.

## Test interruptions and physical-device boundary

The initial UI run caught a duplicate lazy-list key where an earmark was displayed in both current allocations and history; distinct keys fixed the crash. An interrupted rerun left synthetic monthly data, and a combined instrumentation run ended during package installation/cleanup. The final verification uses a clean synthetic installation and serializes database/UI engines.

## APK and update

APK: `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `f764763f73c6c76847ca20077c3822ed69dc1180ae25530e145ce95e84dd78ce`.

Certificate SHA-256: `dd7d62a4506fd0cd64cb1dddaa11a56f838694a64b7ec8c6f79f6461c5589f73`.

Install over the existing same-signed 0.1.0/0.2.0 app. **Do not uninstall or clear personal app storage.** Existing encrypted records and the device key are reused. The actual 0.2.0→0.3.0 fixture retained corrected bank balances of ₹100.10/₹200.20, five opening investment units and ₹400 acquisition cost (₹502.50 market value). It then selected the fictional household demo and launched successfully. Earlier schema 1/2 and populated schema 3 migrations are covered by encrypted device fixtures.

During a rerun a physical phone appeared in ADB and Gradle selected it alongside the emulator. The run was interrupted; Android rejected the test-package install with `INSTALL_GRANT_RUNTIME_PERMISSIONS` denial, and no phone instrumentation tests ran. The initial metadata check was blocked by automatic approval review because phone access was outside the emulator-only boundary. The user explicitly approved reading only app version/install timestamps. The phone initially disconnected, then reconnected; the approved read-only check confirmed version code 3 / version 0.3.0 and reported install/update time `2026-10-27 20:14:12`. Metadata alone does not establish how it was installed or whether earlier personal records remain; no such inference is made. No financial records were opened on the phone. Further tests explicitly target `emulator-5554`; no phone reset/uninstall action was requested.

## Security and remaining limits

* SQLCipher, Keystore wrapping, optional device gate, obscured amounts, screenshot protection and backup exclusions remain. No Internet permission, telemetry, upload, provider or background job was added. APK inspection confirms API 29 minimum, target/compile API 37, four native ABIs, version 0.3.0 and the existing certificate.
* **Portable password-protected backup/restore remains unavailable.** Uninstall, clear-storage, key/device loss can make personal records unrecoverable. Keep independent source records. This APK remains development-signed/debuggable; hardening and release readiness remain Milestone 6.
* Runtime verification uses API 36 emulator only. API 29/37 devices, hardware Keystore guarantees, physical biometric dialogs, 16 KB devices, TalkBack and font/device matrices remain unverified.
* Monthly summaries do not reconcile bank cash automatically. Source-derived totals require explicit actual records for every active source; expected pay and policy budgets are excluded. Negative-income reversals need an appropriate descriptive outflow rather than a negative receipt.
* Insurance is a manually maintained record, not an insurer feed. Premiums/timing/cover must come from the actual policy; no legal adequacy or claim determination is offered. Date attention appears in app only, with no system notifications. Historical renamed source/policy metadata uses current display names while financial revisions remain retained.
* Existing Milestone 2 contract, tax, cross-currency funding and general trade-cancellation limitations remain; see its [historical report](PROGRESS_MILESTONE_2.md). No forecast, calculator or public-data adapter was added.

Recommended next: **Milestone 4 — Analytics and Forecasts**, only after explicit authorization.
