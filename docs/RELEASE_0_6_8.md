# WealthOS 0.6.8 — easier everyday updates

Code 14. Database schema 7, encryption/backup formats, dependencies and financial calculations are unchanged.

## Changes

- Investment transactions put units, total amount and a native date picker first. Fees and optional notes expand when needed; a collapsed summary shows entered fees and whether a note exists. Manual adjustments keep their required explanation visible.
- Manual unit-price/property/deposit value updates use the native calendar. An optional source note stays out of the main form, while a same-day correction exposes its required explanation directly. Existing snapshot holding updates retain their current-value-first flow.
- Loan/deposit events use clearer principal/interest labels and an expandable fee/note section. A loan payment and a maturity payout are named explicitly. Cash-account selection and explicit already-reflected/provisional treatment remain visible and unchanged.
- New deposits select maturity through the native calendar. Settings displays the actual installed package version instead of the old hard-coded 0.6.4 label.

The transaction, valuation and contract-event save payloads are byte-identical to 0.6.7. Only entry presentation and date selection changed; source comparison and numeric workflows verify that existing accounting rules are retained. No earlier history is inferred and no existing financial records are rewritten.

## Verification

- 129 JVM cases passed: 107 finance, 12 portable security and ten public-data cases.
- All 17 affected native cases passed on WealthOS29 / Android 10 / API 29 / 4 KiB. These cover collapsed fees/notes, explicit linked-cash treatment, principal versus interest, correction revision preservation, installed version, existing portfolio/quick-entry/NAV workflows, and light/dark/150% font reachability.
- After the full 17-case pass, the maturity-payout title was clarified. All three affected render cases passed again on the final UI Lab; this is not three additional distinct tests. No save payload changed after the full workflow run.
- The initial run's FD timeout and focused outdated-note interaction failure are retained. The test now checks calendar confirmation/dismissal and the selected date, and expands the optional first-valuation note. Its known principal, observed value, contract estimate and other financial expectations were not weakened.
- Screenshot capture now synchronizes against the Compose root or active dialog. The old surface capture could show the preceding screen after navigation; it is not used as final visual evidence. MainActivity screenshot protection is unchanged.
- The 73-case encrypted-storage suite from 0.6.7 remains historical evidence; it is not claimed as rerun for this UI change. New native workflows save and reread actual encrypted records, including after activity recreation.

## Artifact status

Actual debug and minified coexisting Release builds passed. Debug/release lint each reported zero errors and zero warnings in the offline run with the existing pinned dependencies. The original development certificate verifies; the unsigned Release passes ZIP/ELF 16 KiB alignment checks. The signed 0.6.7 artifact is unchanged.

- [Original WealthOS development-app update](../.tools/WealthOS-0.6.8-development.apk), retaining the existing development certificate. SHA-256: `efba66c2ab3649a58bad192b4b4b663b94705648ca4bf7d13dd45e82c558fc26`.
- [WealthOS Release update](../.tools/production-release/WealthOS-0.6.8.apk), signed with the existing permanent certificate. SHA-256: `a45ff0805decf5703b6d420c2281fbc8a301ef642c6cd566d01f0de58f31c55c`.
- Independent package verification passed: the expected release identity and single v3 signer, permission/component inventory, OS backup exclusions, release flags and ZIP/ELF 16 KiB alignment for eight native libraries. All 98 application payload entries are byte-identical to the tested unsigned input; only the three signing metadata entries were added. [Package report](verification/daily-updates-068/release-reverified.json), [payload comparison](verification/daily-updates-068/release-payload.json).
- [Build, test and artifact evidence](verification/daily-updates-068/verification.json). The [previous signed Release](RELEASE_0_6_7.md) remains unchanged and available.

The signing-certificate SHA-256 remains `cf06385769b4d9e0992910521f210a77407cb284e2963290fbb10006b8d88054`. Signing was completed locally on 29 October 2026. No password or private key belongs in chat or GitHub.

Install only the update matching the existing app identity, without uninstalling or clearing its storage.

No personal phone access, personal-vault reset, physical ARM/16 KiB execution or independent security review is claimed. Guided device checks and independent review remain pending. See the [implementation plan](DAILY_UPDATES_0_6_8_PLAN.md).

## Review captures

These are native Compose test-host images with fictional records, not personal screens: [investment entry](screenshots/ux-daily-investment-light-1.0.png), [loan payment in dark mode](screenshots/ux-daily-loan-dark-1.0.png), [price update](screenshots/ux-daily-price-light-1.0.png), and [maturity payout at 150% text](screenshots/ux-daily-deposit-light-1.5.png). The [capture manifest](verification/daily-updates-068/screenshots.json) lists all 51 retained local captures. [Verification report](verification/daily-updates-068/verification.json) and raw failures/final successes document the actual scope.
