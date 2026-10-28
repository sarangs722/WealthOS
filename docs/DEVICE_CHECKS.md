# Guided physical-device checks — WealthOS 0.6.4

These checks are for the user to run locally. They have **not** been executed or marked passed by the agent. Report only pass/fail, the phone model and Android version; do not send financial amounts, passwords, keystores, backup files or screenshots of personal records.

The [signed 0.6.4 artifact and emulator verification](RELEASE_0_6_4.md) are ready. The phone results below still require your own checks.

## Preparation

* Use the verified, permanently signed **0.6.4 / code 10** APK when it is available. An unsigned candidate is not installable. It uses the existing permanent release certificate and the **WealthOS Release** identity.
* Keep the original **WealthOS** installation intact. Release installs alongside it, or updates an existing Release installation with the same certificate. If Android reports a signing mismatch, stop and retain both installations/data; do not uninstall to force an update.
* Before making changes to an app that already holds personal records, keep an independently retained encrypted backup and its password. If the original app lacks **Manage backups**, follow the verified existing-signer update/migration instructions in [the current release guide](RELEASE_0_6_2.md#existing-records-and-guided-phone-checks).
* Keep a note of the phone's current font size, theme and accessibility settings so you can restore them afterward. Use your existing screen lock; these checks do not require changing its PIN or enrolling a new biometric.

## A. Interface and accessibility — fictional demo only

In **WealthOS Release → Settings → Explore demo mode**, confirm that the app identifies the data as fictional.

| Check | Pass criteria | Your result |
| --- | --- | --- |
| Navigation | Home, Portfolio, Forecast, Tools and Protect can each be reached and scrolled | Not run |
| Light/dark theme | Text and controls remain readable in both system themes | Not run |
| Large text | With the phone's largest font size, summary cards stack, exact amounts remain readable (or explicitly scroll when exceptionally long), and buttons/fields remain reachable | Not run |
| Forecast chart | Open the fictional base scenario. Nominal is solid; today's purchasing power is dashed. Both remain visible in dark mode; dates and numerical values are available in the details | Not run |
| Amount privacy | Enable **Obscure amounts**. Read views/charts hide financial values and free-text financial notes; inputs may show values while editing | Not run |
| TalkBack | Enable Android TalkBack temporarily. Swipe through navigation, Settings and scenario details. Controls have meaningful names and focus does not get trapped; activate with TalkBack's double-tap gesture | Not run |
| Screenshot/recents protection | A screenshot of the actual app is blocked/blank or shows Android's protected-content message; the recents preview does not expose financial content | Not run |

Restore your preferred font/theme/TalkBack settings. Demo records remain in memory and do not become personal records. Do not use demo mode to judge personal-vault persistence.

## B. Access lock and ordinary offline use

These checks apply only to the app you intend to use. Keep its existing records intact.

1. Enable **Device access lock** using the phone's existing device security. Leave the app using Home, then return: the unlock gate must appear before records.
2. Cancel authentication: records must remain inaccessible. Retry using your existing biometric/device credential: the app should open successfully. Report which authentication method you actually tested; one method does not establish the other.
3. Exit demo mode if active. Close and reopen the app and confirm that your previously recorded balances/dates/history are unchanged. No amounts need to be sent to the agent.
4. Temporarily disconnect networking using the phone's normal controls. Confirm that saved records, history and calculators still open. Restore the phone's original connectivity afterward. Public refresh availability is a separate optional feature.
5. Note obvious long freezes, crashes or repeated loading. Emulator timings do not establish physical-device performance or battery life.

## C. Backup validation and controlled restoration

1. In the app holding records, export an encrypted portable backup using **Manage backups**. Confirm the successful readback message, retain the file independently and keep its backup password separately from the file.
2. Select that file and use **Validate and preview backup**. Confirm the date and record count look appropriate. **Cancel restore**: validation alone must not replace the current vault.
3. A full restore test should use an **initially empty receiving installation**. If WealthOS Release already holds records, leave its full-restore check pending rather than replacing them just for testing.
4. For an empty receiver, select the intended backup, authenticate, inspect its preview and confirm that **Current vault: 0 rows** before acknowledging replacement. Check the restored records locally, the pre-restore safety-copy section and public networking being off. Cold-open the app and verify the same values/dates again.
5. Retain the original app until restoration and a new export from Release have both been independently checked. Continue future edits in one app to avoid divergent books.

The financial backup password and APK signing passphrase serve different purposes. Keep independently protected copies of the permanent signing key/passphrase for future same-identity updates. No private key is required for these phone checks.

## Results to send back

```text
Phone model / Android version:
WealthOS Release version:
Demo navigation / dark theme / largest font:
TalkBack:
Amount hiding / protected screenshot / recents:
App-lock cancel / successful method tested:
Offline access / cold-open persistence:
Backup export / preview-cancel:
Full restore into empty receiver: passed / failed / not run
Any crash, freeze or unreadable control (screen/action only):
```

Passing these guided checks provides device-specific evidence. It does not establish power-loss durability on every OEM, physical 16 KiB behavior unless the device is known to run that mode, or an independent security audit. Those remaining requirements must not be marked complete from an emulator run or an unchecked checklist.
