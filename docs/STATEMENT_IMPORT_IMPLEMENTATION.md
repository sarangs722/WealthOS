# Statement import implementation

Scope: first native, local HDFC Bank and Tata Neu HDFC statement import. Groww/CAS integration remains a separate follow-up. User statements stay ignored and read only; all committed fixtures are independently fictional.

Implementation is now present in the 0.6.9 candidate. See [release status, schema, verification and remaining limits](RELEASE_0_6_9.md). The sequence below records the work boundaries; it is not a claim of completed signing or phone acceptance.

1. Verify current HSSF dependency on Android 10, including minification. Parse bounded XLS in an isolated, time-limited process with no vault or network permissions; parse native CSV with the same verified header adapters. Reject unsupported workbooks rather than guessing.
2. Add independent statement/spending domain records. Preserve transaction/value dates, source hashes, reviewed classifications, overlap decisions and append-only revisions. Card repayments are settlements; spending does not automatically change balances, liabilities or existing actual-cash summaries.
3. Migrate encrypted Room schema 7 explicitly and extend backup/restore validation at the same time. Save atomically, reject repeated files and require review of overlap candidates. Retain corrections and support voiding an import through a revision.
4. Add a concise native flow: document picker, source nickname, month, review, save. Expose incomplete coverage and separate spending/cash movement totals. Keep raw statements out of persistent storage. Respect privacy amount masking and local lock.
5. Run deterministic parser/accounting tests, encrypted repository/migration/backup tests, native end-to-end checks and real debug/minified release builds/lint. Do not install on or reset the user's phone. Report actual verified outcomes and remaining limits before release signing.

## Minified reader check

Build the disposable minified app with `:app:assembleRelease -Pwealthos.uiLab=true`. This is signed only with the development test identity and uses `com.wealthos.app.uxlab`; it is not a production release. Build the framework-only probe APK with `:app:assembleDebugAndroidTest -Pwealthos.uiLab=true -Pwealthos.readerProbe=true` and install it only on the guarded emulator. Run `com.wealthos.app.uxlab.test/com.wealthos.app.StatementReaderInstrumentation` against the installed minified UI Lab.

The probe refuses a debuggable target or a different application ID. It uses framework APIs only, checks both independently fictional XLS files, SHA-256, row counts, native decimal values, directions and explicit INR currency. Ordinary AndroidX instrumentation linked against debug classes cannot reliably test a shrunk target because shared Kotlin/tracing classes may be renamed or removed. No production keep rules are added to retain test-harness classes. `wealthos.readerProbe` is rejected unless `wealthos.uiLab` is enabled.
