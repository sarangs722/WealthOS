# Milestone 3 — Income and Protection

Extends [portfolio accounting](MILESTONE_2_ARCHITECTURE.md). Three modules remain: pure Kotlin `core:finance`, encrypted Room `core:database`, and native Compose `app`. No dependency, permission, upload or background worker was added. Forecast and Tools remain outside this milestone.

## Persistence and revisions

Schema **4** adds nine tables to schema 3: `income_sources`, `income_rates`, `income_records`, `monthly_cashflows`, `emergency_targets`, `emergency_allocations`, `insurance_policies`, `insurance_terms`, and `premium_settlements`. Existing tables retain their definitions. `HouseholdMigrations.FROM_3_TO_4` explicitly creates tables/indexes; all older migrations remain registered. No destructive migration fallback is allowed.

Amounts are decimal TEXT; calculations use BigDecimal. UUID relationships use restrictive foreign keys. Source/policy currencies and kinds remain immutable. Effective dates/months and UTC entry timestamps are distinct. New observations append revisions with correction explanations; unique business-date/revision indexes and Room transactions serialize revision assignment. Metadata edits preserve before/after audit events. Initial source/rate and policy/terms creation commit atomically with their audit events. A coherent BankBook includes HouseholdBook inside the existing read transaction and invalidation Flow.

## Income and monthly summaries

Income sources record salary, rent, freelance/business income, pensions, interest/distributions or other income. Dated expected monthly net rates are planning inputs only. Actual receipts use a source/month record, with optional salary gross and deductions that must satisfy gross minus deductions equals net. Late receipts after a source ends are allowed. Expected pay is never substituted for receipts. Actual months cannot be future-dated; zero is a known value, distinct from missing data.

A monthly summary is unique by month/currency, with retained revisions. It uses either a manually entered net-income total or the latest actual record from each source for that month. In manual mode source receipts are only a reconciliation reference; they are not added again. Source mode requires a receipt, including explicit zero when applicable, from every source active in any part of the month. Income and outflow completeness must both be confirmed. Missing inputs leave surplus unavailable. Current-month entries are labelled month-to-date.

Surplus before investments equals net income minus essential/discretionary/other outflows, actual premiums, actual loan principal and actual loan interest. Remaining cash subtracts investment contributions once more. A loan principal payment reduces cash availability even though it is not an interest expense. These descriptive monthly totals do not mutate bank balances, ledger events or holdings. They are not an automatically reconciled bank statement. No cash posting or scheduled future receipt is inferred.

## Emergency reserve

An emergency target is a dated INR amount. Each earmark references exactly one existing bank cash balance or liquid instrument and records its total allocation in native currency, rather than an increment. The latest effective date/revision per source applies. Zero releases an earmark. Creating a positive earmark requires a known liquid source value and cannot exceed that value.

Progress is recalculated against current source availability, including provisional tracked bank cash. If a source declines, the counted allocation is capped at its nonnegative available value. Nonliquid sources count zero; unknown values or missing FX leave the consolidated allocation unavailable while retaining a known INR subtotal. Released allocations count zero even if their source has become unvalued. Source valuations older than 30 days and manual FX older than seven days are flagged. Instrument liquidity uses the existing explicit liquidity policy and user designation; restrictions and penalties are not inferred.

Coverage in months is only a descriptive ratio to the latest INR summary's confirmed essential, other, premium and loan outflows. Discretionary spending and investments are excluded. Zero/missing/unconfirmed reference outflows leave the ratio unavailable. The reference month is exposed; a current-month reference is explicitly month-to-date. No default target or recommended coverage is fabricated. All earmarked assets remain counted exactly once in net worth.

## Insurance and dates

Term and health policy metadata is separate from append-only effective terms. Terms retain recorded coverage dates, optional cover limit, optional premium, installment frequency, first tracked premium due, optional last premium due, and individual/family-floater scope. A floater is recorded once with a shared limit. Coverage is never an asset or an assumed claim payout; policy limits are not summed into a promise of available protection.

Recurring due dates are calculated from the original anchor plus whole month multiples, preserving month-end intent (31 January → 29 February → 31 March in a leap year). One-off premiums have one due date. The optional last premium date supports limited-pay contracts. No installments before the first tracked anchor are invented. New effective terms replace the prior schedule from their effective date; prior unpaid tracked installments remain visible. Inactive terms suppress ongoing dashboard action items. A recorded period ending is not an automatic renewal, and an accepted premium entry does not extend the coverage period.

A settlement is the cumulative amount paid toward a particular installment as of an actual payment date. Later corrections replace the amount for that due date; they do not add it again. Partial settlements reduce outstanding value; overpayments do not automatically settle other installments. Unknown premiums remain unknown even when a settlement exists. Payments do not change bank cash or monthly summaries automatically. The recurring monthly-budget equivalent is informational, not actual expense; one-off or completed limited-pay premiums have no recurring budget.

Protect shows recorded upcoming/past premium dates and cover-end attention items in app. There are no system notifications or automatic renewals. Status wording describes records and asks for confirmation; no universal grace period, lapse rule, claim eligibility or insurer acceptance is inferred. Official context reviewed: [IRDAI health insurance FAQs](https://irdai.gov.in/faqs-on-health-insurance-regulations), [IRDAI health department](https://irdai.gov.in/health-dept), and [LIC policy conditions](https://www.licindia.in/web/guest/policy-conditions). Users enter their actual policy terms.

## Native interface and privacy

Home opens monthly cash flow and income sources; Protect contains reserve progress, earmarks, policies and date attention. Native forms support source/rate/receipt history, summary revisions, reserve changes, renewals and settlements. Read views respect amount obscuring, including notes, while edit forms intentionally display entry values. Demo household records are explicitly fictional, read-only and constructed in memory; they do not enter the vault or increase demo net worth.

Existing SQLCipher encryption, Keystore wrapping, optional device gate, screenshot protection and backup exclusions are unchanged. No full policy identifier, medical diagnosis or bank credential is required. Portable password-protected backup remains unimplemented; see [security limits](SECURITY.md). This is a development build, not release-hardening completion.
