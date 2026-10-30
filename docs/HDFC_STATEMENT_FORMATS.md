# HDFC statement format review

Inspected locally on 30 October 2026, using the two user-supplied exports. This is a verified input contract and implementation plan, not a shipped importer. WealthOS remains 0.6.8, schema 7. The original workbooks were read only; no financial record, bank balance or installed application was changed.

Subsequent development: [0.6.9 candidate](RELEASE_0_6_9.md) implements these adapters. Reader verification also found a bank transaction date outside the requested period while its value date is within it. Preserve requested periods separately, group by actual transaction dates and flag the mismatch; never discard that row merely to fit the requested range. The historical inspection status above describes the initial review, not the later implementation.

Only generic headers, formats and validation outcomes are recorded here. Do not commit the source workbooks, their names, identifiers, narratives, amounts or source-derived fixture rows. `.codex-attachments/` and `.tools/` are excluded from Git. Test fixtures must be independently fictional.

## Verified bank export

- Container: binary OLE Compound File / legacy Excel `.xls`, not XLSX, CSV or HTML. Renaming the extension is not conversion.
- The transaction table follows a customer/account preamble. Find the complete header signature rather than assuming a fixed starting row.
- Header signature, in order: `Date`, `Narration`, `Chq./Ref.No.`, `Value Dt`, `Withdrawal Amt.`, `Deposit Amt.`, `Closing Balance`.
- Transaction and value dates are text in `dd/MM/yy`. They can differ. Use transaction date for monthly grouping; retain value date separately. Resolve the two-digit year against the statement's explicit period, rejecting ambiguity rather than using the runtime's century pivot.
- Withdrawals, deposits and closing balances are numeric spreadsheet cells. An unused direction is blank, not an unparseable amount. The sample has exactly one positive debit/credit direction per transaction. Both positive, both blank, negative or invalid values must trigger review/rejection in future adapters rather than silent sign guesses.
- The footer has a separate `Opening Balance`, `Debits`, `Credits`, `Closing Bal` summary. Identify this section explicitly; do not import summary numbers as transactions.
- Local validation passed: all monetary cells checked were at paise precision; every consecutive balance equation, the opening-to-first-row equation, debit/credit footer sums, last-row closing balance and opening-to-closing equation reconciled exactly in decimal arithmetic. This establishes consistency of this sample, not universal format coverage.
- Narration and reference strings may contain sensitive identifiers. Preserve only what is needed for local review/matching, inside encrypted storage; do not use floating-point or numeric coercion for references.

## Verified Tata Neu HDFC card export

- Container: binary OLE Compound File / legacy Excel `.xls`.
- The transaction table is laid out across merged/spacer columns. Match physical header positions; do not discard blank cells and then index a compressed row.
- Header signature: `Transaction type`, `Primary / Addon Customer Name`, `Date & Time`, `Description`, `Base NeuCoins*`, `AMT`, `Debit / Credit`.
- In the supplied layout the zero-based physical columns are 0, 4, 9, 12, 18, 20 and 23 respectively. Header-driven matching must tolerate moved columns and reject missing/duplicate required headers.
- `Date & Time` is text in `dd/MM/yyyy / HH:mm`. Strict parsing passed for all transaction rows. Retain the statement's local date/time without inventing a UTC instant. Monthly grouping uses its transaction date, explicitly labelled as such.
- `AMT` is positive decimal text with two decimal places; grouping separators may occur. Validate Indian/international grouping before removing separators. `Cr` in `Debit / Credit` marks a credit. Debit rows in this sample have a blank marker. This blank-means-debit convention is specific to this verified template, not a generic CSV default; unknown markers must fail validation.
- The sample's transaction dates cover two calendar months. A billed statement is not a complete calendar-month expense report. Offer a month selector based on parsed transaction dates and show partial/unconfirmed coverage until the user confirms all relevant files.
- Customer names, reward/NeuCoin columns, total/minimum dues, reward summaries and EMI information sections are outside the ordinary transaction table. Do not import them as expenses, cash income or additional transactions.
- A credit can be a payment, refund or adjustment. Direction alone cannot classify it. Likewise a debit can need special handling for an EMI, fee or reversal. EMI schedule/summary rows must not create a second expense for already listed transactions.
- The sample establishes the domestic row format. International/currency variants and other card templates remain unverified. Unknown transaction types/currencies must remain unsupported or explicitly reviewed, never assumed INR solely from the file extension.

