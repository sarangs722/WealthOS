# WealthOS

**Current candidate: [0.6.9 statement spending](docs/RELEASE_0_6_9.md)** adds on-device HDFC Bank/Tata Neu HDFC XLS and matching CSV imports, monthly review, overlap handling, encrypted history and schema-8 backup/restore. Existing-key production signing remains pending; the signed update below remains available.

**Latest signed update: [0.6.8 easier everyday updates](docs/RELEASE_0_6_8.md)** simplifies investment transactions, price/value updates and loan/deposit events, adds native date selection and displays the installed version correctly. 129 JVM and 17 affected native cases passed; three render cases passed again after the final maturity-title clarification. Debug/minified builds and both lint checks passed with zero reported errors/warnings. Both matching APKs are ready; existing-certificate release signing, independent package checks and byte-identical comparison of 98 application payload entries passed.

**Previous signed update: [0.6.7 financial-history restore hardening](docs/RELEASE_0_6_7.md)** retains its verified release artifact. Schema 7 and the existing signing identities are unchanged. [0.6.6](docs/RELEASE_0_6_6.md) provides the shorter cash-flow, scenario, goal and calculator forms.

Native, offline-first Android personal wealth tracker. **Previous data-entry refinement: [0.6.5 quick entry](docs/RELEASE_0_6_5.md)** starts investment holdings with name and current value, keeps other figures optional and uses shorter native forms. The signed update retains the existing release identity; [0.6.4](docs/RELEASE_0_6_4.md) is retained. **Milestone 6 (0.6.0)** adds password-protected portable backups, validated transactional restore, encrypted pre-restore safety copies and release hardening. Existing bank, portfolio, income, protection, planning and optional public-data features remain local and offline-first.

**Updating from older versions:** install the matching verified APK over the existing app, retaining the same application/signing identity. Do not uninstall or clear storage: the encrypted vault and device key are reused, and explicit migrations in the current source reach schema 8. After updating, create a portable backup and validate its original password/records before relying on it. Network refresh defaults off.

## Run

Open this folder in a compatible current Android Studio, select a Java 21 Gradle runtime, install Android SDK Platform 37 and Build Tools 36.1.0, then run `app` on Android 10+ (API 29+). The compile and target SDK is 37. `local.properties` must point to your SDK; it is intentionally ignored by Git.

PowerShell example for this development machine:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio1\jbr'
$env:ANDROID_HOME = 'C:\Users\saran\AppData\Local\Android\Sdk'
$env:ANDROID_SERIAL = 'emulator-5554' # Explicitly restrict instrumentation to the isolated WealthOS AVD.
.\gradlew.bat :core:finance:test :core:imports:test :core:security:test :app:assembleDebug :app:lintDebug
.\gradlew.bat :core:network:testDebugUnitTest :core:network:connectedDebugAndroidTest
.\gradlew.bat :core:database:connectedDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

The first build downloads pinned dependencies and requires internet. After installation, app functionality requires no connection. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Debug signing is for development only.

For device regression tests, [Verify-Regression.ps1](scripts/Verify-Regression.ps1) checks the isolated AVD name/API and sets an explicit emulator serial (WealthOS / API 36 / 5554, or WealthOS29 / API 29 / 5556). Instrumentation fixtures create synthetic records; use a fresh test installation. Run from the repository root with `-SdkDirectory` and `-JavaDirectory` pointing to the paths above. Optional `-ResetSyntheticFixture` removes only this app/test package from the named test emulator before running; use it solely to discard known synthetic fixture data, never for personal records or an upgrade-retention check. The [reinstall fixture](scripts/Verify-PortableReinstall.ps1) requires an empty AVD and proves recovery after an actual uninstall with a different protected key.

## Use

