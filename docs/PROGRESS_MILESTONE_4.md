# Milestone 4 — Analytics and Forecasts

Verified **27 October 2026**. Native APK **0.4.0**, version code **4**. Milestone 4 is complete; Milestone 5 has not been started.

## Implemented

* Transaction-investment native-currency contributed capital, realized/unrealized book gains, defined simple holding return, ACT/365 XIRR/CAGR where appropriate, and INR portfolio XIRR using dated FX for each flow. Opening-history, basis, valuation, FX and ambiguous-root limitations are explicit.
* Recorded wealth movement with actual input dates/status; existing net-worth history remains. Wealth movement is separate from investor return and does not invent income or causal attribution.
* Deterministic monthly forecasts with existing assets/liabilities, asset-specific returns/contributions, external contribution versus cash-flow funding, salary/annual growth, essential/discretionary inflation, premium budgets, loan payments, deposit maturity/reinvestment, withdrawals, active financial goals and independent FX assumptions.
* Independently saved pessimistic/base/optimistic scenarios, retained revisions, common 5/10/15/20/30-year comparison, nominal and purchasing-power estimates, monthly/annual detail and dated starting inputs. Unfunded cash stops a projection without fabricating borrowing or sales.
* Goals with explicit purchasing-power/earmark reference dates, future versus reference-money targets, declared existing allocations, required monthly saving, retained revisions and completion/deactivation. Goals and earmarks never add wealth or post cash automatically.
* Fifteen offline tools: inflation/purchasing power, SIP, step-up SIP, lumpsum, CAGR, XIRR, FD, RD, EMI/amortization, prepayment, retirement, withdrawals, goal saving, manual currency conversion and both real→nominal/nominal→real returns. Calculator results are invalidated after inputs change. Foreign FX references are not fabricated.
* Native Forecast/Tools destinations, scenario/goal builders and analytics from Home, privacy-aware outputs/charts and read-only memory-only fictional demo scenarios/goals. Calculations run off the UI thread.

## Architecture and schema

The same three modules/dependencies remain. Pure Kotlin DecimalMath/FinancialMath, Performance, Forecasts and Goals are independent of Android and networks. Decimal transcendental functions use 160 significant digits, bounded iterations and an API-29-compatible square root. Financial amounts never pass through binary floating point.

Explicit additive encrypted migration **4→5** creates five normalized planning tables. All 21 existing table definitions are unchanged. Scenario/goal revisions use stable UUID keys plus immutable row UUIDs; assumptions/withdrawals reference their scenario revision and instruments. Related rows/audit are atomic, corrections retain history, and book reads remain coherent. Existing migrations remain registered; destructive fallback is absent. Application ID, database path, device key alias and signing identity are unchanged. See [architecture and exact conventions](MILESTONE_4_ARCHITECTURE.md).

## Automated verification

| Check | Result |
| --- | --- |
| `:core:finance:test` | **91 passed**, zero failures/errors/skips |
| `:core:database:connectedDebugAndroidTest` | **35 passed**, zero failures/errors/skips, API 36 x86_64 |
| `:app:connectedDebugAndroidTest` | **12 passed**, zero failures/errors/skips, API 36 x86_64 |
| `:app:assembleDebug` | **BUILD SUCCESSFUL**, final APK generated |
| `:app:lintDebug` | **0 errors, 1 advisory**: newer Gradle available; compatible version remains pinned |
| Actual old-APK seed/update/retention | **OK (1 test)** seeding actual 0.3.0; **OK (1 test)** retaining bank history, holdings/cost and salary after same-signed 0.4.0 `install -r` |
| Updated standalone launch | **Status: ok** |
| Fictional native UI | Forecast screenshot captured after explicit demo selection and visually inspected; nominal/real outputs and chart render correctly |

There are **138 passing regression tests**, plus two directly executed installation-fixture tests. Parameter-gated installation fixtures are not counted as passed during an ordinary connected suite. Retained evidence: [test counts/results](verification/milestone-4-tests.json), [APK hashes/signing/update result](verification/milestone-4-apk-upgrade.json), [old APK seed](verification/milestone-4-old-apk-seed.txt), [retained records](verification/milestone-4-retained-records.txt), and [fictional forecast screenshot](images/milestone-4-demo-forecast.png).

