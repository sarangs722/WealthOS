# WealthOS 0.6.2 — verified privacy and accessibility fixes

Latest UI refinement: [0.6.4](RELEASE_0_6_4.md), with the same permanent signer. This page retains historical evidence.

October 28, 2026. **Signed local release available**, version 0.6.2 / code 8 / schema 6. This continues Milestone 6. No personal phone was accessed or installed/modified by the agent. Guided physical-device acceptance remains open. The first-party security review is complete within the scope below; no independent audit is claimed.

## Installable artifact

* Signed APK: [.tools/production-release/WealthOS-0.6.2.apk](../.tools/production-release/WealthOS-0.6.2.apk).
* Application ID `com.wealthos.app.release`, launcher label **WealthOS Release**; Android 10/API 29 minimum, target API 37.
* APK SHA-256: `9c054bd7784f2e9981dba5d694a3f5b1517d052078bd993e1c05b4c5c6afae05`.
* Existing permanent certificate SHA-256: `cf06385769b4d9e0992910521f210a77407cb284e2963290fbb10006b8d88054`.

The user supplied the existing passphrase in a local masked console. No new signing identity was created; no passphrase/private-key contents were read into chat. Independent SDK verification passed the certificate pin, single v3 signer, non-debuggable/non-test flags, reviewed permission/component inventory, cloud/device/legacy backup exclusions, disabled cleartext and all eight native libraries' 16 KiB ELF/ZIP alignment. All 97 original unsigned ZIP entries retain identical bytes after signing; only the three expected signing entries and APK signing block were added. The earlier signed 0.6.0 and intermediate 0.6.1 artifacts remain preserved; use 0.6.2 for the fixes below.

## Changes

