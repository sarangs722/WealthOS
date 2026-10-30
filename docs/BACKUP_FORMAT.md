# Portable backup format v1

Implemented in `core:security` (pure JVM/JCA) and `RoomBackupRepository`. Application 0.6.9 uses Room schema 8. The encrypted envelope remains format 1. There is no server, account, device identifier, embedded database password or Keystore material in a portable backup.

## Envelope

All integers are big-endian. The 72-byte header is authenticated as AES-GCM additional data, followed by ciphertext and the 16-byte authentication tag:

| Offset | Size | Field |
| --- | --- | --- |
| 0 | 8 | ASCII `WOSBAK01` |
| 8 | 4 | Format version, 1 |
| 12 | 4 | KDF identifier, 1 = PBKDF2-HMAC-SHA256 |
| 16 | 4 | Cipher identifier, 1 = AES-256-GCM |
| 20 | 4 | PBKDF2 iterations, 600,000 |
| 24 | 32 | Fresh cryptographically random salt |
| 56 | 12 | Fresh cryptographically random nonce |
| 68 | 4 | Ciphertext length including tag |
| 72 | variable | Ciphertext followed by 128-bit GCM tag |

The password is 12–128 UTF-16 code units, must contain properly paired surrogates, and is passed unchanged to `PBEKeySpec`. Standard JCA PBKDF2-HMAC-SHA256 uses its UTF-8 representation; an independently generated .NET Unicode fixture checks compatibility on JVM and Android. There is no trimming or Unicode normalization. File size is bounded before deriving the key; only the exact supported KDF/cipher/iteration parameters are accepted. Unknown versions, trailing bytes and inconsistent lengths fail. GCM authentication completes before decoding any rows. A wrong password and authentication damage produce the same failure message.

