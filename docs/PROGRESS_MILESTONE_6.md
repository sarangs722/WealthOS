# Milestone 6 — backup, recovery and release hardening

**Latest signed follow-up: [0.6.6 / code 12](RELEASE_0_6_6.md).** Remaining planning/cash-flow/calculator forms are simplified; 129 JVM and 47 distinct native UI cases have passing results. Debug/minified builds and lint passed; core mathematics/schema 7 are unchanged. Both matching APKs are verified. Existing-certificate signing, full package checks and byte-identical comparison of 98 payload entries completed on 29 October 2026. Physical/independent checks remain pending.

**Previous verified data-entry refinement: [0.6.5 / code 11](RELEASE_0_6_5.md).** Name/current-value investment snapshots and optional reported figures are implemented with schema 7, same-holding tracking upgrades and old-backup migration. 129 JVM, 68 encrypted persistence, seven Android network and 43 distinct native UI cases passed. The signed same-key artifact is verified. The 0.6.6 remaining-form follow-up is recorded in [remaining work](REMAINING_WORK.md) and its [release notes](RELEASE_0_6_6.md); packaging status there is authoritative. Physical checks and independent review remain pending.

**Previous verified refinement: [0.6.4 / code 10](RELEASE_0_6_4.md).** The five main destinations, Add flow, forms and detail hierarchy were redesigned for clearer everyday use. 121 JVM tests and 37 final API 29 UI tests passed, with 30 fictional light/dark/large-text screenshots. Builds/lint, permanent signing/payload verification and both native in-place updates passed; existing fictional records, UID/data directories, history and holdings were retained. Financial mathematics, schema 6 and encryption are unchanged. The phone remains untouched by the agent; the user's guided physical checks remain pending. No further financial milestone was started.

**Previous verified patch: [0.6.2 / code 8](RELEASE_0_6_2.md).** The follow-up review fixed dark-chart contrast/line identification and reproduced/fixed recovery of privacy flags from an interrupted atomic write. Eight targeted cases passed on API 36 and three recovery cases passed on API 29; the full baseline was not rerun. The signed same-key update preserved the fictional vault/history. The user chose to run [guided physical-device checks](DEVICE_CHECKS.md) personally; those results are still pending. This is a bounded first-party security review, not an independent audit.

The original 0.6.0 milestone verification and subsequent runtime history follow; their historical counts/artifacts are retained separately from the latest patch.

2026-10-28. Software hardening, recovery fixtures and native shrunk-release smoke verification are complete. The user subsequently completed permanent local signing; [the installable 0.6.0 APK and independent package/signature verification](PRODUCTION_RELEASE.md) are now recorded. Physical-device/independent review and a user-verified backup-based migration remain. No further milestone has been started. The personal phone and its financial records were not accessed or modified; device fixtures used the explicitly named WealthOS test emulators.

Release follow-up: [local signing tools, coexisting release build and verified results](RELEASE_READINESS.md) were prepared with 19 host signing checks and a native API 29 coexistence/restore smoke; the 204-test milestone baseline below is unchanged and is not claimed as rerun for that follow-up. Permanent signing is now complete under the user's locally entered credentials. Physical/independent reviews still remain.

Further [16 KiB runtime verification](16K_VERIFICATION.md) verified 83 existing cases across API 35/36 and native minified restores on both versions. The current 18-case UI harness subsequently passed completely on API 35 using a separately verified stable emulator 37.2.12; its 18 API 36 cases had already passed. All financial expectations and the byte-identical product APK are preserved. Earlier API 35 ART/JIT crash causation remains unconfirmed; Google/system-process failures and a System UI ANR still limit that experimental image's stability evidence. Physical-device validation remains required. These are repeated platform checks of baseline cases, not new financial features or a claim that all platforms are cleared.

The exact same current APKs then repeated the FD case three times on the older emulator 36.3.10: two passed and one crashed in Android's `ReferenceQueueDaemon`. That comparison is explicitly retained as failed. Final debug/test/minified-release assembly and debug/release lint passed in 36 seconds with unchanged artifact hashes and zero lint errors. The runtime investigation changed only host verification tooling and documentation; it did not alter financial source or user data.

## Implemented

