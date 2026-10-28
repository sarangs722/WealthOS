# Portfolio architecture and financial policies

## Modules and storage

The three existing modules remain: pure Kotlin `core:finance`, encrypted Room `core:database`, native Compose `app`. No dependencies, network permission, cloud service, analytics or online account were added. The ViewModel injects bank and portfolio repositories over the same SQLCipher database. A Room invalidation flow re-reads the complete book inside one database transaction, so a linked trade/cash write cannot briefly publish half of a financial operation.

Schema **3** adds instruments, investment entries, dated valuations, financial contracts, contract events, cash movements, transfer groups, manual FX observations and brokerage statement references. Existing bank/audit tables are unchanged. Relationships use UUID foreign keys with restrictive deletion; ledger sequence and observation revisions have unique indices. Currency belongs to an immutable instrument/account and is retained explicitly on cash/FX records. Amounts, units and rates persist as decimal TEXT. Audit writes share the financial transaction.

The explicit additive migration 2→3 contains SQL matching exported Room schema 3. Migration 1→2 remains registered, so the original chain also reaches schema 3. Application ID, database filename and Keystore alias stay unchanged. `versionCode=2` permits an in-place APK update. Missing keys, migration/decryption errors and invalid persisted state still fail closed; no reset/destructive fallback exists.

## Accounting precision and dates

Money input supports the native currency's scale, up to 24 integer digits. Units, quotes and manual FX permit up to 12 fractional digits. Ambiguous grouping/exponents are rejected. Sums use `BigDecimal`; book-cost allocation and contract math use 512 significant decimal digits, sufficient for the supported bounded contract envelope. Financial equality uses numeric comparison, so 10.0 and 10.00 close the same lot/principal. FIFO consumes the final remainder exactly. Display rounds money HALF_EVEN at native scale; quotes retain up to 12 decimals. Floating point is confined to chart coordinates.

Effective dates determine financial order. An instrument's opening date establishes the first known position; earlier aggregate history is incomplete. On the same date, explicit per-instrument sequence orders ledger events; valuation revisions order corrections. Record instants are retained for audit, not used to invent financial chronology. Valuations retain explicit event-sequence cutoffs for same-day reconciliation. Future actual events/observations are rejected; contractual maturity and projected schedule dates may be in the future.

## Holdings and book gains

Quick Add creates an opening lot with optional known total cost and no synthetic bank cash flow. Unknown cost remains null; pre-opening cash-flow history stays incomplete even when a basis is known. Subsequent entries support purchases, redemptions/partial sales, cash distributions, reinvestments, split/bonus ratios and explained signed unit adjustments. Every backdated entry is replayed through the later recorded ledger before committing; overselling and unsupported fractional corporate-action results are rejected.

FIFO book basis includes purchase fees. Net sales subtract fees; realized gains consume known FIFO costs. Reinvested distributions create both distribution credit and acquisition basis, with only extra fees affecting cash. Split ratios preserve lot cost; bonus lots add zero additional book cost after existing lots. A manual removal does not fabricate a realized sale. This is transparent book accounting, not jurisdiction-specific tax reporting or a complete return engine.

A quote carried across a split/bonus is theoretically adjusted to the new unit count, avoiding artificial wealth creation. That quote is explicitly an **estimate** until a subsequent observed price includes the corporate action. The source quote, adjusted quote, dates, sequence and remaining lots are inspectable. Missing prices/basis/history never become fabricated zeroes or XIRR/CAGR. Those return methods remain Milestone 4 work.

## Cash and snapshot reconciliation

Cash is separate from holdings. Brokerage account observations must contain cash only. A brokerage statement's total portfolio value is a reference, never an asset. Reconciliation compares cash plus assigned same-currency instruments at the reference date; missing components make it unavailable.

Each linked cash leg references exactly one trade, contract event or atomic transfer group and an existing bank observation. Its account currency must match the financial event. The user explicitly chooses provisional adjustment or already-reflected treatment. A snapshot older than the event cannot already include it. For an eligible snapshot, later-dated cash movements apply; same-day/backdated movements apply only when explicitly anchored provisionally to that snapshot. A subsequent authoritative observation through the event date absorbs the earlier movement. A newer snapshot still dated before the event does not absorb a later flow.

The UI identifies provisional deltas and retains observed balances/movement history separately. Unlinked events do not alter tracked cash; the user must update the actual bank observation independently. Two same-currency internal-transfer legs commit atomically. Cross-currency cash conversions require actual execution data and are not inferred from manual valuation FX rates. Balance differences remain unexplained movement, not automatically income/return.

## Contracts and observed assets

EPF/PPF/NPS and property records use dated observed totals without invented statutory interest/tax rules. Property values must be the user's attributable share. Gold/commodity instruments support native units (such as grams) with manual quotes; financial gold may be a separately named unit-tracked instrument or an ETF.

FD/RD estimates use the selected nominal contract rate: simple ACT/365, or completed monthly/quarterly/annual anniversary periods followed by an ACT/365 simple stub on the accrued amount. Month-end anniversaries use calendar dates. Only recorded RD installments accrue, each from its actual installment date. Estimates stop at contractual maturity and remain an asset until actual closure is recorded; no automatic bank payout or reinvestment is invented. Simple-convention interim payouts subtract gross accrued interest; cumulative deposits pay at closure. Actual maturity principal, interest and withholding/fees are separate inputs. Observed totals remain distinct and authoritative; later uncovered contract events require reconfirmation instead of silently adding them twice. Previous earned interest, provider day-count rules, tax and penalties are not reconstructed.

Loan liabilities use known opening principal less actual principal repayments. Actual interest/fees reduce linked cash without reducing principal. The original remaining-term schedule is a fixed nominal-rate/monthly-reducing estimate (annual rate / 12), HALF_EVEN at currency scale, final principal remainder cleared. Future payments are not treated as paid. Daily-interest resets, floating-rate schedules and tax accounting are outside this generic schedule; actual recorded payments remain authoritative.

## Consolidation and liquidity

Home includes recorded cash, positive instrument values and deposits, minus overdrafts and outstanding loan principal. Foreign valuations use the latest dated manual INR-per-native-unit observation. Missing required values/FX leave totals unavailable, with a labelled known subtotal. Valuation inputs older than 30 days and FX older than 7 days are flagged. Native exposure and INR allocation remain inspectable; incomplete allocation is labelled as a known subtotal.

Liquid net worth is accessible cash plus positive assets explicitly marked liquid, less all liabilities. Retirement/property/deposit defaults are excluded; the user can explicitly change asset liquidity. Statement references, emergency earmarks and insurance coverage never become additional assets. Historical charts show carried observations, contract estimates and gaps at incomplete dates; movement is not represented as investment return.
