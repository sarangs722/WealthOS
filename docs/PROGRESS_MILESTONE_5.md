# Milestone 5 — Public financial references (0.5.0)

Completed and verified 27 October 2026. Milestone 6 has not been started.

## Implemented

* Native Public data & sources page from Settings/Home; default-off optional networking, manual refresh, provider attribution/terms, source and retrieval dates, validation, publication caveats, freshness and last-known/error states.
* ECB-derived INR FX references, MoSPI national Combined General CPI and published YoY (2024 base), RBI repo observations via official BIS v2. Fixed, bounded HTTPS requests contain no personal financial information.
* Exact-code/optional-ISIN bindings for INR mutual funds, streaming personal AMFI NAV-file import retaining only the selected quote, clear unauthenticated-file provenance, explicit review/application. AMFI catalogue downloads are deliberately unavailable under its verified usage restrictions.
* Append-only source corrections, latest-revision deduplication including corrections reverting to an earlier value, refresh success/failure history, offline cache use, explicit idempotent NAV/FX posting with same-day financial revisions and atomic provenance/audit.
* Manual economic/bank-rate references and source data health. Economic data never rewrites contracts or forecast assumptions. Existing manual prices/FX remain available. There is no background worker or automatic posting.
* Obscured reference amounts/inputs and clearly fictional, read-only, memory-only demo data. No new third-party dependency.

## Architecture and schema

Added `core:network`; pure financial domain remains Android/network independent. Explicit schema 5→6 adds five public-data tables; all 26 existing tables are unchanged, and the earlier migration chain remains registered. Decimal values remain lossless TEXT/BigDecimal. The same encrypted vault, application identity and Keystore wrapping key are retained.

Unit-price quotes can have dates before a Quick Add opening, while positions/transactions and observed totals cannot be backdated implicitly. Historical holdings before opening remain unavailable. Source cache alone contributes no wealth.

See [architecture](MILESTONE_5_ARCHITECTURE.md), [verified sources and usage decisions](MILESTONE_5_SOURCES.md), [plan](MILESTONE_5_PLAN.md) and [security](SECURITY.md).

## Verification

| Automated suite | Executed | Result |
| --- | ---: | --- |
| Pure finance/domain | 97 | Passed |
| JVM file/CSV/request parsers | 10 | Passed |
| Android XML/JSON/provider parsers | 7 | Passed |
| Encrypted repositories, relationships and migrations | 46 | Passed |
| Native UI, privacy and persistence workflows | 15 | Passed |
| **Total** | **175** | **Zero failures, zero skips** |

Additionally, all **3 real HTTPS provider checks** (ECB, MoSPI, BIS) passed on the Android emulator. The **2 actual APK-upgrade fixtures** passed: the original 0.4.0 APK created its own encrypted bank/holding/income/scenario records, then `adb install -r` updated it to the final 0.5.0 APK without clearing/uninstalling the seeded installation. Retention checks verified both bank revisions (₹100.10/₹200.20), 5 investment units/₹400.00 FIFO cost/₹502.50 valuation, received income and the saved ₹100.00 monthly contribution assumption. Network refresh defaulted off, and launcher verification returned `Status: ok`.

Closing-date NAV tests cover same-day corporate actions entered after the quote and later-date carried-price adjustments, including an encrypted reopen fixture. UI checks cover exact scheme binding, content-resolver file reading, explicit application, correct holding value/book cost, retained references after recreation, obscured inputs and disabled refresh while network is off. Existing financial expected amounts were retained while correcting asynchronous test synchronization.

The actual app/test APKs build successfully. Lint reports **zero errors and one notice** that a newer Gradle version is available; the compatible existing toolchain remains pinned. `Verify-Regression.ps1` now checks emulator readiness and fresh executed-test reports, preventing false success when the Android test engine reports an APK install/boot failure without executed tests. The final live BIS probe passed after implementing documented M/NaN missing-day gaps; no zero/interpolated observation is substituted.

All device verification used the guarded `WealthOS` API 36 AVD, `emulator-5554`; instrumentation phases were serialized. The personal phone and its financial records were not accessed or modified. API 29 is the declared supported minimum, not a runtime-tested device in this milestone.

### Build and evidence

* Package `com.wealthos.app`, version `0.5.0`, versionCode `5`; encrypted schema `6`; compile/target API `37`, minimum API `29`.
* APK: `app/build/outputs/apk/debug/app-debug.apk`; identical archived baseline: `.tools/milestone-5.apk`.
* Final APK SHA-256: `55f51eae926a537eb14563b8010932f93baef49881a01514f0602d7d6addc5fa`.
* Existing signer SHA-256: `dd7d62a4506fd0cd64cb1dddaa11a56f838694a64b7ec8c6f79f6461c5589f73`; signature verified before actual in-place upgrade.
* Machine-readable evidence: `.tools/m5-verification/evidence.json`; archived test reports under `.tools/m5-verification/`.
* Complete regression log: `.tools/m5-regression-release-candidate.log`; final source checks: `.tools/m5-network-final.log`; real feed checks: `.tools/m5-live-final.log`.
* Actual final-APK upgrade evidence: `.tools/m5-upgrade-final-verification/result.json`, `old-apk-seed.txt`, `updated-apk-retention.txt` and the final fictional demo capture.
* [Native screenshot, explicitly fictional demo](screenshots/milestone-5-public-data-demo.png). Screenshot protection was restored immediately after the test capture.

Run `scripts/Verify-Regression.ps1` with explicit SDK/Java paths for offline deterministic regression. `scripts/Verify-PublicFeeds.ps1` separately opts into real public requests; ordinary tests do not depend on Internet availability. `scripts/Verify-ApkUpgrade.ps1` requires an empty isolated AVD (or its explicit resume mode), the preserved `.tools/milestone-4.apk`, and the built final/test APKs. It refuses a non-emulator target or a different AVD/signing identity.

## Limits and next milestone

AMFI import is user-initiated; it authenticates file format/identity fields, not the publisher. No stock/ETF/commodity connector was enabled without verified legitimate free access. MoSPI does not expose provisional/final status for these observations; BIS is not a live announcement feed. Provider format/TLS/access failures preserve last-known data and manual operation.

This remains a development/debug build. Portable password-protected backup/restore, API 29/physical-device coverage, performance/accessibility review, independent security review and release signing remain **Milestone 6 — Hardening and Release**, requiring explicit authorization. Do not uninstall or clear a personal installation to upgrade; the device-bound vault key is not a portable backup.