## First native import experience

**Cash flow → Import statement → Choose file → Review month → Save spending summary.**

1. Pick one or more files through Android's document picker. Recognize these two header signatures automatically. Show a short source label and account/card nickname rather than requesting column mapping or account/card numbers for a recognized export.
2. Select the calendar month from the detected transaction dates. Show the covered date range and a concise partial-month indicator. The card's statement month/file name must not override individual dates.
3. Show the proposed spending total and an **Items to review** count. Keep the transaction list available for corrections. Suggested categories are editable, and uncertain payments remain unclassified. Support bulk confirmation without silently accepting unknown rows.
4. Keep ordinary spending, income, refunds, own transfers, investments, loan components and card repayments distinct. A bank debit paying the card bill is a settlement, not another purchase expense. A card purchase affects spending on its transaction date without claiming bank cash left that day.
5. Compare with any existing summary and replace through an explicit new revision; never add imported totals on top of an already entered manual total. Reimporting the same file is idempotent. Overlap candidates require review; identical legitimate transactions retain their multiplicity.

The initial spending view must remain separate from the existing confirmed actual-cash-flow definition. It cannot claim bank cash surplus from card purchases or silently update net worth, bank observations, investment holdings or card debt. Saving normalized imports requires encrypted provenance, explicit schema migration, audit/reversal support and portable-backup validation together.

## Reader and validation gates

The actual downloads make legacy `.xls` support a first priority. CSV/delimited remains useful, but CSV/XLSX-only support would not handle these files. No Android Excel dependency has been adopted by this inspection.

The inspection used a read-only local `xlrd` analysis tool, not app runtime code. Apache POI's official release page currently lists 6.0.0 and its homepage requires Java 17. This is a candidate to evaluate, not proof of native Android compatibility. Verify the specific HSSF reader path on Android 10 and a minified build before adoption. POI explicitly recommends resource containment for untrusted documents; a successful read of these small samples is not a security review. See [releases](https://poi.apache.org/download.html), [requirements/advisories](https://poi.apache.org/) and [secure processing](https://poi.apache.org/security.html).

The bank's native numeric cells are stored as IEEE binary values by the Excel format itself. A text-only `BigDecimal` rule cannot describe their input encoding. The reader must convert Excel's numeric representation through a documented canonical decimal conversion at the input boundary, verify representable INR paise precision and supported magnitude, and reject ambiguous/excess-precision values. All subsequent accounting, reconciliation and persistence use `BigDecimal`/decimal text. Never calculate balances, round arbitrary sub-paise input into acceptance, or accumulate totals in `Double`. Card text amounts parse directly to decimal. Add independent numerical fixtures for both paths.

Bound file size, workbook streams, records, rows, cells and text lengths before allocation. Reject unsupported/encrypted/macro-containing files, formulas in required fields and malformed tables without evaluating formulas, macros or links. A reader must not execute embedded content or access the network. Use a contained parsing process with cancellation/time limits if the selected library cannot guarantee resource bounds. Do not log financial parser payloads or raw exception messages.

Required synthetic tests include both header signatures, preambles/footers/reward/EMI sections, merged/spacer columns, strict dates and period-based years, differing value dates, cross-month card cycles, decimal text and native numeric conversion, credit/blank/unknown markers, malformed/oversized input, failed reconciliation, legitimate repeated purchases, overlapping exports, paired bank/card payments, partial coverage, atomic save/rollback/reversal and schema-7 backup migration/restore. International rows and other templates need their own verified fixtures before being advertised as supported.

## Verification for this review

Both original files opened successfully in the local legacy Excel reader. Strict date/type/sign checks and the bank's independent summary/balance reconciliations passed. Neither workbook was edited or converted. No automated app tests, Android build, importer UI or broker integration were run or completed for this documentation-only review.
