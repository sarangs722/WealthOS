# Release signing preparation — verified results

Latest follow-up: [0.6.2](RELEASE_0_6_2.md) is signed with the same permanent certificate and fixes the review's contrast/privacy-recovery findings. The user-run [phone checks](DEVICE_CHECKS.md) remain pending. The preparation fixtures below are historical evidence, not a claim that the full suite or device review was repeated for each patch.

October 28, 2026. Release preparation for WealthOS 0.6.0 (code 6, schema 6) is complete. The user subsequently completed the local permanent-key signing workflow; [the signed artifact, independent verification and migration instructions](PRODUCTION_RELEASE.md) are now available. The unsigned/disposable-fixture results below remain the historical preparation evidence. No personal phone or its financial records was accessed. This continues Milestone 6; no new financial feature milestone was started.

A follow-up adds [single-command local signing setup](RELEASE_SIGNING.md#single-command-local-setup): the user chooses a new/existing key and enters the passphrase locally. **Nine additional setup checks passed**, including new-key signing, restricted key-directory ACLs, existing-key reuse without modification, certificate mismatch rejection and disposable-key cleanup. See [the setup test report](verification/release-setup-tests.json). These supplement the original 19 checks below.

[16 KiB runtime verification](16K_VERIFICATION.md) subsequently verified 58 database/seven network cases on API 35, the same 18 isolated app cases on both API 35/36, and normal-animation native minified restores on both versions. The full API 35 UI pass used separately verified stable emulator 37.2.12 and the current test harness. Earlier ART/JIT crash causation remains unconfirmed; that experimental image still exhibited Google/system-process failures and a System UI ANR. Physical ARM execution is not claimed. Product code/schema/dependencies and the retained candidate APK remain unchanged; builds/lint verified the test-only fixes and the byte-identical product APK.

## Implementation

The approach was to preserve the existing development identity, prepare a distinct release sandbox, then verify the actual signed artifact and a fictional backup migration before using any production identity.

* `'-Pwealthos.coexistingRelease=true'` explicitly selects `com.wealthos.app.release`, launcher label **WealthOS Release**, for the shrunk release build. Default debug/main builds retain `com.wealthos.app` and **WealthOS**. There are no financial model, calculation, database schema or dependency changes.
* `New-ReleaseKey.ps1` prepares an encrypted PKCS12 signing key outside the repository, with local password confirmation, public certificate/fingerprint output and overwrite protection. `Sign-Release.ps1` receives credentials through `SecureString`/signer standard input and publishes only a verified candidate. Gradle receives no signing password or private key. The Java 21 keytool child receives its password in a child-only environment variable.
* `Verify-ReleaseCandidate.ps1` verifies the SDK signature, expected certificate, one signer/v3 signature, release flags, exact permission/component inventory, package-specific provider authority, packaged OS backup exclusions and eight native libraries' ELF/ZIP 16 KiB alignment. Development certificates are rejected. This is a bounded artifact check, not an independent security audit.
* Signing tools refuse existing destinations and clean up only their own staging files. Additional private-key file extensions are ignored. Password/provider memory has best-effort clearing, without a secure-erasure claim.

## Checks actually run

| Check | Verified result |
| --- | --- |
| Actual Gradle build | `assembleRelease`, `lintRelease`, `assembleDebug`, `assembleDebugAndroidTest` with the coexisting flag: **BUILD SUCCESSFUL**, 3m 14s |
| Release lint | 0 errors, 1 existing compatible-version notice about newer Gradle; 9.6 remains intentionally pinned |
| Signing-tool fixture suite | **19 checks passed**: real RSA-4096 PKCS12 creation/signing, password buffers, newline framing, wrong password, wrong public pin, debug signer, tampered APK, overwrite protection, unchanged source/key and disposable-key cleanup |
| Actual signed fixture | Full package/signature/backup/native checks passed; test certificate only, private key destroyed |
| Android 10 native smoke | Owned `WealthOS29`, `emulator-5556`, API 29: separate app UID, empty receiver, real SAF file selection, validated restore acknowledgement, encrypted safety-copy UI and cold-start persistence |
| Restored fictional values | Net worth ₹1,152.52; bank revisions 1/2 retain ₹500.01/₹650.02; fund 5 units, ₹400.00 remaining FIFO cost, ₹100.50 price, ₹502.50 value; networking off |
| Original emulator vault | Encrypted database and wrapped key byte-identical throughout the separate app's installation/restoration, with no WAL created; original app subsequently reopens at ₹1,152.52 |
| Existing development artifact | Rebuilt default debug APK is byte-identical to the previously verified Milestone 6 APK |

The API 29 emulator displayed one Quickstep launcher crash before the candidate opened; its dialog was dismissed. No WealthOS crash was observed. The signing-suite runner reports success only after all expected rejection cases, positive assertions and private-key cleanup complete.

These are additional release-tool and native smoke checks. The [204 distinct Milestone 6 tests](PROGRESS_MILESTONE_6.md) are prior verification, not claimed as rerun here. PowerShell parsing and unsigned native packaging checks also passed after the final host-script edits.

## Artifacts and evidence

The retained **unsigned** candidate is `.tools/release-readiness/wealthos-release-unsigned.apk`, SHA-256:

`c35699ca225fece744d1f8ddf381f4a8ced6cfd90c07db4a1396173465705c25`

It requires a retained production key before installation. Disposable-signer APKs under `signing-tests-*` are test fixtures and must not be distributed or used for personal records: their private keys were deliberately destroyed.

The default development APK remains `.tools/milestone-6.apk`, SHA-256 `71baccb417015beac48139fc2e4855c423ab46655d0e86ce720ece30c2fe5cd0`. The original default unsigned release was preserved separately as `.tools/milestone-6-release-unsigned.apk`, SHA-256 `242f5281914b4149ef8aaea127acee7dc3400cd80fbd812c27eeeb7d88b1599d`.

Public verification records:

* [Build/lint and artifact hashes](verification/release-readiness-build.json)
* [19 signing checks and disposable-key cleanup](verification/release-readiness-tests.json)
* [Actual fixture package/signature check](verification/release-readiness-fixture-signature.json)
* [Native coexistence and original-vault fingerprints](verification/release-readiness-coexistence.json)

To repeat the local signing suite, use a new output directory under this repository (the fixture keystore is independently created in the user's temporary folder and destroyed). Set the SDK/Java/Python paths to the verified local installations:

```powershell
.\scripts\tests\ReleaseTools.Tests.ps1 `
  -SdkDirectory $env:ANDROID_HOME `
  -JavaDirectory $env:JAVA_HOME `
  -PythonExecutable 'C:\PATH\TO\python.exe' `
  -UnsignedApk '.tools\release-readiness\wealthos-release-unsigned.apk' `
  -DevelopmentSignedApk '.tools\m6-release-lab.apk' `
  -OutputDirectory '.tools\release-readiness\new-signing-test-run'
```

## Remaining release requirements

Permanent local signing is now complete; see [the signed deliverable and verification](PRODUCTION_RELEASE.md). Independently protected key/passphrase retention and [controlled backup-based migration](RELEASE_SIGNING.md) remain user steps. Never send the private key or password in chat. No physical-device migration or publication was performed.

Physical biometrics/OEM durability, TalkBack/performance, execution on physical ARM with 16 KiB pages and independent security review remain. The initial API 29 coexistence fixture used 4 KiB pages; subsequent Google x86_64 16 KiB userspace simulations are documented separately with their runtime limits. Recommended next work is permanent signing followed by those device/review checks; no financial data or installed personal app needs to be altered to prepare the signing identity.