**Forecast readability.** The original fixed forecast colours had only approximately 2.56:1/2.96:1 contrast against the dark card. The chart now uses theme-aware green/blue colours, a solid nominal line and a continuous dashed purchasing-power line. Density-independent strokes and inset endpoints improve visibility. The legend/accessibility description identifies the line styles and points readers to the numerical details. Hidden amounts still suppress both series. Actual Compose theme colours passed 3:1 graphical contrast against the relevant light/dark surfaces; the dark fixture at 200% local font scaling was rendered and inspected. This is a specific contrast check, not whole-app WCAG certification. See [W3C non-text contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) and [Android accessibility testing](https://developer.android.com/develop/ui/compose/accessibility/testing).

**Interrupted privacy-write recovery.** Startup previously checked only the main privacy file before calling `AtomicFile.openRead()`. If only the last committed `.bak` remained, it could silently use default-off lock/amount-hiding settings. Android 10's [AtomicFile implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android10-release/core/java/android/util/AtomicFile.java) uses that backup convention, and the [current API](https://developer.android.com/reference/android/util/AtomicFile) supports legacy recovery. Startup now also detects that recoverable file and lets `openRead()` restore the committed flags. No preference format, database key, database schema, financial math or dependency changed.

The recovery tests use newly created isolated private directories, never the real privacy file/vault. Against the unfixed 0.6.1 target, the backup-only test failed as intended while the incomplete-base and new-install cases passed. The same unchanged expectations then passed after the fix, including on Android 10. These are deterministic interrupted-file-state fixtures, not physical power-loss tests.

## Verification actually performed

| Check | Result |
| --- | --- |
| Debug APK/test APK, minified coexisting release, debug/release lint | Build passed in 4 minutes; 277 tasks, 41 executed; both lint reports have zero errors and the existing Gradle-version notice |
| API 36 targeted tests | **8 passed**, zero failures/skips: three chart accessibility cases, three privacy-recovery cases, forecast/goal privacy and access-lock/`FLAG_SECURE` regression |
| API 29 recovery tests | **3 passed**, zero failures/skips, running the minimum-supported platform's atomic-file implementation |
| Existing API 29 fixture | Its encrypted vault and wrapped database-key bytes stayed identical across the data-preserving debug update/tests |
| Render inspection | Synthetic light/dark charts inspected; final 0.6.2 renders are byte-identical to the inspected images; no protected personal screen was captured |
| Real signed upgrade | 0.6.0 → intermediate 0.6.1 → 0.6.2, same permanent signer, app ID/UID/data directories; no uninstall or data clear |
| Retained fictional records | ₹1,152.52 net worth; bank revisions 1/2 at ₹500.01/₹650.02 with correction note; fund five units, ₹400.00 FIFO cost, ₹102.50 unrealized gain; incomplete-return-history warning retained; networking off |
| Exact final-APK startup diagnostics | Three cold-process first-display samples: **2,233 / 1,962 / 1,758 ms**; one PSS snapshot 25,056 KiB |

There are six new ordinary regression cases. The **11 passing executions cover eight distinct targeted cases**; the three recovery cases repeat across two APIs. The full historical baseline of 204 cases plus six new cases was **not** rerun in full. Ordinary app regression now requires at least 24 cases. Three separately parameterized upgrade/reinstall definitions deliberately discover zero cases without their host-provided arguments and are not counted in that ordinary suite.

The native rehearsal first restored the known encrypted 12-row **source** fixture into an empty 0.6.0 receiver through the real SAF picker, authenticated preview, explicit acknowledgement and retained safety copy. Restore adds its own audit/network-setting records; 12 is not a claim about the final receiving row count. The final signed 0.6.2 app then retained the expected wealth/history through repeated cold starts. Its exit records contained deliberate force-stops and no recorded app crash during this rehearsal.

Startup samples use Google's x86_64 API 36 emulator, 4 KiB pages, WHPX/software rendering and a 2 GiB guest after the build daemon was stopped. `am start -S -W` measures cold first display, **not** full financial-data readiness, physical-phone speed, peak memory, smoothness or battery life. See [Android's measurement distinctions](https://developer.android.com/topic/performance/issues/launch-time). An initial System UI timeout during the preliminary 0.6.1 setup was recovered with **Wait**; its interrupted attempt was not counted as a successful update or performance sample.

Evidence: [combined public report](verification/release-062.json), [API 36 raw results](verification/release-062-api36.log), [API 29 raw results](verification/release-062-api29.log), [retained failing reproduction](verification/release-062-before-fix.log). Synthetic renders: [light](screenshots/release-062-fictional-chart-light.png), [dark/200%](screenshots/release-062-fictional-chart-dark-200.png).

## Existing records and guided phone checks

The user chose to run physical-device checks personally. Follow [the guided checklist](DEVICE_CHECKS.md), then report pass/fail and phone model/Android version without sharing financial values or passwords.

* If **WealthOS Release** is already installed with this permanent signer, 0.6.2 updates it in place. Keep an independently retained encrypted backup before personal-device changes; do not uninstall/clear storage to resolve a signing error.
* If your original **WealthOS** still lacks **Manage backups**, the [migration-only development update](../.tools/WealthOS-0.6.2-development.apk) retains its verified original development certificate (`dd7d62a4…589f73`). This is for updating that original installation without uninstalling so it can export a portable backup; it is not the permanent-release identity. If Android reports a mismatch, stop and keep the existing app/data.
* The permanent Release identity installs alongside the original app. An initially empty receiving vault is expected. Restore your intended backup only after inspecting its preview and the receiving vault; retain the original installation until the migration and a fresh export have been independently checked. Do not replace a populated vault merely to run a test.

The bounded first-party review covered privacy-file recovery, database-key handling, backup authentication/restore boundaries, manifest/export/backup controls and fixed public HTTPS transport. It is **not an independent security audit**. Phone checks have not yet been reported; hardware/OEM biometric/Keystore behavior, physical durability, TalkBack/performance and physical 16 KiB execution remain. Earlier experimental Android 15 runtime/system failures remain unresolved; [historical 16 KiB checks](16K_VERIFICATION.md) were not rerun on this patch. Test emulators are stopped with original font/theme settings restored. No new milestone was started.
