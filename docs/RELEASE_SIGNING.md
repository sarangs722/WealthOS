# Production signing without replacing the development vault

Current signed artifact: [WealthOS 0.6.2 and verified fixes](RELEASE_0_6_2.md), reusing the user's original permanent certificate. The single-command setup below remains the historical 0.6.0 bootstrap; do not rerun it to recreate a key or overwrite an existing output. Future candidates use `Sign-Release.ps1` with the existing key, reviewed candidate, known public certificate and a new versioned output.

The current installed development identity is `com.wealthos.app`, signed by the Android development key. A newly created release key cannot update it. **Keep that installation and its data intact.** WealthOS supports an explicit coexisting release build with application ID `com.wealthos.app.release` and launcher label **WealthOS Release**. Its private database/Keystore namespace is separate. Default debug/main builds keep the existing identity and label.

This is an opt-in build configuration, not an automatic phone migration. None of the signing scripts installs, uninstalls, reads a phone or uploads anything. A certificate fingerprint is public metadata; signing passwords and private keys must stay local. Physical-device, accessibility/performance and independent security review are still required before declaring a production release.

See the [verified build, 19 signing checks and native coexistence results](RELEASE_READINESS.md). The unsigned source candidate is retained; disposable signing fixtures are never personal-use release artifacts. **Current status:** the user has completed local permanent signing. Use [the verified signed APK and migration instructions](PRODUCTION_RELEASE.md), not a disposable fixture. The setup instructions below remain the procedure for future reviewed candidates and do not need to be repeated for 0.6.0.

## Single-command local setup

For the retained verified candidate, run this from the repository root in **PowerShell 7.4+**:

```powershell
.\scripts\Complete-ReleaseSetup.ps1
```

The setup verifies the candidate's recorded hash, resolves local SDK/Java 21/Python tools, and asks locally whether to create a new permanent key (`N`), use an existing key (`E`) or cancel (`Q`). Password input is masked and never returned to chat. New keys default to `%LOCALAPPDATA%\WealthOS\signing\wealthos-release.p12`, outside the OneDrive repository; a newly created signing directory grants access only to the current Windows user and SYSTEM. Existing keys require their known public certificate fingerprint and alias. A different private-key password can be supplied as `-KeyPassword (Read-Host -AsSecureString)`.

The default accepted output is `.tools/production-release/WealthOS-0.6.0.apk` with its public `.verification.json`. Existing keys and outputs are never overwritten. If signing fails after creating a key, retain that key and choose `E` on the next attempt; do not create a second identity as a workaround. Retain an independently protected copy of the key and its passphrase before personal use. This setup does not install, migrate or publish the app. Its [nine end-to-end fixture checks](verification/release-setup-tests.json) passed using disposable keys that were then destroyed.

The explicit build/key/sign commands below remain available for other local tool paths or a freshly reviewed candidate.

## Build the coexisting candidate

Set `JAVA_HOME`, `ANDROID_HOME` and `ANDROID_SDK_ROOT` as in [the build guide](RELEASE.md), then run from the repository root:

```powershell
.\gradlew.bat :app:assembleRelease :app:lintRelease '-Pwealthos.coexistingRelease=true'
```

Preserve `app/build/outputs/apk/release/app-release-unsigned.apk` in a separate release-candidate folder before building a different variant. It is intentionally unsigned. No secret enters Gradle properties or its configuration cache.

## New key, only if you do not already own one

Use PowerShell 7.4+ and Java 21. Create a dedicated folder outside the source repository, preferably outside automatic cloud synchronization, with access restricted to your Windows user. For example, choose a location under `%LOCALAPPDATA%\WealthOS\signing`. Keep an independently protected backup of the resulting keystore and its passphrase before distributing any APK.

```powershell
.\scripts\New-ReleaseKey.ps1 `
  -JavaDirectory $env:JAVA_HOME `
  -KeystorePath 'C:\YOUR-PRIVATE-FOLDER\wealthos-release.p12'
```

