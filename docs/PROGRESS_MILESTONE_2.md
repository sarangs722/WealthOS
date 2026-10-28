# Milestone 2 — Financial Portfolio

Verified **27 October 2026**. APK **0.2.0**, version code **2**. This extends the working native application; no later milestone was started.

Historical verification record. See [Milestone 3 progress](PROGRESS_MILESTONE_3.md) for the current APK and schema.

## Implemented

* Indian/international mutual funds, stocks and ETFs; physical/financial gold and commodity unit holdings; EPF/PPF/NPS and property observations; FD/RD contracts; loans and liabilities.
* Quick Add known opening units/optional acquisition basis, with detailed subsequent purchases, partial sales/redemptions, fees, distributions, reinvestments, splits, bonus issues and explained unit adjustments. Opening history/basis is never invented.
* FIFO lots, remaining cost, realized/unrealized native-currency book gains, manual unit prices and dated valuation revisions. Corporate-action-adjusted carried prices are labelled theoretical estimates until a fresh quote is entered.
* FD/RD principal, contracted rates, conventions, maturity dates, actual installments, simple-contract interim interest payouts and actual closure principal/interest/fees. Observed totals remain separate from generic gross estimates. No scheduled payment/maturity cash is fabricated.
* Known loan principal, actual repayment components and an explicitly estimated monthly reducing-balance schedule, including zero interest and final rounding adjustment.
* Brokerage cash accounts, assigned custody holdings and statement-total reconciliation references. References are never additional assets. Linked cash, explicit already-reflected/provisional treatment, authoritative snapshot absorption and atomic same-currency internal transfers prevent duplicate cash application.
* Dated manual FX with source notes, timestamps, revisions and staleness; INR consolidation, allocation, native exposure, liquid net worth and historical completeness. Missing values/rates remain unavailable.
* Native add/edit metadata, transaction, valuation, contract event, FX, transfer and statement-reference forms; category grouping/search/sort; retained history and audit; privacy controls and extended read-only fictional demo.

## Architecture and schema

The existing three modules and dependencies remain. Schema **2→3** adds nine normalized tables without altering bank observations/accounts/audit records. Explicit migrations, foreign keys, unique sequences/revisions and transactional audit writes protect integrity. Lots are derived from immutable ledger events, avoiding a second mutable holdings total. A transactionally consistent book is published through Room invalidation/Flow.

Application ID `com.wealthos.app`, database path and device Keystore alias are unchanged. The new APK uses the same signing certificate as 0.1.0. See [architecture and exact financial policies](MILESTONE_2_ARCHITECTURE.md).

## Actual automated verification

| Check | Result |
| --- | --- |
| `:core:finance:test` | **33 passed**: 10 bank fixtures + 23 portfolio/contract fixtures |
| `:core:database:connectedDebugAndroidTest` | **20 passed** on API 36 x86_64 |
| `:app:connectedDebugAndroidTest` | **7 passed**, zero failed/skipped, on API 36 x86_64 |
| `:app:assembleDebug` | **BUILD SUCCESSFUL**; APK generated |
| `:app:lintDebug` | **0 errors, 1 advisory**: newer Gradle available; compatible version remains deliberately pinned |
| Actual old-APK seed fixture | Direct instrumentation: **OK (1 test)** on installed 0.1.0 |
| In-place APK update/retained history | Same-signed `adb install -r`; direct retention fixture: **OK (1 test)** |
| Standalone updated launch | **Status: ok** |
| Fictional native UI rendering | Captured after selecting explicit demo mode and visually inspected |

There are **60 passing regression tests**, plus the two directly executed installation-fixture tests. Installation fixtures are parameter-gated because they require the original APK and a pre-existing seeded record; an ordinary connected suite does not fabricate that setup or count an unexecuted upgrade check as passed.

Financial fixtures cover multiple SIP prices, fee-inclusive FIFO, partial/full sales, unknown basis, distributions/reinvestment, splits/bonus and quote adjustment, independent FX movement, stale/missing data, large exact products, decimal-scale equivalence, cash snapshots and internal transfers, broker reconciliation, deposit/RD conventions, maturity closure, actual loan interest versus principal and amortization rounding. Device checks also cover populated encrypted schema migration, unchanged bank values/UUIDs/notes/audits, failed cash-leg rollback and same-day deposit-observation reconciliation. UI checks exercise bank history/edit/recreation, investment purchases/sales/manual quotes, property, loan repayments, FD observed-versus-estimated values, demo/privacy and the secure access gate.

Generated reports remain in the usual module `build/reports` directories. The current [Verify-ApkUpgrade.ps1](../scripts/Verify-ApkUpgrade.ps1) has since been updated for 0.2.0→0.3.0; the retained evidence below documents the original 0.1.0→0.2.0 run. The installation fixture refuses physical devices, requires the isolated `WealthOS` AVD, compares APK signers and never uninstalls or clears data. Its resume mode rechecks an already-seeded fixture without resetting it.

Retained evidence: [upgrade hashes/signing/result](verification/milestone-2-apk-upgrade.json), [old APK fixture](verification/milestone-2-old-apk-seed.txt), [retention fixture](verification/milestone-2-retained-bank-history.txt), [fictional native screenshot](images/milestone-2-demo-home.png).

## APK and upgrade

APK: `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `9090c6388baa433e96c98faa6aa83492e682a88a4ea5deeadb22ebd9a358993b`.

Install **over the existing 0.1.0 app**. Do not uninstall or clear storage. The actual old→new fixture retained bank observations and their correction history; the populated encrypted migration also retained exact amounts, UUIDs, notes and audit data. The user's phone and its financial records were not accessed during development or testing.

## Security and remaining limits

* Encryption, device key protection, optional access gate, amount obscuring and backup exclusions remain. No new permission or INTERNET access was added. APK inspection confirms minimum API 29, compile/target API 37 and four native ABIs.
* **Portable backup/restore is still unavailable.** Key/device loss, uninstall or clear-storage can make records unrecoverable. The APK is development-signed/debuggable; release hardening and signing remain Milestone 6.
* Runtime verification used API 36. API 29/37, physical biometric dialogs, hardware Keystore assurance, 16 KB devices, TalkBack and device/font matrices remain unverified.
* Tax accounting, statutory retirement accrual, bank-specific FD/RD day-count/penalty rules, floating-rate/day-count loan schedules, fractional-share cash-in-lieu and automatic price feeds are not inferred. Use observed statements and actual cash events where generic estimates differ.
* Financial ledger events and contract terms are retained. Current edits cover metadata, explained unit adjustments and dated valuation revisions; general trade cancellation and contract amendments are not implemented. Loan liabilities cover recorded principal; unpaid capitalized interest must be reflected in an appropriate known principal baseline rather than silently estimated.
* Cross-currency cash conversion is not linked automatically. Manual FX is a valuation observation. For investments funded in a different currency, record actual bank observations independently until execution-level FX accounting is supported.
* XIRR/CAGR, forecasts/scenarios/calculator screens remain Milestone 4; income/protection remains Milestone 3; external providers remain Milestone 5.

Recommended next: **Milestone 3 — Income and Protection**, only after explicit authorization.
