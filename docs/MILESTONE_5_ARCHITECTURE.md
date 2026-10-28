# Milestone 5 — public references and explicit financial posting

Extends [Milestone 4](MILESTONE_4_ARCHITECTURE.md) without changing its financial mathematics. Source verification and limitations are in [MILESTONE_5_SOURCES.md](MILESTONE_5_SOURCES.md).

* `core:finance` remains Android/network independent. It defines source observations, provenance, bindings, validation rules and the public repository interface. `BigDecimal` values retain their precision. Financial valuations optionally reference a source observation.
* New `core:network` is a small Android library using platform HTTPS, XML and JSON, with pure Kotlin CSV/file parsing. Provider requests are fixed and contain no financial inputs. Parsers return immutable domain observations; they cannot access the database or mutate wealth. There are no new third-party dependencies.
* `core:database` stores public cache, permission, bindings, refresh history and explicit applications in the same encrypted vault. Repositories transactionally validate and append rows. Audit failures roll back financial values and source links together. Cached data is separate from posted valuation/FX tables.
* `app` handles optional refresh consent, lifecycle-scoped IO, explicit source review/application, SAF personal NAV-file selection and manual economic references. UI mutation is disabled in demo mode. No WorkManager task is required for manually triggered refresh. No network request runs at startup. Failed requests preserve cache and financial tracking.

## Schema 6

Explicit additive migration 5→6 creates `public_settings`, `public_observations`, `nav_bindings`, `public_refresh_runs` and `public_applications`; inserts network-disabled preference. All 26 earlier tables are unchanged. Existing migration chain 1→2→3→4→5→6 remains registered; there is no destructive fallback or key change.

Observation decimal values are TEXT; retrieval times are UTC instants, effective observation dates are ISO dates, UUIDs identify records. Provider/series/date/revision is unique. A fingerprint index supports deduplication against the latest same-date revision, allowing a later correction to restore an earlier numerical value as a new revision. Bindings append per-instrument revisions, with inactive bindings retaining prior identity. Applications reference source/instrument/valuation/FX using restrictive foreign keys and unique source-target keys. Earlier observations and financial revisions are retained.

Each application must target exactly one valuation or FX row. NAV import is restricted to INR transaction-based mutual funds; code and optional ISIN must match at posting time. Same-day manual valuations require a reviewed revision. Current ledger valuation still selects effective date then revision. Cached public observations by themselves do not count toward net worth, income, returns or forecasts.

Unit-price quotes now have dates independent of an instrument's known opening date. This allows yesterday's NAV for a holding established today; observed total balances cannot predate their asset opening, and transactions still cannot precede opening. Historical valuation before opening remains explicitly incomplete. Existing split/bonus quote adjustments remain in effect.

Imported AMFI NAVs use closing-date semantics: a split/bonus effective on the NAV date is already represented in that day's NAV, even when the event is recorded later in the ledger. It must not discount that quote a second time. Corporate actions on later financial dates still adjust carried prices and remain labeled estimates. Manually entered quotes retain the existing same-day event-sequence anchor, because they may precede a recorded action.

Source pages expose source/retrieval dates, validation provenance, freshness and publication caveats. Official HTTPS data and user files have distinct labels; source authenticity is never claimed for an imported file. Applying a reference does not introduce a transaction, cash flow, or change to contractual/planning assumptions.