The script prompts locally for a new passphrase and confirmation. It creates an encrypted PKCS12 store using RSA-4096/SHA256withRSA with a 30-year certificate, and exports only its public certificate (`.cer`) and public fingerprint metadata (`.public.json`). It never overwrites a keystore. New-key passphrases use 20–128 printable ASCII characters to avoid Java keystore/console interoperability pitfalls; this is unrelated to Unicode-capable financial backup passwords.

The keytool password exists briefly in the child process's environment rather than its command line. It is not added to the parent environment, a plaintext file, source control or Gradle settings. Mutable password buffers are cleared where possible; Windows/.NET/Java provider memory cannot promise secure erasure. Losing the signing key can prevent future same-identity updates; a financial backup cannot replace a missing signing key.

If a keystore already exists, do not run the creation script. Use its known alias and independently retained public certificate fingerprint.

## Sign and check

Replace placeholders with the actual local paths and the public SHA-256 fingerprint from the certificate metadata. Do not place passwords in commands or chat. The script prompts for the store password and defaults to the same private-key password; an existing keystore with a different key password can supply a separate `SecureString` read with `Read-Host -AsSecureString`.

```powershell
.\scripts\Sign-Release.ps1 `
  -SdkDirectory $env:ANDROID_HOME `
  -JavaDirectory $env:JAVA_HOME `
  -PythonExecutable 'C:\PATH\TO\python.exe' `
  -UnsignedApk 'C:\YOUR-CANDIDATE-FOLDER\app-release-unsigned.apk' `
  -KeystorePath 'C:\YOUR-PRIVATE-FOLDER\wealthos-release.p12' `
  -Alias 'wealthos-release' `
  -ExpectedCertificateSha256 'YOUR_64_HEX_DIGIT_PUBLIC_CERTIFICATE_FINGERPRINT' `
  -OutputApk 'C:\YOUR-CANDIDATE-FOLDER\wealthos-release.apk'
```

The SDK signer receives passwords over redirected standard input; arguments contain only paths, alias and public parameters. A verified candidate is published only after signature/certificate pinning, non-debuggable/non-test flags, the exact package/permission/component inventory, packaged backup exclusions, cleartext prohibition and native ELF/ZIP 16 KiB alignment all pass. Existing output files are never overwritten. Failed signing/verification removes only its own uniquely named temporary output; it does not modify a keystore, unsigned input, existing accepted APK or financial database.

The accompanying `.verification.json` contains public artifact/certificate metadata and remaining external review requirements. It is not an independent security certification. Android debug certificates are explicitly rejected, even when applied to a minified release APK. Recheck a retained signed artifact with `Verify-ReleaseCandidate.ps1` using the same public fingerprint.

## Controlled migration after the candidate is signed and reviewed

1. Update the development installation to the verified backup-capable 0.6.0 APK using its existing development signer. Do not uninstall or clear it.
2. Pause financial edits, create an encrypted portable backup, retain its original password separately and validate the file. Keep an independent copy.
3. Install the reviewed **WealthOS Release** candidate alongside it. An empty new vault is expected; the development vault remains in the original app.
4. Restore the selected backup into Release only after its preview and explicit replacement acknowledgement. Verify known bank dates/revisions, units/costs, contracts, policies, goals and scenarios. Re-enable local amount hiding/device lock as desired; networking defaults off.
5. Use one app for future financial edits to avoid diverging books. Retain the original installation until you have independently verified the new app and a new portable backup. Nothing automatically deletes the old installation.

Official references reviewed October 28, 2026: [Android signing and key continuity](https://developer.android.com/studio/publish/app-signing), [application ID suffixes](https://developer.android.com/build/build-variants), [apksigner password inputs and verification](https://developer.android.com/tools/apksigner), [Java 21 keytool](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html). These tools are local; no key is sent to a service.
