# WealthOS foundation

Historical Milestone 1 design. See [Milestone 2 portfolio architecture](MILESTONE_2_ARCHITECTURE.md), [Milestone 3 household architecture](MILESTONE_3_ARCHITECTURE.md), [Milestone 4 analytics](MILESTONE_4_ARCHITECTURE.md), and [current Milestone 5 architecture](MILESTONE_5_ARCHITECTURE.md) for schema and policies that extend this foundation. Milestone 5 adds optional, manually triggered public networking; the no-network statement below describes the original milestone only.

## Boundaries

* `core:finance`: pure Kotlin/JVM, `BigDecimal` parsing/formatting, immutable account/balance types, repository contract and deterministic bank valuation. Android, SQLCipher and Compose are absent.
* `core:database`: Room entities/DAO, transactional repository, SQLCipher open factory, Keystore wrapping. Flows publish a transactionally consistent bank book.
* `app`: lifecycle ViewModel, constructor injection, native Compose Material 3 screens and device access lock. No network permission, online account, analytics, ads or background jobs.

Features stay in packages until their size justifies separate modules. Hilt and WorkManager are intentionally not needed for a single repository and no scheduled work. Future financial engines must remain in `core:finance`.

## Schema and dates

UUID primary keys. `bank_accounts` stores identity, native currency, account kind and creation/update instants. Currency is immutable after creation. `balance_observations` references its account with restrictive deletion. It stores lossless decimal TEXT, currency, ISO effective date, UTC recorded instant, revision and note. Unique `(accountId, effectiveDate, revision)` identifies correction order. `audit_events` preserves before/after metadata and append events inside the same financial transaction.

Schema 1 is the initial account/observation/audit layout. Schema 2 adds a non-null observation note with an empty default. `MIGRATION_1_2` explicitly adds that column. Room exports schema 2; an encrypted integration fixture constructs the original schema and validates its upgrade with retained records. There is no destructive migration fallback, delete/reset shortcut, or public observation update/delete operation.

Account creation, opening observation and audit events commit atomically. Same-day observations append a new revision and require an explanation. A new backdated observation is legal; it cannot replace a newer effective balance. Creation and observation times do not decide financial chronology. Future observations and dates before 1900 are rejected by the repository. Multiple writes serialize inside Room transactions, including revision allocation.

## Bank valuation policy

For each known account, select the greatest effective date on or before the requested date, then its greatest revision. An observation replaces the prior known balance; it is never added to it. Positive balances are assets; negative balances are overdraft liabilities. Net bank wealth is assets minus liabilities. All arithmetic stays decimal, with no SQL REAL/SUM coercion. Presentation uses native currency scale and HALF_EVEN only for display; entry with excess fractional precision is rejected, never rounded silently. INR uses Indian digit grouping and supports up to 24 integer digits.

All bank funds are treated as liquid for this slice; restricted/contract deposits are outside its scope. Missing observations or foreign accounts without FX rates make the consolidated total unavailable. The known INR subtotal is explicitly separate. Empty records show an empty state, never fabricated wealth. Foreign balances retain their native currency but are not assumed equivalent to INR. Balances over 30 days old are flagged. Totals cover bank accounts only, not complete personal wealth.

History uses known accounts and latest eligible observations at each recorded date. Before an account's first observation, that historical aggregate is incomplete. Between dates, balances are carried forward. The chart uses steps and gaps for incomplete dates. Period change describes recorded balance differences, never income, investment returns or causal attribution. Details expose observations, revisions and entry timestamps. Loans, investments, earmarks and insurance cannot be added in this slice, so those categories cannot double count assets.

## UI state

Five navigation destinations form the shell. Home/Portfolio are functional; later destinations state their milestone and availability. Account details, edits, balance history and privacy settings are native Compose. Demo is read-only fiction constructed in memory, never seeded into the user's DB. Switching demo mode does not modify real records. Form writes are serialized by the ViewModel and buttons remain disabled during saves. Database opening/decryption runs on IO; Room suspend methods manage database threading. Errors preserve the existing vault and show recovery guidance without logging financial inputs.

## Extending the ledger

Future transaction and contract tracking must add normalized records and explicit migrations. Account cash must remain distinct from instrument holdings, and statement totals must remain reconciliation references. Never infer pre-opening transactions during a Quick Add upgrade. Snapshot reconciliation, FX and performance calculation fixtures are required before adding those features.
