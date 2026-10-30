# Guided physical-device checks — WealthOS 0.6.9

These checks are for the user to run locally. They have **not** been executed or marked passed by the agent. Report only pass/fail, the phone model and Android version; do not send financial amounts, passwords, keystores, backup files or screenshots of personal records.

The [verified signed 0.6.9 Release and matching development update](RELEASE_0_6_9.md) are ready. Phone results below still require your own checks. Every version handoff must include a short checklist of changed flows with expected results; keep these user checks separate from automated verification.

## Preparation

* Use the verified **0.6.9 / code 15** APK. For an existing **WealthOS Release** app, use the same-certificate Release APK. For the original **WealthOS** development app, use its matching development update. Keep the chosen installation and its storage intact.
* Keep the original **WealthOS** installation intact. Release installs alongside it, or updates an existing Release installation with the same certificate. If Android reports a signing mismatch, stop and retain both installations/data; do not uninstall to force an update.
* Before making changes to an app that already holds personal records, keep an independently retained encrypted backup and its password. If the original app lacks **Manage backups**, follow the verified existing-signer update/migration instructions in [the current release guide](RELEASE_0_6_2.md#existing-records-and-guided-phone-checks).
* Keep a note of the phone's current font size, theme and accessibility settings so you can restore them afterward. Use your existing screen lock; these checks do not require changing its PIN or enrolling a new biometric.

## First: focused 0.6.9 checks

Use your actual statements and intended classifications; do not save fictional transactions into your personal vault. These checks are pending until you report results.

| Check | Action and expected result | Your result |
| --- | --- | --- |
| Update and retain records | Install the matching APK over the existing app. Settings shows 0.6.9; existing accounts, balances, dates and history remain unchanged | Not run |
| Bank import preview | Home → Cash flow → Statement spending & imports → Import statement. Choose the original HDFC Bank XLS, select the intended month and inspect Dates & coverage. Selected rows match that month's transaction dates; differing value dates remain visible. Canceling the picker does not change saved records | Not run |
| Card import preview | Import the Tata Neu HDFC card XLS. Check selected-month dates, debit/credit direction and amounts against the statement. A billing cycle spanning months is filtered by transaction month; rewards/EMI summary metadata does not appear as ordinary spending | Not run |
| Review and totals | Classify repayments, own transfers, investments, refunds and loan/EMI entries carefully. Card repayment and own transfers are excluded from spending; refunds reduce spending in their transaction month. Unreviewed or unsplit loan rows remain incomplete. Confirm card spending only for eligible ordinary purchases you have checked | Not run |
| Save and reopen offline | Save draft, or save reviewed spending when all entries are checked. Disconnect networking, close and reopen the app. The import and saved categories remain; bank balances, net worth and the existing manual cash-flow summary are unchanged by the import | Not run |
| Same-file duplicate | Choose the same file and month again. The app says it is already saved and offers Open saved statement; no second import or duplicated total is created | Not run |
| Privacy and layout | Enable Obscure amounts and open the saved import: amounts and transaction descriptions are hidden. At a large system font size, month/category controls and Save remain reachable | Not run |
| Backup contains imports | Settings → Manage backups: export, then validate and preview the backup and cancel restore. Validation succeeds with statement tables included; canceling leaves the current vault unchanged. Retain the file/password independently | Not run |

Optional deeper checks: if a real category needs correction, revise it and confirm the total changes appropriately. On an intentionally unwanted import, Void this import removes its spending contribution and Restore import brings it back without deleting history. With overlapping exports from the same source nickname, potential duplicates require an explicit keep/exclude/duplicate choice. A full backup restoration check belongs only in an empty receiver, following section D below.

Report each focused check as passed, failed or not run, with the screen/action for any failure. Financial values or statement uploads are not needed.

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

## B. Remaining entry flows - inspect without saving

Outside demo mode, open the following forms and then leave with Back. Do not add fictional financial records to a personal vault merely for these checks.

| Check | Pass criteria | Your result |
| --- | --- | --- |
| Mutual fund | Name/current value are primary; invested amount, units and NAV are optional; the date picker and fixed Save remain reachable | Not run |
| Cash flow | Optional payments expand; unconfirmed figures say Save draft; confirmation controls are clearly labelled | Not run |
| Scenario/goal | Primary inputs are concise; advanced assumptions expand with retained values; calendars and Save work at large text sizes | Not run |
| Calculator | Using fictional inputs, SIP 1,000/month for 12 months at 0% gives 12,000; changing inputs hides that result until recalculation | Not run |
| Amortization | EMI principal 1,200, annual rate 0%, term 12 months gives 100/month; repayment schedule expands separately | Not run |

Calculator inputs do not post financial records. Review any saved assumptions before changing them; a full persistent-entry test belongs in a separate empty receiver.

## C. Access lock and ordinary offline use

These checks apply only to the app you intend to use. Keep its existing records intact.

1. Enable **Device access lock** using the phone's existing device security. Leave the app using Home, then return: the unlock gate must appear before records.
2. Cancel authentication: records must remain inaccessible. Retry using your existing biometric/device credential: the app should open successfully. Report which authentication method you actually tested; one method does not establish the other.
3. Exit demo mode if active. Close and reopen the app and confirm that your previously recorded balances/dates/history are unchanged. No amounts need to be sent to the agent.
4. Temporarily disconnect networking using the phone's normal controls. Confirm that saved records, history and calculators still open. Restore the phone's original connectivity afterward. Public refresh availability is a separate optional feature.
5. Note obvious long freezes, crashes or repeated loading. Emulator timings do not establish physical-device performance or battery life.

## D. Backup validation and controlled restoration

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
Quick entry / cash flow draft / scenario-goal forms:
SIP / EMI / stale-result clearing:
TalkBack:
Amount hiding / protected screenshot / recents:
App-lock cancel / successful method tested:
Offline access / cold-open persistence:
Backup export / preview-cancel:
Full restore into empty receiver: passed / failed / not run
Any crash, freeze or unreadable control (screen/action only):
```

Passing these guided checks provides device-specific evidence. It does not establish power-loss durability on every OEM, physical 16 KiB behavior unless the device is known to run that mode, or an independent security audit. Those remaining requirements must not be marked complete from an emulator run or an unchecked checklist.