1. Add a savings/current bank account, select native currency, and enter a known balance and its financial date.
2. Open the account to append balance observations or edit its nickname/bank/type. Correcting a date requires a note and preserves earlier revisions.
3. Portfolio → **Add** → a record type opens Quick Add. Mutual funds, stocks, ETFs, gold and commodities need only a name and current value. Invested amount, units and NAV/price are optional; alternatively calculate a snapshot from units × price. Choose the financial date of the copied figures. Reported invested amount is not FIFO cost or transaction history. **Track purchases & sales** can start detailed tracking later on the same holding, preserving earlier snapshots without creating another asset or cash movement. Only add subsequent transactions; missing earlier history stays unknown.
4. EPF/PPF/NPS and properties use dated observed totals. Enter only your attributable property value. FD/RD contracts retain their agreed rates and distinguish observed totals from generic gross estimates; only actual RD installments/payouts count. Loans start from known principal and separate actual principal, interest and fees from estimated monthly schedules.
5. Link cash only when appropriate. Choose **Apply provisional cash adjustment** or **Balance already reflects this cash flow** explicitly. Later authoritative bank observations absorb flows through their date. Brokerage accounts contain cash only; their statement portfolio totals are separate reconciliation references. Internal transfers save both legs atomically and currently require matching currencies.
6. Home leads with INR net worth, assets, debt and quick access to cash flow and emergency funds. Open **Net worth details** for liquidity/allocation/currency exposure, **Wealth insights** for history/returns, or the review row for data health. Foreign positions retain native units/currency. Enter dated manual FX in Portfolio → **Portfolio options** → **Exchange rates**; missing prices/rates leave consolidated totals unavailable. Cross-currency cash conversions are not invented from valuation FX rates.
7. Home → **Cash flow** → **Income sources & salary** records expected net rates separately from actual monthly receipts. Salary gross minus deductions must match net. The monthly summary uses either a manual income total or recorded sources, with explicit completeness and separate expenses, principal/interest, premiums and investments. These summaries do not post bank cash movements.
8. **Protect** sets an INR reserve target and earmarks existing liquid assets, capped at available value. Add term/health policies, recorded cover periods, premium schedules and cumulative installment settlements. Coverage and earmarks never create net worth. Dates need insurer confirmation; there are no background reminders or automatic renewals.
9. **Forecast** saves pessimistic/base/optimistic assumptions independently. Choose externally funded contributions or cash-flow funding, asset-specific returns/contributions, loan payments, salary/expense growth, deposit reinvestment, FX changes and withdrawals. Read nominal and inflation-adjusted estimates; projections stop at unfunded cash. Scenario and goal revisions preserve earlier assumptions and never modify financial balances.
10. **Financial goals** distinguishes future targets from money at an explicit reference date. Declare existing earmarks, calculate required monthly saving, and deactivate completed/paused goals. Goal earmarks are not additional wealth; review their availability rather than assuming past returns.
11. **Tools** provides inflation/purchasing power, SIP/step-up, lumpsum, CAGR/XIRR, FD/RD, EMI/amortization, prepayment, retirement/withdrawal, goal, manual FX and real/nominal calculators. Their assumptions and rounding rules appear with results. Editing inputs hides stale output until recalculation.
12. Home → **Wealth analytics & investment returns** exposes recorded wealth movement and scoped investment performance. Incomplete opening history, valuations or dated FX leave returns unavailable. Non-conventional multi-sign-change cash flows do not receive an arbitrarily chosen XIRR root.
13. Settings contains amount obscuring, optional device access lock, recovery limitations and a read-only fictional demo. Demo data never enters the personal database.
14. Settings or the Home footer → **Public data & sources** enables optional public refreshes. Each source exposes dates, attribution, validation and stale/last-known status. Network is off by default; refreshing only caches public references. Review and confirm a cached FX/NAV to post a dated valuation. Same-day manual revisions are retained; older financial dates cannot overwrite newer values. Economic references do not change forecasts or contracted rates.
15. For INR mutual funds, **Edit NAV binding** records the exact AMFI code and optional ISIN; **Import selected fund NAV** reads your personal file and retains only that fund quote. Verify the plan/option and identity before applying. AMFI is not automatically downloaded; imported publisher identity is unauthenticated. Stock/ETF/commodity prices and bank-rate references remain manual. See the verified source/usage decisions below.

