# Remaining project work

The requested six milestones have functional software implementations. Remaining release acceptance includes the user's guided phone checks, physical ARM/16 KiB assurance and independent security review; emulator evidence cannot complete those items.

## Current bounded follow-up: 0.6.7

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

No new financial feature milestone, cloud backend, account/analytics service or unverified market-price source is introduced by these follow-ups. The remaining acceptance work requires external evidence rather than further unrequested feature expansion.