* Settings → Manage backups: password confirmation, user-chosen native document destinations, encrypted output/readback verification, authenticated import, record-count/date preview and explicit replacement acknowledgement. Passwords are transient and clear on background/recreation; demo mode cannot export or restore financial data.
* Complete, lossless logical snapshots of all 31 financial/audit/public-data tables in one transaction. UUIDs, decimals, dates, revision history, event anchors, scenarios/goals and source-application provenance are retained. No live SQLite/WAL copying or plaintext temporary backup is used.
* Portable PBKDF2-HMAC-SHA256 / AES-256-GCM format independent of the device key. Strict authenticated headers, bounds/types, trusted packaged schema, encrypted staging, SQL integrity/foreign-key and domain/reconciliation validation precede live changes. Unknown versions, damaged files and wrong passwords fail without replacement.
* One transactional restore, current-vault fingerprint protection against edits after preview, verified encrypted pre-restore safety copies, rollback on failure/cancellation and a restore audit event. Restoration disables public networking and retains local device privacy settings. Safety copies are never silently pruned.
* Activity-owned system-picker callbacks survive removal of the financial composition by the optional access lock. Only ciphertext/selected URI and a pending NAV binding survive in the ViewModel; passwords and restore previews do not. Expired operations after process death require preparation again. The existing selected-fund NAV picker uses the same lifecycle ownership.
* Fixed a verified midnight boundary: public observation dates use the source calendar (Indian references/manual dates: Asia/Kolkata; ECB: Europe/Berlin), independently of the UTC retrieval timestamp. A valid Indian date is no longer rejected while UTC is still yesterday. Boundary tests retain rejection of a date beyond its source's day.
* Reproduced and fixed concurrent first-key creation across independent loader instances: a process-wide lock now protects the entire read/create/wrap/write operation. Eight simultaneous loaders must return the same persisted random key. The test failed against the old instance-only synchronization and passed with the fix on API 29 and 36; existing key format, alias and bytes are not regenerated by this change.
* Debug and minified/resource-shrunk release builds, debug/release lint, native ELF/ZIP 16 KiB alignment checks, guarded upgrade/reinstall scripts, backup/recovery/signing documentation and a two-worker build default for this 16 GiB host.

## Architecture and storage

`core:security` is a pure JVM module using JCA with no additional cryptography dependency. The financial engine remains independent of Android UI/network APIs. `RoomBackupRepository` owns consistent snapshots, staging, validation and transactional replacement; `BankViewModel` and native Compose/Activity document contracts own the user flow. Schema **6 is unchanged**; the explicit existing migrations remain in use, without destructive fallback. Packaged trusted schemas provide staging DDL; imported files never supply executable SQL.

