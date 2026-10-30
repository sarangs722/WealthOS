# Build, signing and recovery

Latest signed patch: [0.6.9 statement spending and verification](RELEASE_0_6_9.md). The [guided phone checklist](DEVICE_CHECKS.md) records the remaining user-run acceptance checks; do not treat its unchecked rows as passing results.

Every version handoff must include a concise checklist in the response: where to navigate, what to test and the expected result for changed features and basic record/offline/privacy regressions. Update the guided checklist for that version and link it. Keep optional empty-receiver restoration separate from routine checks. Report automated results and outstanding user-device checks separately.

The user has completed [local permanent signing of WealthOS 0.6.0](PRODUCTION_RELEASE.md). That page identifies the installable coexisting APK, its independent verification and migration steps. The Gradle build described below intentionally produces the unsigned source candidate; it does not contain the private signing key.

Use Android Studio with Java 21, Gradle wrapper 9.6.0, SDK platform 37 and build tools 36.1.0. Minimum runtime is Android 10 / API 29. `local.properties`, SDK/AVD caches, keystores and build outputs are ignored. Public reference networking is optional and defaults off. Build tooling/dependency resolution requires internet on a cold machine.

Gradle uses a 3 GiB heap and at most two workers by default to avoid excessive pressure on a 16 GiB development machine. Run emulator suites serially on memory-constrained hosts, and run release shrinking and lint in separate invocations if necessary. A larger machine can override `--max-workers` explicitly; this does not change application behavior or financial data.

From the repository root, set `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT` and a specific isolated `ANDROID_SERIAL` before instrumentation. `scripts/Verify-Regression.ps1` permits only `emulator-5554` / WealthOS / API 36 and `emulator-5556` / WealthOS29 / API 29; it checks boot and fresh executed-test reports. Its opt-in synthetic reset must never be used on a personal installation. `scripts/Verify-ApkUpgrade.ps1` tests an in-place update without deleting app data. `scripts/Verify-PortableReinstall.ps1` requires an empty named test AVD and only uninstalls the synthetic installation it just seeded and exported successfully.

```powershell
.\gradlew.bat :core:finance:test :core:security:test :core:network:testDebugUnitTest
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
.\gradlew.bat :app:assembleRelease :app:lintRelease
python scripts/Verify-ApkPackaging.py app/build/outputs/apk/release/app-release-unsigned.apk
```

The release variant enables R8 code shrinking and resource shrinking and uses consumer rules supplied by Room/SQLCipher. It intentionally has **no signing key configured**. `app-release-unsigned.apk` is reviewable/buildable but cannot be installed until signed. Do not distribute the debug build as a production-signed release. Any separately signed lab smoke artifact uses only the existing Android development key and is explicitly not a production release.

Create/retain a user-controlled production key through Android Studio **Generate Signed App Bundle or APK**, or sign the unsigned APK using the official [`apksigner`](https://developer.android.com/tools/apksigner) with password prompting. Keep keys/passwords outside the repository and command history. Retain encrypted independent copies of that key: future same-package updates require the same signing identity. Verify `apksigner verify --verbose --print-certs` and SDK `zipalign -c -P 16 -v 4` on the actual signed output. Never assume the debug signing key is an appropriate long-term production identity.

An APK signed with a different key cannot update the current development installation. **Do not uninstall to work around a signature mismatch.** First update to 0.6.0 using the existing development identity, export a portable backup to a separately retained location, and validate its password/records on a fresh test installation/device. Default builds keep `com.wealthos.app`. The opt-in `'-Pwealthos.coexistingRelease=true'` build produces `com.wealthos.app.release`, labeled **WealthOS Release**, for a controlled migration alongside it. Follow the [local key/signing procedure](RELEASE_SIGNING.md); signing scripts never install or access a phone. Private keys and passwords remain outside Gradle and the repository.

## Personal backup/recovery steps

1. In Settings → Manage backups, enter/confirm a long unique passphrase and choose **Create encrypted backup**. Choose a destination you control; confirm that WealthOS reports successful readback. Keep the password separately.
2. Copy the encrypted file independently and validate it in WealthOS using **Choose backup file**, its original password and **Validate and preview backup**. Preview alone does not change financial records. Cancel to keep the current vault. Prefer an isolated second installation for a full rehearsal.
3. On the receiving fresh installation, choose that file/password, inspect its creation date and table/record counts, check the replacement acknowledgement and explicitly confirm. All receiving financial rows are replaced transactionally; the receiving key/privacy settings remain local and public networking is off.
4. Check known bank dates/revisions, units/costs, policies, goals and saved scenarios. Export the pre-restore safety copy if the previous receiving vault needs to survive uninstall. Each safety copy uses the password used in its restore operation.

Do not clear storage, uninstall or discard the original device based only on possession of an untested file. Forgotten backup passwords cannot be recovered. Keep bank statements/contract documents as independent financial evidence.

## Release boundary

Automated emulator checks do not establish physical biometric reliability, hardware-backed Keystore assurance, power-loss durability on every OEM, real-device 16 KiB execution, TalkBack quality or an independent security audit. Native ELF/ZIP alignment is checked separately from runtime page-size compatibility. No production signing key, store publication, live-user migration, telemetry or backend is introduced by this work. The verified milestone report records actual builds, runtime APIs, tests and remaining external review requirements.
