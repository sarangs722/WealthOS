# Milestone 3 — Income and Protection

Authorized after Milestone 2. Preserve the signed application identity, encrypted database and device key. Implement only Milestone 3.

1. Add pure Kotlin income-source/rate history, actual monthly salary/other income, versioned monthly expense/cash-flow summaries, emergency targets/earmarks, and term/health insurance terms/premium settlements.
2. Keep descriptive cash flow separate from asset balances. Source-derived income requires explicit completeness; manual income totals use source records only as reconciliation references. Insurance and earmarks never become assets or additional outflows automatically.
3. Use an additive encrypted schema 3→4 migration, immutable dated revisions, restrictive relationships, transactional audit and coherent book reads. Preserve every existing portfolio/bank record.
4. Add native Monthly cash flow and income screens from Home, plus Protect with emergency reserves, policies, recorded premium dues/renewals and history. Keep amount hiding and explicitly fictional demo records.
5. Verify deterministic financial/date boundaries, incomplete data, declining/provisional reserve availability, unknown coverage/premium, partial/advance payments, month-end due dates, corrections, rollback and populated schema migration. Run UI/build/lint, then update an actual populated 0.2.0 test installation without uninstalling it.

No network, notifications, new dependencies or background jobs are needed for an in-app due list. No statutory grace period, insurance adequacy recommendation, claim payout or automatic renewal is assumed. Forecasts/calculators remain Milestone 4 and portable backup remains Milestone 6.
