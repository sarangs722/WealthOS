# Import investigation — 0.6.9

The user expressed uncertainty about statement reading and requested Superpowers for subsequent development. Systematic debugging is the current workflow: reproduce the symptom, trace the failing boundary, add a failing regression test, then make and verify a specific fix. Application behavior and version remain unchanged by this investigation.

## Executed checks

- Reran `:core:finance:test :core:imports:test --rerun-tasks`: 116 finance and 12 parser tests passed, with zero failures, errors or skips. Existing compiler warnings about unnecessary non-null assertions remain outside this investigation.
- Compared the two authorized original XLS files read only, using independent xlrd extraction versus the actual compiled JVM application adapter. The comparison matched all source transaction rows, transaction/value dates, descriptions, references, exact decimal amounts and debit/credit directions. Comparisons ran locally in memory; financial values, row digests, source filenames and source hashes were omitted from the report.
- Independently reconciled the bank's opening balance, running balances, debit/credit footer controls and closing balance using decimal arithmetic. The original files remained byte-identical before/after the check.
- Inspected the document-picker ownership, bounded read, isolated reader protocol, month selection, classification rules and transactional save paths. This inspection is not a fresh Android runtime test.

The bridge and independent checker are local ignored developer tools under `.tools/statement-import-audit/`; neither is an app dependency or an automated fixture. No original workbook was edited, exported or copied into tests. No phone, installed vault or financial record was accessed or changed. See [sanitized execution evidence](verification/statement-import-069/reader-audit.json).

## Current conclusion and missing evidence

No source-reading mismatch was reproduced for the supplied samples. This does **not** establish the user's file-provider behavior, installed version, observed preview, selected month, category decisions or saved totals. The previous release's native verification remains separate evidence; no new APK, native build or device-test run is claimed here.

The user has been asked which symptom occurs (file error, wrong/missing preview fields, unclear/wrong saved total, or not yet tested) and whether it affects the bank, card or both. Screen/action and generic error text are sufficient; personal amounts and credentials are unnecessary. Continue diagnosis from that response before proposing application changes. Monthly transaction filtering, draft/unreviewed figures and separation from manual cash flow are possible explanations to check, not confirmed causes.
