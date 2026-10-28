# Milestone 4 — Analytics and Forecasts

Authorized after Milestone 3. Preserve application/signing identity, vault and existing records. No provider, network, backup or release-hardening work is included.

1. Add a pure decimal mathematics engine for effective compounding, SIP/step-up, inflation, goals, retirement/withdrawals, CAGR, ACT/365 XIRR and loan prepayment. Reuse existing deposit and amortization conventions. Bound inputs/iteration and expose unavailable/ambiguous results.
2. Derive investment performance from known cash flows and dated FX, retaining opening-history limitations. Keep wealth movement separate from investment return.
3. Add a deterministic monthly forecast with asset-specific returns/contributions, cash-flow versus externally funded contribution modes, salary/expense growth, premiums, loans, deposits/maturity, withdrawals/goals and FX. Stop at unfunded cash rather than silently borrowing or inventing asset sales. Show nominal and today's purchasing power plus dated inputs.
4. Persist independent pessimistic/base/optimistic scenario revisions, per-asset/FX assumptions, planned events and goal revisions in additive encrypted schema 5. No scenario or goal is an additional asset.
5. Enable native Forecast/Tools, scenario builder/comparison, goals and analytics. Calculations run off the UI thread; hide financial outputs in obscured mode; demo remains clearly fictional and read-only.
6. Test reference and boundary fixtures, reconciliation, interrupted writes, encrypted populated migrations and native workflows. Build/lint and verify the actual 0.3.0→0.4.0 upgrade only on the isolated emulator. Pin ANDROID_SERIAL and serialize instrumentation engines.

No default rates are recommendations. Forecasts are estimates, before tax/fees unless explicitly entered. Calculator conventions and limits are shown with outputs. No automatic assumptions about inflation, guaranteed returns, grace periods or statutory product rules are introduced.
