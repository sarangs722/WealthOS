# WealthOS 0.6.7 - financial-history restore validation

Code 13; schema 7 and portable backup/encryption formats unchanged. Bounded first-party review; independent security and physical-device acceptance remain pending.

## Fixed

Restore now rejects transactions before the known starting position, including earlier same-day sequence order. Valuation coverage must refer to an actual event eligible on that financial date; negative, nonexistent and future-event references are rejected. Per-instrument sequence/date indexes support backdated entry without repeated full-ledger scans.

Initial holding snapshots now receive zero-valued, system-owned coverage markers from their empty ledger. Financial amounts, dates and optional reported figures are retained. Existing records are not rewritten, no transactions/returns are invented and no database reset or migration is needed.

These were logical-validation gaps after successful decryption, not a break of authenticated encryption. Correctly encrypted fictional test payloads reproduced the gaps. Invalid imports fail in encrypted staging before live records or pre-restore safety copies change.

## Verification

- The three original probes failed against 0.6.6 before repairs. Expected financial values were not changed.
- 129 JVM tests passed: 107 finance, 12 security, 10 network.
- All 73 encrypted persistence/backup/migration tests passed on guarded API 29, including valid backdated history, old schema-6 backups, metadata round trips, different-key restoration and transactional rollback.
- All ten affected native workflows passed: backup/password/lifecycle handling, detailed investment positions, quick snapshots/upgrades and explicit NAV application. UI layout is unchanged from 0.6.6; its broader visual matrix is retained, not claimed as rerun.
- [Review details](HARDENING_0_6_7.md) and [raw evidence/hashes](verification/history-hardening-067/verification.json).

## Build and artifact status

Actual debug and minified release builds passed. Debug/release lint each have zero errors and one existing Gradle-version warning. The unchanged development signature verifies; the unsigned release passes ZIP/ELF 16 KiB alignment for all eight native libraries.

- [Original WealthOS development-app update](../.tools/WealthOS-0.6.7-development.apk), same existing development identity.
- [WealthOS Release update](../.tools/production-release/WealthOS-0.6.7.apk), signed with the existing production certificate. Independent package verification passed, including the permission/component inventory, OS backup exclusions and ZIP/ELF 16 KiB alignment for eight native libraries. All 98 unsigned application payload entries are byte-identical after signing; only three signing metadata entries were added. The previous 0.6.6 artifact remains unchanged.
- [Verification and artifact SHA-256 values](verification/history-hardening-067/verification.json).

Release APK SHA-256: `902c214de014392fd2c2c9c8feeff233360910600157e13a6d290782f7e8364d`. The public signing-certificate fingerprint remains `cf06385769b4d9e0992910521f210a77407cb284e2963290fbb10006b8d88054`. Signing was completed locally; no password or private key belongs in chat or GitHub.

Install an update matching the existing app without uninstalling or clearing its storage. No phone access, personal-data changes or independent audit are claimed.
