# Statement import and investment access

Public-source research checked on 29 October 2026. Design proposal only: WealthOS 0.6.8 is unchanged. On 30 October, the user supplied bank/card exports and authorized local format inspection. Their verified layouts, validation results and implementation gates are recorded in [HDFC statement formats](HDFC_STATEMENT_FORMATS.md). No broker account, API entitlement or personal credential was accessed. No API subscription was activated. The user confirmed HDFC Bank, Tata Neu HDFC Bank Credit Card, and both Groww mutual funds and stocks/ETFs.

## Confirmed first sources

| Source | Officially documented exports | First implementation target |
| --- | --- | --- |
| HDFC Bank account | NetBanking lists PDF, Excel, Text, Delimited and MSMoney downloads. Email statements are PDF. [HDFC NetBanking FAQ](https://www.hdfc.bank.in/need-help/net-banking-faqs). | The supplied export is verified binary XLS with separate withdrawal/deposit columns. Prioritize this real layout; native delimited remains useful, with its delimiter/header layout still to verify. |
| Tata Neu HDFC Bank Credit Card | HDFC's current mobile-banking FAQ lists card statement downloads in PDF, XLS and CSV. [HDFC Bank App FAQ](https://www.hdfc.bank.in/need-help/mobile-banking-faqs). | The supplied Tata Neu export is verified binary XLS with text amounts, a credit marker and a distinct transaction table. Do not assume a monthly email PDF or CSV has identical columns, or that every HDFC card exposes the same format. |
| Groww mutual funds and stocks/ETFs | Both categories are in scope. The broker/API and consolidated-statement evidence below remains applicable. | Start from separate holdings exports or a suitable consolidated statement. A stock API connector does not satisfy the mutual-fund requirement by itself. |

The supplied bank/card examples are genuine binary `.xls` files. Their headers and amount/date conventions are now verified for these two layouts; Android reader compatibility and the runtime importer remain unimplemented. Use independently fictional fixtures with the verified structure. Account/customer/card identifiers, PAN, names, addresses and genuine transaction narratives/amounts must not enter source control. Generic mapping remains useful for other formats; verified recognition of these samples is not evidence of support for every HDFC export.

Tata Neu review requirements include purchases versus repayments/refunds, fees and taxes, EMI-related adjustments, statement-cycle coverage and reward sections. Reward-point/NeuCoin tables are not automatically bank cash or income. Available credit and minimum amount due must not become assets or stand in for total outstanding liability. If the downloaded statement covers a billing cycle, month selection must use actual covered transaction/posting dates and identify incomplete calendar-month coverage.

## Recommended direction

1. Add an on-device monthly statement-import wizard, starting from native CSV/delimited exports and verified Excel adapters, with saved column mappings, reviewable categorization and duplicate detection. Keep the ordinary interface focused on monthly totals. Inspect actual Excel containers before selecting `.xls`/`.xlsx` support; do not require users to rename a file extension.
2. Add holdings-statement import into the existing current-value/snapshot flow, then validated consolidated account statement (CAS) adapters. Match existing holdings before adding anything.
3. Investigate an optional Groww stock-holdings connector if the user's account actually has the documented zero-cost entitlement. Keep file import available regardless of API eligibility or outages. Do not describe it as a verified permanent free connection yet.

The importer itself needs no paid API or cloud backend. This is separate from any institution's own statement/access charges. Live account aggregation is a different integration with eligibility, cost, consent and security requirements.

## Verified access options

| Route | Evidence and practical limits | WealthOS decision |
| --- | --- | --- |
| Groww stock holdings API | The documented `GET /v1/holdings/user` returns demat stock holdings, including ISIN, quantity and average price. The displayed response does not supply a current market quote. [Official portfolio docs](https://groww.in/trade-api/docs/curl/portfolio). | Potential optional quantity/holding refresh. An average acquisition price is not a current valuation or complete FIFO history. |
| Groww cost | Groww's 30 September 2026 guide describes a zero-cost **Free Trial** that includes holdings/positions but excludes live-data/backtesting APIs. Its main API landing page separately advertises ₹499 plus taxes/month. Trial duration, continued zero-cost eligibility and account-specific entitlement are not established by the reviewed public pages. [Official guide](https://groww.in/blog/api-trading-on-groww), [pricing page](https://groww.in/trade-api). | Worth a read-only eligibility check later, with user authorization. Do not purchase/activate a paid plan or assume a lasting free tier. |
| Groww authentication | The docs describe a direct token expiring daily at 6 AM and an API-key/secret flow requiring daily approval. [Official authentication docs](https://groww.in/trade-api/docs/curl). | This is not yet a demonstrated low-maintenance automatic sync. No password/PIN/TOTP-seed scraping. App behavior can be limited to holdings reads; broker-enforced read-only credential scopes were not verified. |
| Groww mutual funds | No personal mutual-fund holdings endpoint was found in the reviewed public Trading API pages. The stock endpoint is not proof of mutual-fund support, even when units are dematerialized. | Use statements first. Do not use Groww AMC's public scheme-portfolio disclosures as if they described this user's investments. |
| Groww statement exports | Groww places holdings statements and investment/tax reports under Profile → Reports & statements. Exact downloaded columns and format are not verified without an example. [Official report guide](https://groww.in/blog/post-trade-analysis-on-groww). | Add adapters after inspecting a sanitized format. An order list includes orders that may not be executed trades; tax reports are not necessarily a complete holdings ledger. |
| Mutual-fund CAS | AMFI directs investors to CAMS/KFintech mail-back statements and MFCentral portfolio/CAS downloads. KFintech's official form offers summary/detailed PDF statements delivered to the registered email. [AMFI](https://www.amfiindia.com/online-center/download-cas), [KFintech](https://mfs.kfintech.com/investor/General/ConsolidatedAccountStatement). | Useful consolidated-file import, with a separately verified local PDF parser. Reconcile statement date, holder/folio coverage, demat versus non-demat holdings, and missing history. Public NAV data alone cannot identify a user's holdings. |
| Depository CAS | CAS can consolidate demat securities and mutual-fund holdings. It is a dated statement, not a universal live API. [SEBI investor guide](https://investor.sebi.gov.in/consolidated_account_statement.html), [CDSL FAQ](https://www.cdslindia.com/CAS/FAQ.html). | Valuable for investments across brokers. Import only covered rows and dates, not an assumed complete lifetime portfolio. Avoid promising every institution's statement service is invariably free. |
| Account Aggregator (AA) | The Ministry of Finance describes FIUs as entities registered with and regulated by a financial-sector regulator. Sahamati explicitly says an unregulated fintech cannot itself become an FIU/FIP and describes commercial onboarding arrangements. [Government overview](https://financialservices.gov.in/account-aggregator-framework), [Sahamati FAQ](https://sahamati.org.in/faq/). | A regulated-partner route could be explored, but this is not a freely available universal personal API key for the present standalone app. Production cost/eligibility/consent/backend implications must be checked first. This conclusion concerns the reviewed integration route, not all possible customer-data export rights. |
| MFCentral partner API | No self-service, generally available free developer access was verified. One concrete integration, MProfit, documents a distributor partner and its own commercial policies. [Provider's terms](https://mprofit.in/mfcentral-cas-api-terms/). | This proves a partner path exists, not that WealthOS has access or that every MFCentral integration has the same commercial terms. Keep manual CAS import independent. |

## Proposed monthly statement experience

**Cash flow / Expenses → Import statement → Choose file → Review → Save month.**

- Recognize a verified issuer template where possible; otherwise show a short mapping screen: date, description, debit/credit or signed amount, optional reference and balance. Remember the mapping for that issuer/account.
- Ask for the source account/card and the month. Show detected coverage and whether the file is a billing cycle, calendar month or partial export. Do not assume a card billing cycle equals a calendar month. Make transaction/posting-date choice explicit and consistent.
- Show a compact summary and an **Items to review** count. Remember user-confirmed merchant rules locally; ambiguous rows remain reviewable. Salary, transfers, investment flows and refunds are not automatically ordinary spending/income merely because of debit/credit direction.
- Import multiple files before confirming completeness. Same file reimport must not add totals again. Overlapping exports need row-level matching; equal date/merchant/amount alone must not silently delete legitimate repeated purchases.
- Let the user approve the categorized totals and compare them with an existing manual summary. An import must not add itself on top of an already entered total. Save an explicit new revision/contribution with provenance and correction history.
- Leave incomplete imports as drafts. Unreadable dates, unknown currencies, ambiguous signs, unresolved loan components, missing coverage and rejected rows cannot silently become zero or confirmed complete totals.

### Spending versus cash movement

Keep these meanings separate in the model and presentation. Card purchases can be January spending while the bank repayment happens in February. Counting both as expenses doubles spending; excluding the repayment from actual bank cash movement also misstates cash flow.

| Example | Spending view | Cash-movement view |
| --- | --- | --- |
| Bank-paid groceries | Expense once | Bank cash outflow |
| Credit-card groceries | Expense at the chosen transaction/posting date | Increase card obligation; bank cash is unchanged |
| Bank payment of the card bill | Settlement, not a second purchase expense | Bank cash outflow and card settlement |
| Transfer between own bank accounts | Excluded from spending | Two linked legs; no consolidated wealth/income creation |
| Merchant refund | Reduction of the appropriate expense, with its actual refund date retained | Actual credit to bank/card as applicable |
| Investment purchase | Investment allocation, not living expense | Funding flow only once; no inferred investment units or duplicated bank adjustment |
| Loan EMI | Interest is expense; principal is separate | Actual payment outflow; components must be known or flagged for review |

The existing `MonthlyCashFlow` model stores confirmed monthly actual outflows, investments and obligations and computes surplus/remaining cash. Do not silently change those definitions by inserting mixed bank/card spending totals. Before release, introduce explicit spending/settlement aggregates and a defined bridge into monthly cash-flow totals; preserve existing summaries as the legacy/manual basis. Card outstanding debt also needs an explicit observed revolving-liability representation if imported into net worth, rather than pretending it is a fixed-amortization loan or inferring zero from an incomplete file.

First implementation can import and review rows and produce a draft expense summary. It must not claim complete cash surplus, change bank observations, pay a bill, add investment transactions, or replace liabilities implicitly. Those applications need separate review and correct linked records.

## Persistence and accounting requirements

This is a proposal, not a schema already shipped. Use an explicit migration for encrypted import batches, source/account aliases, normalized rows, row identities, review decisions, saved mappings/rules and per-month applications/revisions. Extend typed portable backups and restore validation for the new tables before shipping persistent import. Older schema-7 backups need explicit migration defaults.

Retain source hashes, parser/mapping version, source/row reference, currency, effective date and import timestamp. Provider transaction IDs, when reliable, are preferred over inferred duplicate keys. Cross-file candidate duplicates and unmatched transfers need a review path. Preserve legitimate identical rows and row multiplicity. Import application must be atomic, idempotent and reversible through audited revisions; cancellation or failure must leave existing financial rows unchanged.

For holdings, match account/source plus ISIN and plan/option/folio context; present uncertain matches instead of merging by a similar name. Update the existing holding with a dated snapshot. Holdings-only files must not invent old purchases, cost lots, XIRR or returns. A detailed-history mode needs a deliberate cutover from opening/snapshot positions and coverage checks before adding transactions. Groww exports, CAS and API rows may describe the same positions; they are not three assets.

## Parser, privacy and verification boundaries

- Process selected documents locally through Android's file picker; no statement-upload backend, account scraping or analytics. Keep normalized necessary data in SQLCipher. Do not retain raw statements by default or log financial rows/PAN/account identifiers. A local statement-password prompt, if later needed for PDFs, is transient and is not an online account password.
- Prioritize verified legacy `.xls` support alongside native CSV/delimited parsing because both supplied HDFC downloads use binary Excel. XLSX, HTML disguised as Excel, macro-enabled/encrypted workbooks and scanned PDFs need explicit support decisions, not silent best-effort parsing. Do not execute formulas, macros, external links or embedded content.
- Parse monetary text directly to `BigDecimal` and persistent decimal text, with explicit currency precision. Native numeric Excel cells need a verified canonical decimal conversion at the reader boundary, followed by strict paise/magnitude validation; the source encoding itself is binary. Never perform accounting arithmetic in floating point or round invalid sub-paise values into acceptance. See the format review for this previously unverified bank-cell distinction. Validate date formats, Excel date systems, debit/credit conventions and totals against previews.
- Bound bytes, rows, cells, ZIP expansion and XML/PDF resource use; disable external entities/network access. No XLSX/PDF dependency has been adopted or claimed verified in this research. Native Android compatibility, license and security review are implementation gates.
- For any future Groww connector, use official documented authentication and a strict holdings-read endpoint allowlist; do not add order operations. Distinguish application read-only behavior from the token's actual capabilities. Pricing/entitlement, token expiry, quote availability, secure token handling and offline failure behavior must be verified with an authorized account before calling it working.

Required deterministic fixtures include overlapping reimports, identical legitimate payments, partial months, billing cycles spanning months, debit/credit reversals, cross-month refunds, paired bank/card settlements, internal transfers, investments and EMIs, mixed currencies, invalid/ambiguous dates, precision limits, malformed/oversized files, parser cancellation, atomic rollback, revision reversal, existing-summary reconciliation, missing holdings history, snapshot-to-ledger cutover, backup round trips and migration/restoration. User phone records must not be used as disposable fixtures.

## Implementation sequence

1. With the two supplied HDFC XLS layouts verified, evaluate a bounded Android reader and build independently fictional fixtures, then the preview/review flow and CSV/delimited support. Both Groww investment categories are confirmed but their export layouts remain to be inspected. Prefer native exports to unnecessary PDF conversion.
2. Add encrypted provenance, duplicate handling and draft monthly spending applications, with explicit cash-flow semantics and complete backup/migration tests.
3. Add holdings export adapters and verified local CAS parsing; retain manual-value entry and incomplete-history labels.
4. If the user's Groww account confirms no-cost holdings access, prototype the documented stock endpoint without paid quotes or trading operations. Do not make API access essential to offline tracking.

No runtime code or app version was changed for this exploration. Existing signed 0.6.8 and its recorded tests/build remain the current implementation, not evidence that imports or a broker connection have already been built.
