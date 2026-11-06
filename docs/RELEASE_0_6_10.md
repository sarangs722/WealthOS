# WealthOS 0.6.10 — clearer import months and screen capture

Version code 16; database schema 8. This is work in progress, committed at the user's request. Candidate verification is incomplete; no signed 0.6.10 production update is ready. Production signing requires the existing key's local passphrase after verification. This update does not install on or access the user's phone.

## Changes

- Import previews now show human-readable month choices with transaction counts. The initial bank month follows the requested statement period when that month contains transactions. Otherwise, including card files without a requested period, the month with the most transactions is selected; ties select the latest month. Every other parsed month remains selectable. Transaction dates, value dates and file rows remain unchanged.
- The preview reports selected-month transactions out of the file's total, separately from the number needing category review. Saved imports show their own month and saved-row count, without claiming to retain other months from the original file. Saving still posts only the explicitly selected month.
- Saved-import entries now say **View transactions**. No parser, category, valuation, balance, cash-flow or reconciliation rules changed. “Not reviewed” means classification is pending, not that file reading failed.
- Screenshots and screen recording are allowed as explicitly requested by the user. MainActivity no longer applies Android's [FLAG_SECURE](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE). The optional local access lock and amount obscuring remain available. The app no longer claims protected screenshots or hidden recents thumbnails.

## Root cause and scope

The supplied statements span calendar months. The previous preview selected the latest transaction month, even when it contained only a small tail of a statement primarily covering an earlier month. The reported one-row bank preview matched that selected month's actual source-row count. Independent source-versus-adapter comparison found no dropped transactions. Download/file-name month, requested bank period, transaction dates and value dates are distinct concepts.

Automated fixtures remain independently fictional. The fixes address the misleading initial month and missing count context; they do not invent October transactions, move financial dates or rewrite saved imports. Reopen the original file and explicitly select another month to import that month's rows. Existing file/month deduplication and category review remain enforced.

## Verification status

Three new regressions failed against the prior implementation before production changes: screenshot permission at the app-window boundary, requested-period bank selection and the card's populated-month selection. The subsequent UI Lab debug and Android-test builds completed successfully. The recorded native run executed 16 cases: 13 passed, including both capture/access-lock cases, four preview cases, four import cases and three rendered UI cases. These three `BackupWorkflowTest` cases timed out in `ready()` while waiting for Home:

- `exportReadbackPreviewCancelAndConfirmedReplacementKeepHistory`
- `truncatedDocumentProviderCannotReportExportSuccessOrChangeVault`
- `passwordConfirmationAndRecreationDoNotPersistPassword`

Their root cause remains unestablished. Do not treat this run as a passing backup suite. Final minified builds, lint, packaging and production signing remain pending. No tests were rerun for the Git-only handoff on 7 November 2026. No database schema, encryption, backup format, signing identity or dependency version changed. See [recorded verification status](verification/capture-month-0610/status.json).

## User checks after updating

1. Install the matching APK over the existing app; Settings should show **0.6.10** and earlier records should remain intact.
2. Home → Cash flow → Statement spending & imports → Import statement. Choose the bank XLS. Check the initial month against the statement's requested period and inspect the counts in the Month menu. Select each month and compare its rows with the source's transaction dates.
3. Repeat for the card XLS. The month containing the most transactions opens first; other months remain available. The selected/file count should change when the month changes. “Not reviewed” stays until categories are reviewed.
4. Open a saved import through **View transactions**. Its month, rows and categories should remain; reimporting the same file/month should not duplicate totals.
5. In fictional demo mode, take a screenshot and a short system screen recording. Both should show the app. Enable Obscure amounts and verify read-view amounts are masked in the capture. Local access lock should still gate records after leaving the app.
6. Close/reopen offline and verify existing records and saved imports persist. Backup export and validate/preview-cancel remain useful optional regression checks; full restoration belongs only in an empty receiver.

Detailed device checklist: [DEVICE_CHECKS.md](DEVICE_CHECKS.md). User-device/OEM capture behavior, physical ARM/16 KiB assurance and independent security review remain separate acceptance items.
