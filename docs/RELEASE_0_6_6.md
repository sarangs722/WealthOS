# WealthOS 0.6.6 - simpler planning and cash flow

Version code 12. Database schema 7, encryption, portable-backup format and all financial mathematics are unchanged from 0.6.5. Debug and minified release builds are verified. Production signing is waiting for the existing-key passphrase in the local signing window; no signed 0.6.6 release is claimed yet.

## Changes

- Cash flow leads with income, living expenses and contributions. Premiums/loan/other payments expand with a summary of entered amounts. Completeness remains explicit: the action says Save draft until income and outflows are confirmed. Missing information is not silently declared complete.
- Received-income forms put optional payslip details and notes behind compact rows. Same-month corrections still retain history and require explanations.
- Scenarios lead with name, contribution, horizon, return and inflation. Funding/budgets, growth, individual assets, relevant currencies and withdrawals expand separately. All existing values remain stored; no new return/rate recommendations are prefilled.
- An explicit Apply this return to current investments action updates assumptions for funds/stocks/ETFs/gold/commodities. Editing the global return alone does not update these holdings. Property/retirement assumptions and deposit/loan contract rates are preserved.
- Goals lead with name, amount, a native date picker and today's-money choice. Funding, growth, currency and reference-date settings have a compact summary. Calendar entry supports the existing calculator date envelope through 2200.
- Calculators share the fixed action layout. Results scroll into view above inputs; currency/conventions/timing stay accessible under Options with visible summaries. Amortization is expandable. Editing inputs hides stale output.

## Executed verification

- 129 JVM tests passed: 107 finance, 12 security and 10 network. Core source is unchanged; the entire calculateTool body matches 0.6.5 byte-for-byte after newline normalization.
- 47 distinct native UI cases have final passing results on guarded WealthOS29/API 29. The full run passed 45/47; the existing loan test asserted before the new balance rendered, and the new scenario test selected the empty-state button despite saved scenarios being present. The corrected portfolio/scenario classes passed 4/4 with the same financial amounts. Full failure logs and latest per-case passing results are retained.
- Includes unchanged 60,000 surplus/50,000 remaining-cash, 95,000 outstanding-loan, goal-revision, zero-interest SIP and bank/history assertions. The new persisted-assumption case proves bulk application is explicit, preserves property/post-maturity assumptions and the FD's contracted 7% rate, and does not alter current wealth.
- 75 fictional native screenshots cover light/dark/150%-font forms and existing navigation. EMI at zero interest renders 100 per month for 1,200 over 12 months, with its schedule separately accessible. Real MainActivity screenshot protection remains enabled.
- The 0.6.5 encrypted repository/backup/migration tests were not repeated for this UI-only change. Physical-phone checks, physical ARM/16 KiB assurance and independent security review remain pending. No personal phone records were accessed or changed.

## Build and artifacts

Debug and minified release builds passed. Debug/release lint each have zero errors and one existing Gradle-version warning. The unchanged development certificate verifies successfully. The unsigned release passed ZIP/ELF 16 KiB alignment for all eight native libraries; production signature verification remains pending.

- [Original WealthOS development-app update](../.tools/WealthOS-0.6.6-development.apk), same development identity. Install over the matching existing app; retain its storage.
- The unsigned Release candidate is not installable. The local WealthOS 0.6.6 signing prompt requires the existing production-key passphrase; no new key is created and no password belongs in chat or GitHub.
- [Verification and artifact SHA-256 values](verification/remaining-ui-066/verification.json), [latest result for each UI case](verification/remaining-ui-066/ui-case-results.json).
- Fictional previews: [scenario](screenshots/remaining-066-scenario-light-1.0.png), [cash flow](screenshots/remaining-066-cash-light-1.0.png), [goal](screenshots/remaining-066-goal-dark-1.0.png), [SIP](screenshots/remaining-066-sip-light-1.0.png), [large-text EMI result](screenshots/remaining-066-emi-result-light-1.5.png).

Physical checks and independent review are tracked in [remaining work](REMAINING_WORK.md) and [the guided checklist](DEVICE_CHECKS.md). These are not marked complete from emulator results.
