# WealthOS 0.6.8 — concise follow-up entry

Scope: simplify the existing detailed investment-event, manual price/value and loan/deposit-event screens. Reuse the current native calendar, keep essential financial figures first, move optional fees/notes and explanations into expandable sections, and show a summary when optional entered values are collapsed. Same-day valuation corrections and manual unit adjustments must keep their required explanations visible. Linked-cash selection and its explicit treatment remain visible and unchanged. Deposit maturity entry also uses the existing calendar.

Correct Settings to read its version from the installed package rather than a stale hard-coded label. No new dependency, financial formula, repository behavior, database schema or encryption format is planned.

Verification: run the existing portfolio/quick-entry/native public-NAV workflows, new optional-field/correction/calendar tests, and native light/dark/large-text captures. Run relevant JVM regression suites and actual debug/minified release builds with lint. Use only the guarded disposable UI Lab on the named API 29 emulator; retain the original and Release books. No phone access, personal-data reset or claim of independent/physical acceptance. Production signing remains a separate local-key step after verified artifacts exist.
