# WealthOS 0.6.5 - straightforward data entry

Version code 11, encrypted database schema 7. The release is signed with the existing production certificate. Physical-device validation remains separate from native emulator testing.

## Behaviour

- Mutual funds, stocks, ETFs, gold and commodities start with name and current value. Invested amount, units and NAV/price are optional. Units times price is an alternative input mode, rounded once HALF_EVEN at native currency scale.
- These are dated holding snapshots. They do not create purchases, cash flows, FIFO basis, XIRR or CAGR. Unknown information stays unknown. Each update retains its own optional figures; they are never silently copied from an earlier date. Same-day corrections need a reason and keep earlier revisions.
- Detailed tracking can start later on the same holding using known units and optional actual remaining cost/price. Preserve earlier snapshots and UUID; add no cash or duplicate asset. The starting total is never reused as a unit price. Transactions before that starting position are rejected.
- Short shared native forms keep Save visible above the keyboard. Compact calendar controls default to today, optional settings expand in small rows, and contextual help replaces paragraphs. Bank entry starts with name/balance. Retirement/property entry uses statement balance or attributable value. Income and insurance keep less-used settings under optional details; contract entry retains the essential agreed terms.

## Storage and compatibility

Explicit 6-to-7 Room migration adds four holding-observation fields without resetting records. Portable format-1 encryption is unchanged. Schema-6 backups are staged with their packaged trusted schema and migrated to 7 before validation/replacement. Schema-7 backups require an updated receiving app. Reported invested amount is not an automatic acquisition cost when switching tracking modes.

## Verification

- 129 JVM tests passed: 107 finance, 12 security, 10 network; all three tasks explicitly reran for the final build.
- 68 encrypted Room/repository tests passed on API 29, including schema migration, old portable backups, metadata round trips, unknown cost, same-day upgrades and failed-write rollback.
- 7 Android network integration tests passed.
- 43 distinct native UI cases passed across the full suite and targeted reruns. The first full run passed 39/43; one Property selector matched both the filter and Add sheet, and three calendar selectors used the wrong semantics. The corrected portfolio class passed all three cases; the corrected calendar/form class passed all three light/dark/150%-font cases. Financial expectations were unchanged. Raw failure logs and each test's final passing result are retained in [verification evidence](verification/quick-entry-065/verification.json).
- Native forms and navigation were captured in 57 fictional screenshots. [Light form](screenshots/quick-entry-065-fund-light.png), [dark form](screenshots/quick-entry-065-fund-dark.png), [large text](screenshots/quick-entry-065-fund-large.png), [bank form](screenshots/quick-entry-065-bank-light.png). MainActivity screenshot protection remains enabled.
- Actual debug and minified release builds succeeded. Debug/release lint: zero errors and one existing Gradle-version warning each.
- Independent signed-APK verification passed the existing certificate pin, single v3 signer, application ID, permissions/components, disabled OS backups/cleartext and eight native libraries' ELF/ZIP 16 KiB alignment. All 98 unsigned payload entries match the signed APK byte for byte; only the three expected signing entries and APK signing block were added.
- No phone was accessed, no personal vault was reset, and no new signing identity was created. The previous signed 0.6.4 APK retains its original hash. Experimental 16 KiB runtime/physical-device tests and an independent security audit were not repeated or claimed.

## Limits

Snapshot-only holdings require manual total-value updates; automatic NAV application still requires known transaction-derived units. Reported invested/value differences omit unrecorded distributions or withdrawals and are not annualised performance. Contract-specific rates/dates remain necessary for deposit and loan estimates. Physical-device acceptance and an independent security review remain pending.

## Artifacts

- [Signed WealthOS Release 0.6.5](../.tools/production-release/WealthOS-0.6.5.apk), `com.wealthos.app.release`, SHA-256 `de92af2f7eb2d519d3112e55a49b7440a884d3405a72c49a08f1f851aaa38898`.
- [Original development-app update](../.tools/WealthOS-0.6.5-development.apk), `com.wealthos.app`, SHA-256 `02cdc1ae0e5a1cd3c67947ae8b9b2804a4bb7efce4072de177307b99bd7530c1`.

Install the update matching your existing app. Keep the app installed and its storage intact so the existing Keystore-protected encrypted vault migrates in place. Schema-7 backups require an updated receiving app; older schema-6 backups remain supported.
