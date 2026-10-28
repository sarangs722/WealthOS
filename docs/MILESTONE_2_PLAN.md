# Milestone 2 — portfolio implementation

Completed and verified; see [results and remaining limits](PROGRESS_MILESTONE_2.md).

Authorized after the user installed Milestone 1 and entered real bank records. Preserve application ID, signing identity, database filename and Keystore aliases. Upgrade through an additive encrypted schema 2→3 migration. Never uninstall/reset a user's installed app.

1. Add pure decimal models for instruments, opening positions, FIFO lots, trades/corporate actions, manual prices/valuations, deposit/loan contracts, actual contract events, dated manual FX and statement reconciliation references.
2. Model cash legs explicitly. New bank observations supersede recorded movements through their date; same-day/backdated movements require an explicit already-reflected/provisional choice. Statement totals and allocation earmarks never become additional assets.
3. Persist all financial writes and audit entries atomically. Re-read the entire affected ledger before committing backdated events; reject overselling, duplicate maturity payouts and incompatible currencies.
4. Extend native Portfolio and Home, with Quick Add existing positions, detailed events, category grouping, manual valuation, bank/custody association, deposit/loan detail and reconciliation. Retain amount hiding and isolated demo.
5. Test FIFO/SIPs/partial sales/splits, unknown basis, cash/snapshot interactions, transfers, FX, deposit conventions and loan principal/interest, transaction rollback and encrypted migration of populated schema 2. Build/lint and run UI workflows, then verify an APK update over the previous version on an isolated emulator.

No new dependencies or live data providers are needed. Automatic market data belongs to Milestone 5; XIRR/forecasts/calculator screens belong to Milestone 4. Manual FX observations enable valuation but never infer a foreign-currency trade's actual cash conversion.

Deposit estimates use an explicitly selected generic convention (completed anniversary periods plus ACT/365 simple stub, or ACT/365 simple interest). They are gross estimates, not provider-specific bank quotes. Only actual recorded RD installments accrue; scheduled installments and loan repayments are not invented. Observed values remain authoritative and distinct. Preserve the contracted rate; no public rate update is involved.

Reference checks: [bank-specific quarter/day-count differences](https://www.axis.bank.in/deposits/fixed-deposits/change-in-interest-calculation-methodology), [RD conventions](https://www.indianbank.in/departments/recurring-deposit/), [stock split mechanics](https://www.investor.gov/introduction-investing/investing-basics/glossary/stock-split). FIFO here is a transparent book-cost policy, not jurisdiction-specific tax reporting.
