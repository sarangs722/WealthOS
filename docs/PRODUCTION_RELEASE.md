# WealthOS 0.6.0 — signed local release

Latest UI refinement: [0.6.4](RELEASE_0_6_4.md), with the same permanent signer. This page retains historical evidence.

**Superseded by [0.6.2](RELEASE_0_6_2.md)**, which fixes forecast contrast and interrupted privacy-setting recovery using the same permanent certificate. This page preserves the original 0.6.0 signing evidence. Use the latest signed artifact for new installation/update.

October 28, 2026. The user completed the prepared local signing workflow, choosing the permanent key and entering its passphrase locally. The resulting installable APK was independently reverified against the exported **public** certificate. No private key or passphrase was read into chat. No phone installation, financial-data access, migration or publication was performed.

## Artifact and verification

* Installable APK: `.tools/production-release/WealthOS-0.6.0.apk`.
* Launcher label: **WealthOS Release**; application ID `com.wealthos.app.release`.
* Version 0.6.0 / code 6 / database schema 6; Android 10/API 29 minimum, target API 37.
* APK SHA-256: `9bc05a33c5075c1986a62cfbae7bde8f2f3fbe3f12ac7692c01364db18b41914`.
* Public signing certificate SHA-256: `cf06385769b4d9e0992910521f210a77407cb284e2963290fbb10006b8d88054`.
* Public certificate: RSA-4096, valid until October 20, 2056. The public export contains no private key. The signing directory's ACL was checked: inheritance disabled, current Windows user and SYSTEM only.

The SDK `apksigner` verified one signer with an APK Signature Scheme v3 signature. Verification also passed the expected package/label, non-debuggable/non-test flags, exact permission/component inventory, explicit cloud/device/legacy backup exclusions, disabled cleartext networking, ZIP alignment and all eight native libraries' 16 KiB ELF alignment. The APK hash remained unchanged during verification. See [public signature/package report](verification/production-signing.json).

All **97 original ZIP entries** have exactly the same names and uncompressed bytes as the retained, previously tested unsigned candidate (`c35699ca…705c25`). The only additional entries are `META-INF/MANIFEST.MF`, `META-INF/WEALTHOS.RSA` and `META-INF/WEALTHOS.SF`, plus the separately verified APK signing block. The content comparison initially rejected any new entry; inspecting the precise difference established these three signing entries, which were then explicitly accounted for rather than ignoring arbitrary `META-INF` changes. [Payload comparison](verification/production-signing-payload.json).

No financial source, schema, dependency or compilation output changed during signing. The [last real Gradle build/lint invocation](verification/release-16k-api35-followup-build.json) passed before signing. The previously recorded domain/repository/UI tests and [runtime checks](16K_VERIFICATION.md) apply to that original payload; they were **not rerun** for signing. The exact newly signed APK has not been installed on a phone or emulator in this follow-up. Earlier native rehearsals used disposable signing identities, not this permanent certificate.

## Keep the existing vault

The signed app installs alongside the original **WealthOS**, under the distinct label **WealthOS Release**. Its initial empty vault is expected. It cannot update the original development-signed installation, and does not automatically copy its records.

1. Keep the original app and its data intact. It must be on the backup-capable 0.6.0 development build before migration. If it lacks **Settings → Manage backups**, follow the existing-signer update step in [the migration guide](RELEASE_SIGNING.md#controlled-migration-after-the-candidate-is-signed-and-reviewed); do not uninstall or clear it to resolve a signing mismatch.
2. Pause financial edits. Export an encrypted portable backup from the original app, retain the file independently and keep its original backup password separately. Validate its preview without confirming replacement of the original vault.
3. Install the signed **WealthOS Release** alongside it. Choose the backup in Release, authenticate, inspect the preview and explicitly confirm restoration into the initially empty receiving vault.
4. Verify your known balances, effective dates/history, holdings and any other recorded information. Export a fresh backup from Release before relying on it. Continue future edits in one app to avoid divergent books; retain the original installation until migration and recovery have been independently checked.

The **signing passphrase** protects future app-update continuity. The **financial backup password** protects exported records. Keep independently protected copies of the permanent signing keystore and its passphrase; neither is included in the APK/repository or automatically backed up by this verification. A financial backup does not replace a lost signing key.

## Remaining release requirements

This is a verified user-signed local artifact, not a claim of independent security certification. Physical ARM/16 KiB execution, OEM biometric/Keystore behavior, durability, TalkBack/performance and independent security review remain. The experimental Android 15 image had runtime/system failures: the current full UI suite passed on emulator 37.2.12, while the same-APK older-emulator FD repeat produced two passes and one Android-runtime crash. Root cause remains unconfirmed; see the [retained results and limits](16K_VERIFICATION.md).

The Gradle release build intentionally remains unsigned. Future releases must use the same permanent key through the local signing workflow. Do not recreate a key merely because an output already exists; the scripts deliberately refuse overwrites.
