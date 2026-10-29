# Remaining project work

The requested six milestones have functional software implementations. Remaining release acceptance includes the user's guided phone checks, physical ARM/16 KiB assurance and independent security review; emulator evidence cannot complete those items.

## Current bounded follow-up: 0.6.8

Implemented concise follow-up entry for transactions, valuations and loan/deposit events, native calendars including deposit maturity, and the installed-version label. Financial save payloads, core modules, schema 7 and crypto formats are unchanged. 129 JVM cases and all 17 affected native cases passed; the three render cases passed again after the final explicit maturity-payout title. Actual debug/minified Release builds and both lint checks passed, with zero reported lint errors/warnings in this offline run. Both matching APKs are verified. Existing-key production signing, independent package checks and byte-identical comparison of 98 application payload entries completed on 29 October 2026; no phone installation or personal-data changes occurred. See [release notes](RELEASE_0_6_8.md), [plan](DAILY_UPDATES_0_6_8_PLAN.md) and [evidence](verification/daily-updates-068/verification.json).

## Completed previous follow-up: 0.6.7

Completed the first-party financial-history restore review: reject transactions before the known opening position and valuation markers outside eligible recorded history; initialize new snapshots with system-owned zero coverage. The original three regression probes failed before repair. Final verification passed: 129 JVM tests, 73 encrypted storage/backup/migration tests and ten affected native workflows. Debug/minified builds and lint passed. Existing-certificate signing, independent package checks and byte-identical comparison of 98 application payload entries passed. Both matching APKs are available; schema 7 and crypto formats are unchanged. See [review](HARDENING_0_6_7.md), [release notes](RELEASE_0_6_7.md) and retained evidence. This is not an independent security audit or physical-device acceptance.

## Completed previous follow-up: 0.6.6

1. Push the verified 0.6.5 source to GitHub (completed at cefa73b).
2. Implemented and native-tested: simplify monthly cash flow, received income, forecast scenarios, goals and calculators. Keep basic inputs first, save/calculate actions reachable, assumptions/notes expandable, and summaries of hidden saved values visible.
3. Implemented and native-tested: add an explicit bulk forecast-return action for current investment holdings; never silently replace contract rates or specific assumptions.
4. Verified unchanged: retain schema 7, all financial formulae, decimal validation, historical revisions and completeness requirements. No inferred financial transactions or automatic device writes.
5. 129 JVM tests and all 47 distinct native UI cases have final passing results. Visual review covers 75 fictional captures. Final debug/release builds and lint passed. Existing-key production signing and independent package/payload verification completed on 29 October 2026; both matching APKs are available.
6. Refresh current progress and guided checks without marking unexecuted physical checks as passed.

## Acceptance still requiring external evidence

- User-run phone checks: current-record preservation, offline cold-open, device lock/cancel, TalkBack and large fonts, protected screenshots/recents, export readback and preview-cancel. Full restore uses an empty receiver only.
- Physical ARM/16 KiB execution and OEM interrupted-operation/performance assurance. Existing x86 emulator and ELF/ZIP alignment evidence do not establish these.
- Independent security review and independently retained signing key and backup passwords. No secrets or personal financial files belong in GitHub.

## Newly requested: statements and investment imports

The user requested monthly bank/card CSV/Excel imports and exploration of free Groww/consolidated investment access, and confirmed HDFC Bank, Tata Neu HDFC Bank Credit Card, and both Groww mutual funds and stocks/ETFs. [Research and proposed implementation](STATEMENT_IMPORT_AND_INVESTMENT_ACCESS.md) records verified official sources, HDFC's documented bank/card export options, the Groww zero-cost trial versus paid-plan distinction, the documented stock endpoint and missing market quotes, CAS file routes and regulated AA access constraints. This is a new requested feature direction, not functionality already present in 0.6.8. On 30 October, both supplied exports were inspected locally, read only: they are binary XLS with different transaction layouts. [Verified format contract](HDFC_STATEMENT_FORMATS.md) records generic headers, exact bank reconciliation checks, differing transaction/value dates, cross-month card coverage and implementation gates without personal financial rows. No account credentials were accessed and no plan was subscribed to.

Prioritize a verified, bounded Android XLS reader for these actual downloads, with native CSV/delimited support, preview/mapping, reviewable categories, overlap/duplicate handling, separate spending/card-settlement cash flows and safe monthly-summary revision application. Android compatibility, parsing containment and canonical decimal conversion of native numeric cells remain gates; the local inspection reader is not an app dependency. Add persistence, explicit migrations and complete portable-backup/restore support together. Then add holdings/CAS file adapters and, only if actual no-cost entitlement is verified, an optional official Groww stock-holdings connector. Preserve manual/offline operation. Do not introduce a cloud backend, analytics or unverified market-price source to obtain imports. The separate physical-device and independent acceptance requirements above remain outstanding.
