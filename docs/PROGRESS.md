# Milestone 1 — verified foundation

Historical report for APK 0.1.0. The subsequent authorized portfolio work is recorded in [Milestone 2 progress](PROGRESS_MILESTONE_2.md).

Verified on **27 October 2026**. The bank-account vertical slice is complete. WealthOS is a real native Android app; this milestone is not a production release of the full product.

## Implemented

* Compose Material 3 light/dark themes, five-section navigation shell, empty/error/loading states, amount obscuring, account search/sort and readable INR grouping.
* Add/edit savings/current account metadata; INR, USD, EUR, GBP and JPY native balances. Account currency is immutable after creation.
* Dated, append-only bank observations, backdated entry, explained same-day corrections, full revision history and recorded timestamps.
* Decimal bank net worth, assets/overdraft liabilities, a documented bank liquidity policy, freshness/completeness, input details and stepped historical balances. Missing FX/history produces unavailable totals. Balance differences are never classified as income or returns.
* Encrypted Room/SQLCipher vault, Android Keystore AES-GCM key wrapping, atomic key/preference files, optional native biometric/device credential interface gate, secure window and explicit backup exclusions.
* Read-only fictional demo kept in memory, separate from the personal vault. Future navigation sections explicitly state availability.

## Architecture/schema

Three modules: `app`, `core:finance` and `core:database`. AGP built-in Kotlin on Android; independent pure JVM financial engine; Flow/Coroutines; lifecycle ViewModel and constructor injection. No network stack, Hilt or WorkManager is needed for this slice. See [architecture](ARCHITECTURE.md).

Schema 2 contains normalized UUID account, observation and audit tables. Decimal values persist as TEXT, effective dates are distinct from recorded instants, relationships have restrictive foreign keys, revisions have a unique index, and multi-record writes are atomic. Explicit migration 1→2 adds observation notes and is exercised against an encrypted legacy fixture. No destructive fallback exists. The current Room schema is exported in `core/database/schemas`.

## Actual verification

| Check | Verified result |
| --- | --- |
| `:core:finance:test` | 10 tests passed, 0 failed/skipped |
| `:core:database:connectedDebugAndroidTest` | 9 tests passed, 0 failed/skipped on API 36 x86_64 |
| `:app:connectedDebugAndroidTest` | 4 tests passed, 0 failed/skipped on API 36 x86_64 |
| UI bank/demo workflows without internet | Earlier 3-test UI suite passed with emulator airplane mode enabled |
| `:app:assembleDebug` | BUILD SUCCESSFUL; installable APK produced |
| `:app:lintDebug` | Passed: 0 errors, 1 advisory about a newer Gradle version |
| Standalone APK | Installed through ADB; `MainActivity` cold launch returned `Status: ok`; no AndroidRuntime crash output |
| APK manifest inspection | Minimum API 29, target/compile API 37; no INTERNET permission; arm64-v8a, armeabi-v7a, x86 and x86_64 |
| Visual fixture | Direct instrumentation fixture passed; fictional PNG retrieved and visually inspected |
| `git diff --check` | Passed |

There are **23 unique automated tests**, plus a repeated direct rendering-fixture execution. Domain fixtures cover decimal precision/formatting, overdrafts, transfer snapshots, date/revision ordering, missing FX/history, stale carry-forward and empty books. Device fixtures cover SQLCipher persistence, wrong keys, Keystore wrapping/tampering, missing-key preservation, concurrent corrections, foreign keys, injected transaction rollback, retained audits and migration. UI fixtures cover add/update/correction/edit/recreation, demo isolation, empty states, amount hiding, persisted access gating and `FLAG_SECURE`.

Reports are generated under `core/finance/build/reports/tests/test`, `core/database/build/reports/androidTests/connected/debug`, `app/build/reports/androidTests/connected/debug`, and `app/build/reports/lint-results-debug.html`.

APK: `app/build/outputs/apk/debug/app-debug.apk` (development signing).

SHA-256: `5d443357eacc39f88def26beb4ed8d7a59c2f59faeb1c225c4ea997b3a5ec13c`.

[Fictional dashboard rendering](images/demo-dashboard.png)

## Limits and security concerns

* **Portable backup/restore is not available.** Uninstall, clear-storage, device loss or Keystore key loss can make records unrecoverable. The app explains this. No insecure fallback/export was introduced.
* Access-gate persistence and the secure-window flag are automated; successful/cancelled native biometric/device credential dialogs and hardware Keystore guarantees still need physical-device validation. App lock gates the interface; the database wrapping key is not authentication-bound.
* Minimum API 29 is declared and dependency-compatible; runtime tests were on the available API 36 emulator. API 29, API 37, 16 KB devices, TalkBack, large-font/device-layout matrices and release signing/security review remain unverified.
* Foreign-currency balances are stored, but INR conversion is unavailable until FX observations exist. Totals cover bank accounts only. Other assets, monthly cash flow, forecasts, calculators, protection and public data are deliberately not implemented yet.
* Gradle 9.6 remains pinned to AGP's documented default and within Kotlin's documented Gradle range; the 9.8.2 lint notice is informational. The built project uses Java 21. The machine's incorrect global SDK environment variable was not changed; `local.properties` and build-session environment values supplied the working SDK path.

See [security notes and official dependency sources](SECURITY.md) for the threat model and remaining release work. No existing user financial files were reset or replaced. Device tests ran in a newly created isolated headless emulator.

## Next milestone

Recommended: **Milestone 2 — Financial Portfolio**, starting with opening positions, investment transactions, cash/snapshot reconciliation and deterministic holdings fixtures. It has **not** been started and requires explicit instruction.