The existing SQLCipher dependency was updated from 4.19.1 to stable [4.20.0](https://www.zetetic.net/blog/2026/10/27/sqlcipher-4.20.0-release/), released October 27, with SQLite 3.54.1. The database key/Keystore alias and application identity stay unchanged. See [format/security boundaries](BACKUP_FORMAT.md), [security sources](SECURITY.md) and [release/recovery procedure](RELEASE.md).

## Executed automated verification

| Suite | Distinct tests | Result |
| --- | ---: | --- |
| Financial domain, including source-calendar boundaries | 99 | Passed |
| Portable crypto/typed payload, including independent .NET Unicode fixture | 12 | Passed |
| Network parsers/request/transport JVM fixtures | 10 | Passed |
| Android official-format parser validation | 7 | Passed on API 29 and 36 |
| Encrypted database/repository/backup/migration/key fixtures | 58 | Passed on API 29 and 36 |
| Native UI/privacy/workflows | 18 | Passed on API 29 and 36 |

**204 distinct tests verified across JVM and both Android APIs.** The 203-case baseline ran as a complete matrix (285 executions); the final key-startup fix was checked with the focused 3-case key-storage class on both APIs, retaining the old tamper/missing-key cases and adding concurrency. Reports are retained under `docs/verification/milestone-6-*`. The activity-owned backup flow also passed a separate 3-case API 29 run. Additional focused reruns are not added to the distinct-test total.

The 11 new backup database cases cover every populated table, a different encryption key, exact decimals/source provenance, wrong password/tampering, incomplete tables/types/keys/relationships, exponent abuse, edits after preview, failed safety IO, partial insert/audit rollback, cancellation and retained safety copies. Backup UI cases cover password confirmation/clearing, export readback, wrong-password rejection, preview cancellation/acknowledgement, history preservation, lifecycle ciphertext ownership and a document provider returning truncated data. Existing financial and persistence fixtures remain unchanged in their expected numeric results.

Actual integration fixtures on **WealthOS29 / Android 10 / API 29**:

* Preserved signed 0.5.0 APK → populated bank revisions, opening holding/cost, received income and saved forecast → `adb install -r` final 0.6.0 → 2 passing seed/retention tests and successful launcher. The signing certificates match.
* Fresh 0.6.0 → fictional bank revisions/fund → encrypted export/readback → archive only ciphertext outside the app → actual uninstall/reinstall → return that file → validated/confirmed restore → 2 passing tests. Protected-key envelope fingerprints differ. Bank values/revisions, FIFO units/cost, NAV and expected net worth ₹1,152.52 were retained; networking is off and a safety copy exists.

Both upgrade and actual-reinstall fixtures were repeated against the final APK after key-startup hardening, not merely against the earlier candidate.

Native shrunk-release checks on **WealthOS / Android 16 / API 36** used the real system document picker and a disposable emulator device credential. The encrypted Android 10 export was selected from Downloads, the return was gated by the access lock, PIN authentication preserved the selected URI, and authenticated/acknowledged restore produced the expected ₹1,152.52 fictional wealth. A new export through the actual save picker survived the same lock transition and reported successful readback; independent .NET GCM decryption authenticated the resulting schema-6 payload. At 200% system font size all five backup inputs/actions remained reachable by scrolling. The original font and disposable PIN were cleaned up.

The complete native SAF flow used the shrunk candidate before the isolated initial-key mutex fix; the **final** shrunk APK was then installed in place, cold-launched, and reopened that populated vault with the same expected wealth after PIN removal. Both artifact hashes are recorded in `milestone-6-native-release.json`. These emulators use 4 KiB OS pages: 16 KiB ELF/ZIP alignment is verified, while physical 16 KiB runtime is not claimed.

UI tests require a clean synthetic fixture: an earlier API 36 rerun correctly saw previously recorded fictional income in its monthly total. The clean installation passed without changing the expected accounting result. Smaller-screen tests explicitly scroll lazy bank forms to off-screen fields.

## Actual build status

Final build command completed successfully: debug APK/test APK, release APK, debug lint and release lint. Both lint reports contain **0 errors and 1 informational version warning**: newer Gradle 9.8.2 is advertised, while 9.6.0 remains within the verified AGP/Kotlin compatibility range. The SDK reports that SQLCipher native symbols are packaged as supplied where stripping tools are unavailable; all shipped native ELF segments and uncompressed ZIP entries pass the 16 KiB alignment checks.

| Artifact | SHA-256 |
| --- | --- |
| Installable development APK `.tools/milestone-6.apk` | `71baccb417015beac48139fc2e4855c423ab46655d0e86ce720ece30c2fe5cd0` |
| Shrunk unsigned release APK | `242f5281914b4149ef8aaea127acee7dc3400cd80fbd812c27eeeb7d88b1599d` |
| Separate final lab-only shrunk APK, signed with existing development key | `da400ed10bd09209cbfb9b5b616bfd2e7ff7486fe93afb80c0848612307a91d4` |

Application `com.wealthos.app`, version 0.6.0 / code 6, schema 6, compile/target 37, minimum 29. Existing development signer SHA-256: `dd7d62a4506fd0cd64cb1dddaa11a56f838694a64b7ec8c6f79f6461c5589f73`. The preserved 0.5 APK remains unchanged. [Verified native fictional demo capture](screenshots/milestone-6-backup-demo.png); screenshot protection was restored immediately after the test-only capture.

## Security/data-integrity boundaries and remaining release work

This is a development-signed update and release-readiness work, not a production signing identity or independent security certification. The production release artifact is deliberately unsigned; the separate lab APK uses only the existing debug key. No production secret, store publication, private-data upload, tracker or backend was introduced.

Portable format 1 currently accepts schema 6 and limits encoded data to 8 MiB, 50,000 total rows and 64 KiB per text cell. Oversize/incompatible data fails explicitly, without truncation or record replacement. A forgotten password is unrecoverable. Safety files consume local storage until explicitly handled; export them to survive uninstall. An already unopenable vault remains preserved and cannot currently enter the restore screen; use a fresh installation/device with a separately validated portable backup. Plaintext necessarily exists in process memory, and immutable strings/crypto providers cannot promise secure erasure. A killed process can leave an encrypted staging orphan. App lock is an interface gate, not per-use authentication of the wrapping key.

Physical/OEM biometric behavior, hardware Keystore backing, actual physical 16 KiB execution, device power-loss tests, TalkBack/localization and battery/performance profiling still need device review. Emulator cancellation/rollback and packaging checks do not certify these. The dependency/source review is bounded and is not a comprehensive independent supply-chain audit.

Recommended next work: retain independently protected copies of the now-created production signing key and passphrase, rehearse the documented backup-based migration from the development identity, complete physical-device/accessibility/performance and independent security review, then decide on wider production use. The [signed local artifact](PRODUCTION_RELEASE.md) is available; no personal installation was migrated. Do not uninstall a personal installation to bypass a signing mismatch or before validating an independently retained backup.