16. Settings → **Manage backups** creates an encrypted portable copy of all financial records/history, goals, scenarios, public references/applications and audit. Choose the file destination and retain its unique password separately. To restore, choose the file, enter its original password, validate the preview and explicitly acknowledge replacement. Restoration replaces the receiving financial vault, preserves local privacy settings, disables public networking and requires an encrypted safety copy first. Export safety copies to retain them after uninstall. Backups are unavailable in fictional demo mode.

Records use SQLCipher and a Keystore-protected random key. Portable files use password-derived AES-256-GCM independently of that key. A separately retained, validated backup **and its original password** are required for recovery after device/key loss or uninstall. Forgotten passwords cannot be recovered. Follow the [backup/recovery procedure](docs/RELEASE.md); keep source statements/contracts as independent financial evidence. See the verified report for signed-package evidence, physical-device results and review limits.

## Documentation

* [Requested statement imports and free investment-access research](docs/STATEMENT_IMPORT_AND_INVESTMENT_ACCESS.md) — proposal only; current 0.6.8 has no statement importer or Groww connection.
* [WealthOS 0.6.8 entry improvements and current artifact status](docs/RELEASE_0_6_8.md)
* [Previous signed WealthOS 0.6.7 and verification](docs/RELEASE_0_6_7.md)
* [Guided phone checks to run personally](docs/DEVICE_CHECKS.md)
* [Original 0.6.0 signing and migration record](docs/PRODUCTION_RELEASE.md)
* [Milestone 6 verified progress and remaining release requirements](docs/PROGRESS_MILESTONE_6.md)
* [Portable format, bounds and restore architecture](docs/BACKUP_FORMAT.md)
* [Build, signing and recovery procedure](docs/RELEASE.md)
* [Coexisting release and local signing tools](docs/RELEASE_SIGNING.md)
* [Single-command signing setup with local password input](docs/RELEASE_SIGNING.md#single-command-local-setup)
* [Verified release preparation and coexistence results](docs/RELEASE_READINESS.md)
* [16 KiB runtime checks, Android 15/16 native restore and runtime limits](docs/16K_VERIFICATION.md)
* [Milestone 6 implementation plan](docs/MILESTONE_6_PLAN.md)
* [Public-data architecture and schema](docs/MILESTONE_5_ARCHITECTURE.md)
* [Verified public sources, formats and usage decisions](docs/MILESTONE_5_SOURCES.md)
* [Milestone 5 implementation plan](docs/MILESTONE_5_PLAN.md)
* [Milestone 5 verified progress and limitations](docs/PROGRESS_MILESTONE_5.md)
* [Analytics/forecast architecture and financial conventions](docs/MILESTONE_4_ARCHITECTURE.md)
* [Milestone 4 implementation plan](docs/MILESTONE_4_PLAN.md)
* [Milestone 4 verified progress and limitations](docs/PROGRESS_MILESTONE_4.md)
* [Income/protection architecture and accounting policy](docs/MILESTONE_3_ARCHITECTURE.md)
* [Milestone 3 implementation plan](docs/MILESTONE_3_PLAN.md)
* [Milestone 3 verified progress and limitations](docs/PROGRESS_MILESTONE_3.md)
* [Portfolio architecture and valuation policy](docs/MILESTONE_2_ARCHITECTURE.md)
* [Milestone 2 implementation plan](docs/MILESTONE_2_PLAN.md)
* [Original foundation architecture](docs/ARCHITECTURE.md)
* [Security and verified dependency sources](docs/SECURITY.md)
* [Milestone 2 verified progress and limitations](docs/PROGRESS_MILESTONE_2.md)
* [Milestone 1 verification](docs/PROGRESS.md)

This repository contains no online banking credentials, ads, user-tracking analytics, backend or background financial uploads. Financial fixtures are synthetic. Optional public requests contain no personal records. The user completed the local production-signing workflow; its private key remains outside the repository and Gradle. Physical-device and independent review requirements remain documented.
