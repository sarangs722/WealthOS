# WealthOS 0.6.4 — clearer everyday flows

Milestone 6 usability refinement, version code 10, database schema 6. The final APK is permanently signed and independently verified. UI implementation, automated verification, builds and lint are complete; the signed-vault native retention check passed. No personal phone was accessed or modified by the agent.

## What changed

* Home puts net worth, assets and debt first. Cash flow and emergency funds have clear entry points. History, allocation and returns are available through Wealth insights and expandable details.
* One categorized Add menu leads to bank accounts, investments, deposits, retirement savings, property and loans. Existing balances are updated from the account page.
* Portfolio has a single search field, category filters, compact rows and a small options menu for sorting, transfers and exchange rates.
* Forecast leads with goals and saved scenarios; Tools groups searchable calculators by purpose; Protect separates emergency savings from insurance cover.
* Assumptions, source inputs, optional form settings and audit histories expand on demand. Essential incomplete, estimated, stale and provisional states remain visible.
* Summary cards stack at larger text sizes or for long amounts. Exact amounts fit their available width; exceptionally long values have an explicitly labelled horizontal scroll instead of losing digits. Currency formatting and all financial mathematics are unchanged.

Shared native Compose components live in `UiComponents.kt`. No dependency, financial-engine, database-schema, encryption or migration changes were required. Long histories remain lazy lists. An explicit development-only `wealthos.uiLab=true` build uses a separate `com.wealthos.app.uxlab` vault and launcher label; normal builds default to the existing app identity.

## Verification

* **121 JVM tests passed:** 99 finance, 12 security and 10 public-data tests, explicitly rerun during this redesign. No failures, errors or skips.
* **37 Android UI tests passed on API 29:** bank add/correction/history/recreation, investments and partial sales, FD observation versus estimate, property and loans, income/cash flow, emergency earmarks, insurance, goals/scenarios, calculators, backup/restore, privacy and public-reference workflows; plus categorized Add navigation, responsive layouts and exact large-number rendering. The financial expected values were retained.
* **30 fictional native screenshots** cover the five main destinations, Settings, Add, bank entry/details and large amounts in light/dark themes and at 150% text size. Screenshot checks reject a system error window rather than counting an obstructed capture as clean. MainActivity screenshot protection remains enabled.
* The actual minified release and original-app debug update both built successfully. Debug/release lint completed with no errors and one existing AndroidGradlePluginVersion warning each.
* Independent SDK verification passed the existing certificate pin, single v3 signer, reviewed permissions/components, non-debuggable/non-test release flags, OS-backup exclusions, disabled cleartext and all eight native libraries' 16 KiB ELF/ZIP alignment. Every one of the 97 unsigned ZIP payload entries is byte-identical after signing; only the three expected signing entries and APK signing block were added.
* The original-app update preserved UID 10227/data-directory inode 344705 and the fictional net worth, bank revisions, units, basis and gains. Native API 36 tabs, Add menu and bank entry also opened without saving. The signed minified Release update preserved UID 10229 and CE/DE directory inodes 368927/57950, plus the same fictional net worth, both bank revisions, units, basis and gain. Native tabs and the Add/bank-entry flow also passed without saving. No app was uninstalled or financial vault cleared. The API 36 emulator needed its observed System UI Wait recovery at boot; its earlier obstructed and initial-loading frames were excluded. This is app-specific verification, not a claim of platform-wide stability.

The bank workflow test now waits for the asynchronous correction-validation result, confirms typed values, and fully scrolls Save into view. Its original 100.10 → 200.20 and retained-revision assertions are unchanged. The earlier Android 10 timeout logs are retained. An API 36 intermediate passed 34 functional tests, but its System UI overlay screenshots were excluded from clean visual evidence. The final API 29 visual run has no such overlay.

Database/repository instrumentation and experimental 16 KiB runtime stress tests were not repeated for this UI-only change. Their earlier evidence and limits remain in the milestone documentation; the final signed package still requires the normal ELF/ZIP alignment gate. Physical-device acceptance remains for the user to perform. No independent security audit is claimed.

## Artifacts and existing data

* **WealthOS Release:** [permanently signed APK](../.tools/production-release/WealthOS-0.6.4.apk), application ID `com.wealthos.app.release`, SHA-256 `5c08f6561e09c10e87f21f6740915b291f88cbf47cccf8946dca14e8348db27a`.
* **Existing WealthOS development app:** [matching development update](../.tools/WealthOS-0.6.4-development.apk), application ID `com.wealthos.app`, SHA-256 `a119c99f1a59c8518f9a899c1cdacb5f43024533013fb71052e71165db86b0c8`. This retains the original development signer; use the separately signed Release identity for release builds.
* Existing permanent public certificate SHA-256: `cf06385769b4d9e0992910521f210a77407cb284e2963290fbb10006b8d88054`. The user entered its passphrase only in a masked local console; no new signing identity was created and no private key/passphrase was read into chat.

Install the update matching the existing application identity. Signing identities, encrypted records and existing APKs are preserved; uninstalling or clearing the financial vault is not part of the update procedure.

The internally signed 0.6.3 intermediate is retained unchanged. The completed large-text review found an awkward digit wrap and led to the 0.6.4 layout correction. Use 0.6.4 for the completed refinement.

Next step: user review of the revised interface and the already agreed guided physical-device checks; no new financial milestone is started.

Evidence: [verification summary](verification/ui-064.json), [UI test log](verification/ui-064-api29.txt), [signature/package gate](verification/ui-064-signature.json), [payload comparison](verification/ui-064-payload.json). Fictional previews: [Home](verification/ui-064-home.png), [Portfolio](verification/ui-064-portfolio.png), [larger text](verification/ui-064-home-large-text.png), [large exact amount](verification/ui-064-large-amount.png).