PBKDF2 is used because Android 10 and JVM provide it without another cryptographic dependency. The work factor follows [OWASP's PBKDF2 guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html); it does not make a weak/reused password strong, or constitute FIPS validation. The implementation follows [Android's platform cryptography recommendations](https://developer.android.com/privacy-and-security/cryptography) for AES-GCM and avoids pinning a provider. No alternative insecure cipher is selected if crypto is unavailable.

## Lossless payload and bounds

The encrypted plaintext starts with `WOSROWS1`, a schema integer, length-prefixed UTF-8 creation timestamp and table count. Each table contains a name, ordered column names, row count and typed cells. Tags are 0 = SQL NULL, 1 = signed 64-bit integer, 2 = UTF-8 text. Text lengths/counts are signed 32-bit integers validated before allocation. Decimals remain their original TEXT, preserving scale and precision. No floating point, SQL, Java object deserialization, archive extraction or compression is used.

Limits: 8 MiB encoded payload, 50,000 total rows, 100 tables, 80 columns/table, 64 KiB/cell, 100 UTF-8 bytes per identifier and 80 bytes for the timestamp. Strict UTF-8 rejects malformed input. Duplicate tables/columns, inconsistent row widths, unsupported types and trailing payload bytes fail. Only the 31 expected schema tables and exact trusted columns/types may be restored. Oversize export fails without changing the vault; these are explicit supported limits, not silent truncation.

Financial source dates remain separate from retrieval timestamps. Future-date validation uses the source calendar: Europe/Berlin for ECB and Asia/Kolkata for the Indian and manual economic references. It does not reject a valid Indian date merely because UTC is still yesterday. Restore retains both original fields without rewriting dates or values.

## Consistency and restoration

Export captures all financial, household, planning, public cache/application/consent and audit rows within one Room transaction, ordered deterministically by table dependencies and primary keys. The database/WAL/Keystore files are never copied. No plaintext temporary backup is written.

Restore first creates an encrypted staging database using a fresh random key and the **packaged schema 6, 7 or 8 JSON**, selected by the validated incoming schema version, not DDL supplied in the file. Incoming rows use bound inserts in foreign-key order. Storage types, canonical UUIDs, plain decimal bounds, booleans/revisions, SQL integrity/foreign keys and domain/financial reconciliation are checked. The stage then opens through the normal Room migration chain, including explicit migrations 6 to 7 and 7 to 8. This release accepts schemas 6, 7 and 8; it never guesses a schema or performs destructive fallback. Staging files are closed/deleted on ordinary success/error/cancellation; a killed process may leave an encrypted, unrecoverable orphan staging file until app data removal.

The preview records a SHA-256 fingerprint of the receiving vault, excluding capture time. Final confirmation opens one write transaction, checks that fingerprint again, writes and verifies an authenticated encrypted safety copy of the old vault, replaces rows, disables public networking, appends a restore audit event, revalidates and commits. Failed inserts/audit/integrity/cancellation roll back all live rows. A safety copy can remain after a failed restore; this preserves the old data and does not represent a successful replacement. Nothing merges accounts or invents new financial history.

Safety copies live in app-private `noBackupFilesDir/restore-safety`, each encrypted with the password of the backup used during that operation. They are never automatically pruned. Export them explicitly to retain them beyond uninstall/storage removal. Device UI-lock/amount-hiding preferences remain local; transferred network consent is always off after a successful restore.

## File and memory handling

The native [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files) lets the user choose an export/import destination without storage permissions. The selected file contains only encrypted bytes. A completed export is reopened and compared byte-for-byte before success is reported; provider errors/truncation are failures, and partial output must not be used. A provider returning the bytes once cannot guarantee its future durability or storage behavior; retain an independent copy and test restoration.

Passwords are transient `remember` UI fields, cleared on background/disposal, never `rememberSaveable`, logged or written to preferences. A pending restore is invalidated when leaving the foreground. Password arrays, derived key arrays and encoded plaintext byte arrays are cleared where ownership permits. Immutable Kotlin strings, database/provider caches, crypto provider internals and the JVM/Android heap cannot promise secure memory erasure. The app UI lock does not close/rekey the vault or defend a compromised process.

System file-picker callbacks belong to the activity rather than the financial composition, so device access lock may remove that composition without losing an export/import result. Only encrypted export bytes and a selected file URI survive in the ViewModel; passwords and restore previews do not. Picker launch is claimed once per export across recomposition. On return, the access lock is still required before financial UI or the backup screen is shown. After process death, an expired encrypted export or NAV binding is reported and must be prepared again; it is never silently treated as a successful backup/import. The same activity ownership protects the existing selected-fund NAV picker.

There is no password reset. A verified portable file plus its original password permits recovery into a fresh installation with a new Keystore/database key. An already unopenable vault is preserved and cannot currently run the in-app restore screen; preserve it for diagnosis and use a fresh installation/device for a known-good portable backup. Never uninstall an unopenable vault unless a separately retained backup has actually been validated.

## Schema 7 holding snapshots

`asset_valuations` adds nullable decimal TEXT `reportedInvested`, `reportedUnits`, `reportedPrice`, plus `calculatedTotal` INTEGER NOT NULL DEFAULT 0. Existing records retain their values and dates; new optional figures remain unknown. Metadata is valid only for total-value investment observations, including preserved pre-conversion snapshots. Calculated totals must equal units times price rounded HALF_EVEN to currency scale. Financial decimals and boolean flags are validated before replacing the live vault. Older app versions reject schema-7 backups; update the receiving app first. No envelope, KDF or device-key changes are needed.

## Schema 8 statement spending

Four new normalized tables retain statement imports, selected-month lines, review revisions and per-line decisions. They store decimal TEXT with explicit INR currency on the related import, transaction/value dates, optional requested bank period, source nickname/type, file hash, parser version and recorded timestamps. The original XLS/CSV file and unused customer/card/reward metadata are not retained. Earlier reviews and voided imports remain in the portable backup. Restore validates complete per-review decisions, line/month relationships, sequential revisions, category/direction compatibility, UUIDs, dates and coverage flags before replacing live records.

Schemas 6/7 migrate with empty statement tables. Schema-8 backups need WealthOS 0.6.9 or newer; older apps reject them. The envelope/KDF and independent-device password recovery are unchanged. Existing whole-vault backup byte/row limits still apply, including statement history.