Reference fixtures include Microsoft's published XIRR example; same-date aggregation; absent signs; multiple-root patterns; non-convergence; zero/negative growth; beginning/end SIP and step-ups; a 24-digit amount at the maximum 100-year/1000% growth envelope retaining paise; inflation/real-rate inverse; withdrawals/depletion; fixed-rate prepayment; native/FX-independent movement; actual-versus-contribution funding; deposit maturity conservation; loans' principal versus interest; goal reference-date preservation, overdue/completed goals, and existing financial regressions.

Device fixtures cover independent scenario revisions and exact reopen, child/audit rollback, orphan relationships, concurrent revision sequences, immutable goal currency, reference-date/completion persistence, no cash posting and populated encrypted schema 4 migration preserving bank, investment, salary, emergency, insurance and audit records. Legacy schema paths remain covered. Native tests exercise calculator results and stale-output invalidation, scenario/goal revisions/recreation and hidden forecast outputs alongside earlier bank, portfolio and protection workflows.

The first UI run found a test assuming a bank lazy-list row was already composed; tagged list scrolling fixes the selector without changing financial expected values. The initially proposed unfinished migration registration was rejected by automatic approval review and made no changes; a complete additive migration was implemented before the database version was changed. Build-time syntax/state-saver issues were corrected. Only final actually executed checks are counted above.

## APK and update

APK: `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `042474cc4ebea2c814b1f665fa2ff2a24b2197a25996184296d4d23a3ade1145`.

Certificate SHA-256: `dd7d62a4506fd0cd64cb1dddaa11a56f838694a64b7ec8c6f79f6461c5589f73`.

Install **over the existing same-signed app**; do not uninstall or clear personal storage. The actual 0.3.0→0.4.0 fixture retained bank observations/corrections of ₹100.10/₹200.20, five investment units, ₹400 acquisition cost (₹502.50 market value), and a ₹1,000 actual income receipt. The original encrypted vault/device key were reused. Schema 1/2/3/4 migration paths are also covered by encrypted fixtures. APK inspection confirms minimum API 29, compile/target API 37, four native ABIs and no INTERNET permission.

[Verify-Regression.ps1](../scripts/Verify-Regression.ps1) pins the emulator serial and runs domain/database then UI/build/lint in separate invocations. Use its optional reset only for known synthetic AVD fixtures. [Verify-ApkUpgrade.ps1](../scripts/Verify-ApkUpgrade.ps1) requires an empty isolated WealthOS AVD, compares certificates, seeds the actual old APK and updates with `install -r`; it never resets the installation being upgraded and supports retention-only resume.

## Security and known limits

* No new permission, network dependency, external upload, tracker, cloud account or background job. SQLCipher, Keystore wrapping, optional access gate, obscured amounts, screenshot protection and backup exclusions remain.
* **Portable password-protected backup/restore is still unavailable.** Uninstall, clear storage, key/device loss can make personal records unrecoverable. This remains development-signed/debuggable; portable backup, release hardening and independent security review remain Milestone 6.
* Tests target only the isolated WealthOS API 36 emulator with explicit ANDROID_SERIAL. No personal financial records or physical-phone test installation was used in Milestone 4. Physical biometrics, API 29/37 devices, 16 KB devices, TalkBack and font/device/performance matrices remain unverified.
* XIRR conservatively refuses multi-sign-change series because uniqueness is not established; it does not arbitrarily choose one numerical root. No time-weighted/benchmark return or tax performance is invented. CAGR is restricted to no-intermediate-flow cases. Existing Milestone 2 accounting/contract limitations remain.
* Forecasts are monthly estimates with explicit timing, before tax/unentered costs. They use entered fixed loan/deposit terms and premium budgets, not bank/insurer-specific future decisions. No automatic FX cash conversion, asset liquidation or borrowing funds a shortfall. Missing starting data remains unavailable.
* Saved scenarios are assumption revisions, not frozen financial snapshots. Corrected dated financial data and latest shared goal revisions affect recalculation. Goal earmarks are declared values, not inferred actual investment progress or verified asset reservations. Users must review availability and conflicting earmarks.
* Public-data adapters are outside this milestone. FX, inflation and return assumptions are manually entered; no source is presented as a guaranteed future return.

Recommended next: **Milestone 5 — External Financial Data**, only after explicit authorization.
