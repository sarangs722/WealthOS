package com.wealthos.app

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.wealthos.finance.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Composable fun IncomeSourceForm(state:BankUiState,model:BankViewModel,source:IncomeSource?,done:(UUID)->Unit) {
    val stableId by rememberSaveable {mutableStateOf(source?.id?.toString() ?: UUID.randomUUID().toString())}
    var name by rememberSaveable {mutableStateOf(source?.name ?: "")};var kind by rememberSaveable {mutableStateOf((source?.kind ?: IncomeKind.SALARY).name)}
    var currency by rememberSaveable {mutableStateOf((source?.currency ?: CurrencyCode.INR).name)}
    var start by rememberSaveable {mutableStateOf(source?.startedOn?.toString() ?: LocalDate.now().toString())}
    var end by rememberSaveable {mutableStateOf(source?.endedOn?.toString() ?: "")};var expected by rememberSaveable {mutableStateOf("")}
    Form(if(source==null)"Add income source" else "Edit income source","Actual monthly income is recorded separately. Expected salary never changes bank balances.",state,"Save income source",{
        val id=UUID.fromString(stableId)
        model.household({done(id)}) {repo->repo.saveSource(IncomeSource(id,name,IncomeKind.valueOf(kind),CurrencyCode.valueOf(currency),LocalDate.parse(start),end.takeIf {it.isNotBlank()}?.let(LocalDate::parse),Instant.now(),Instant.now()),
            expected.takeIf {source==null && it.isNotBlank()}?.let {Amounts.parse(it,CurrencyCode.valueOf(currency))})}
    }) {
        Field("Income source name",name,{name=it})
        if(source==null)Field("Expected monthly net (optional)",expected,{expected=it},true,"Planning only. Received income is recorded separately.")
        FormDetails("Income details (optional)") {
            if(source==null) {
                Choice("Income kind",kind,IncomeKind.entries.map {it.name to it.label}) {kind=it}
                Choice("Income currency",currency,CurrencyCode.entries.map {it.name to it.name}) {currency=it}
                DateChoice("Known from",start,{start=it})
            } else Text("${source.kind.label} · ${source.currency}")
            Field("Source end date (optional)",end,{end=it},hint="YYYY-MM-DD. Leave blank for an ongoing source.")
        }
    }
}
@Composable fun IncomeRateForm(source:IncomeSource,state:BankUiState,model:BankViewModel,done:()->Unit) {
    var value by rememberSaveable {mutableStateOf("")};var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())};var note by rememberSaveable {mutableStateOf("")}
    Form("Expected income change",source.name,state,"Save expected rate",{model.household(done) {it.saveRate(IncomeRate(UUID.randomUUID(),source.id,LocalDate.parse(date),Amounts.parse(value,source.currency),0,Instant.now(),note))}}) {
        Field("Expected monthly net in ${source.currency}",value,{value=it},true)
        Field("Rate effective date (YYYY-MM-DD)",date,{date=it})
        Field("Rate note / correction",note,{note=it},multi=true)
        Text("Expected income may be future-dated. It is a planning input and is never counted as received salary or net worth.")
    }
}
@Composable fun IncomeRecordForm(source:IncomeSource,state:BankUiState,model:BankViewModel,done:()->Unit) {
    var month by rememberSaveable {mutableStateOf(YearMonth.now().toString())};var net by rememberSaveable {mutableStateOf("")}
    var gross by rememberSaveable {mutableStateOf("")};var deductions by rememberSaveable {mutableStateOf("")};var note by rememberSaveable {mutableStateOf("")}
    Form("Record monthly income",source.name,state,"Save received income",{model.household(done) {repo->repo.saveIncome(IncomeRecord(UUID.randomUUID(),source.id,YearMonth.parse(month),Amounts.parse(net,source.currency),
        gross.takeIf {it.isNotBlank()}?.let {Amounts.parse(it,source.currency)},deductions.takeIf {it.isNotBlank()}?.let {Amounts.parse(it,source.currency)},0,Instant.now(),note))}}) {
        Field("Received month (YYYY-MM)",month,{month=it},hint="The month cash was received. Actual future months are not allowed.")
        Field("Actual net received in ${source.currency}",net,{net=it},true,"Enter an explicit 0 for a known month with no income. Missing is not zero.")
        if(source.kind==IncomeKind.SALARY) {
            Field("Payslip gross (optional)",gross,{gross=it},true)
            Field("Payslip deductions (optional)",deductions,{deductions=it},true,"Provide both fields or neither. Gross minus deductions must equal net received.")
        }
        Field("Income note / correction",note,{note=it},multi=true)
        Text("This stores one received-income total for the source/month. A correction replaces that month's figure for calculations and retains earlier revisions. It does not create a second salary or update bank cash.")
    }
}
@Composable fun CashFlowForm(month:YearMonth,currency:CurrencyCode,state:BankUiState,model:BankViewModel,done:()->Unit) {
    val old=state.book.household.cashFlows.filter {it.month==month && it.currency==currency}.maxByOrNull {it.revision}
    var mode by rememberSaveable {mutableStateOf((old?.incomeMode ?: IncomeMode.MANUAL_TOTAL).name)}
    var income by rememberSaveable {mutableStateOf(old?.manualIncome?.toPlainString() ?: "")}
    var essential by rememberSaveable {mutableStateOf(old?.essential?.toPlainString() ?: "0")};var discretionary by rememberSaveable {mutableStateOf(old?.discretionary?.toPlainString() ?: "0")}
    var premiums by rememberSaveable {mutableStateOf(old?.premiums?.toPlainString() ?: "0")};var loanPrincipal by rememberSaveable {mutableStateOf(old?.loanPrincipal?.toPlainString() ?: "0")}
    var loanInterest by rememberSaveable {mutableStateOf(old?.loanInterest?.toPlainString() ?: "0")};var other by rememberSaveable {mutableStateOf(old?.otherOutflows?.toPlainString() ?: "0")}
    var investments by rememberSaveable {mutableStateOf(old?.investments?.toPlainString() ?: "0")}
    var incomeConfirmed by rememberSaveable {mutableStateOf(old?.incomeConfirmed ?: false)};var expensesConfirmed by rememberSaveable {mutableStateOf(old?.expensesConfirmed ?: false)}
    var note by rememberSaveable {mutableStateOf("")}
    Form("Monthly cash-flow summary","$month · $currency · ${if(month==YearMonth.now())"Current month / month-to-date" else "Recorded month"}",state,"Save monthly summary",{model.household(done) {repo->
        fun amount(text:String)=Amounts.parse(text,currency)
        repo.saveCashFlow(MonthlyCashFlow(UUID.randomUUID(),month,currency,IncomeMode.valueOf(mode),if(mode==IncomeMode.MANUAL_TOTAL.name)amount(income) else null,
            amount(essential),amount(discretionary),amount(premiums),amount(loanPrincipal),amount(loanInterest),amount(other),amount(investments),incomeConfirmed,expensesConfirmed,0,Instant.now(),note))
    }}) {
        Choice("Income input",mode,IncomeMode.entries.map {it.name to it.label}) {mode=it;incomeConfirmed=false}
        if(mode==IncomeMode.MANUAL_TOTAL.name)Field("Actual net income total",income,{income=it},true,"Includes all net income received. Source records are reconciliation references, not additions.")
        else Text("Uses the latest actual record for each source/month. Every source active in this month needs a record, including explicit zero income. Future/expected salary is excluded.")
        Toggle("All received income is included",incomeConfirmed) {incomeConfirmed=it}
        Field("Essential living expenses",essential,{essential=it},true,"Exclude separately entered premiums, loan payments and investments.")
        Field("Discretionary expenses",discretionary,{discretionary=it},true)
        Field("Insurance premiums actually paid",premiums,{premiums=it},true)
        Field("Loan principal actually paid",loanPrincipal,{loanPrincipal=it},true,"A cash obligation, not an expense or a second liability reduction.")
        Field("Loan interest actually paid",loanInterest,{loanInterest=it},true)
        Field("Other actual outflows",other,{other=it},true)
        Field("Investment contributions paid",investments,{investments=it},true,"Exclude internal transfers and market value changes.")
        Toggle("All outflow categories are included; zero means none",expensesConfirmed) {expensesConfirmed=it}
        Field("Summary note / correction",note,{note=it},multi=true)
        Text("Surplus before investments = net income − living expenses − premiums − principal/interest obligations. Remaining cash also subtracts investments. Deductions already removed from salary net pay must not be counted again. Unconfirmed inputs leave totals unavailable; saving a draft is allowed.")
        Text("Summaries never change account balances or holdings. Premium settlements and loan schedules are not automatically added to these figures.")
    }
}
@Composable fun EmergencyTargetForm(state:BankUiState,model:BankViewModel,done:()->Unit) {
    var target by rememberSaveable {mutableStateOf("")};var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())};var note by rememberSaveable {mutableStateOf("")}
    Form("Emergency fund target","Choose your own INR reserve target. This is a planning goal, not an additional asset.",state,"Save reserve target",{model.household(done) {it.saveTarget(EmergencyTarget(UUID.randomUUID(),LocalDate.parse(date),Amounts.parse(target,CurrencyCode.INR),0,Instant.now(),note))}}) {
        Field("Target amount in INR",target,{target=it},true)
        Field("Target effective date (YYYY-MM-DD)",date,{date=it})
        Field("Target note / correction",note,{note=it},multi=true)
    }
}
@Composable fun EmergencyAllocationForm(state:BankUiState,model:BankViewModel,hidden:Boolean=false,done:()->Unit) {
    val sources=EmergencyFunds.sources(state.book,LocalDate.now())
    var key by rememberSaveable {mutableStateOf("")};var amount by rememberSaveable {mutableStateOf("")};var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())};var note by rememberSaveable {mutableStateOf("")}
    val selected=sources.find {it.key==key}
    Form("Earmark existing funds","An earmark reserves part of an existing asset. Net worth does not increase and no money is transferred.",state,"Save earmark",{model.household(done) {repo->
        val source=requireNotNull(selected) {"Choose an existing asset."};val id=UUID.fromString(source.key.substringAfter(':'))
        repo.saveAllocation(EmergencyAllocation(UUID.randomUUID(),id.takeIf {key.startsWith("cash:")},id.takeIf {key.startsWith("asset:")},LocalDate.parse(date),Amounts.parse(amount,source.currency),0,Instant.now(),note))
    }}) {
        Choice("Existing reserve asset",key,listOf("" to "Choose asset")+sources.map {it.key to "${it.name} · ${it.currency}${if(it.eligible)"" else " · not liquid"}"}) {key=it;amount=""}
        selected?.let {Text("Available ${money(it.available,it.currency,hidden)} · ${it.status}. Only explicitly liquid assets may be earmarked.")}
        Field("Earmarked amount in ${selected?.currency ?: CurrencyCode.INR}",amount,{amount=it},true,"This sets the total earmark for this asset, not an increment. Enter 0 to release it.")
        Field("Earmark date (YYYY-MM-DD)",date,{date=it})
        Field("Earmark note / correction",note,{note=it},multi=true)
        Text("Progress is capped at currently available funds if values fall or cash is spent. FX and stale/estimated source values are labelled; withdrawal restrictions and redemption penalties are not inferred.")
    }
}
@Composable fun PolicyForm(state:BankUiState,model:BankViewModel,policy:InsurancePolicy?,done:(UUID)->Unit) {
    val prior=policy?.let {Insurance.view(state.book.household,it,LocalDate.now()).terms ?: Insurance.latestTerms(state.book.household,it.id).lastOrNull()}
    val id by rememberSaveable {mutableStateOf(policy?.id?.toString() ?: UUID.randomUUID().toString())}
    var name by rememberSaveable {mutableStateOf(policy?.name ?: "")};var insurer by rememberSaveable {mutableStateOf(policy?.insurer ?: "")}
    var kind by rememberSaveable {mutableStateOf((policy?.kind ?: PolicyKind.TERM).name)};var currency by rememberSaveable {mutableStateOf((policy?.currency ?: CurrencyCode.INR).name)}
    var effective by rememberSaveable {mutableStateOf(LocalDate.now().toString())};var from by rememberSaveable {mutableStateOf(prior?.validFrom?.toString() ?: LocalDate.now().toString())}
    var until by rememberSaveable {mutableStateOf(prior?.validUntil?.toString() ?: "")};var cover by rememberSaveable {mutableStateOf(prior?.cover?.toPlainString() ?: "")}
    var premium by rememberSaveable {mutableStateOf(prior?.premium?.toPlainString() ?: "")};var frequency by rememberSaveable {mutableStateOf((prior?.frequency ?: PremiumFrequency.YEARLY).name)}
    var due by rememberSaveable {mutableStateOf(prior?.anchorDue?.toString() ?: "")};var lastDue by rememberSaveable {mutableStateOf(prior?.lastPremiumDue?.toString() ?: "")}
    var scope by rememberSaveable {mutableStateOf((prior?.scope ?: CoverageScope.INDIVIDUAL).name)};var active by rememberSaveable {mutableStateOf(prior?.active ?: true)}
    var note by rememberSaveable {mutableStateOf("")}
    val native=CurrencyCode.valueOf(currency)
    Form(if(policy==null)"Add insurance policy" else "Record renewal / edit policy","Recorded terms and dates only. Neither coverage limits nor premium records become net-worth assets.",state,"Save policy terms",{
        val policyId=UUID.fromString(id)
        model.household({done(policyId)}) {repo->repo.savePolicy(InsurancePolicy(policyId,name,insurer,PolicyKind.valueOf(kind),native,Instant.now(),Instant.now()),
            InsuranceTerms(UUID.randomUUID(),policyId,LocalDate.parse(effective),LocalDate.parse(from),LocalDate.parse(until),cover.takeIf {it.isNotBlank()}?.let {Amounts.parse(it,native)},premium.takeIf {it.isNotBlank()}?.let {Amounts.parse(it,native)},
                PremiumFrequency.valueOf(frequency),due.takeIf {it.isNotBlank()}?.let(LocalDate::parse),CoverageScope.valueOf(scope),active,0,Instant.now(),note,lastDue.takeIf {it.isNotBlank()}?.let(LocalDate::parse)))}
    }) {
        Field("Policy nickname",name,{name=it},hint="No full policy number, health diagnosis or bank credentials are needed.")
        if(policy==null) {
            Choice("Insurance kind",kind,PolicyKind.entries.map {it.name to it.label}) {kind=it;scope=CoverageScope.INDIVIDUAL.name}

        } else Text("${policy.kind.label} · ${policy.currency} · Existing history is retained.")

        DateChoice("Cover starts",from,{from=it;if(policy==null)effective=it})
        Field("Recorded cover end (YYYY-MM-DD)",until,{until=it})
        Field("Cover limit in $native (optional)",cover,{cover=it},true,"Blank means unknown. Health floater limits are counted once per policy, not per family member.")
        if(kind==PolicyKind.HEALTH.name)Choice("Health cover scope",scope,CoverageScope.entries.map {it.name to it.label}) {scope=it}
        Field("Premium per installment (optional)",premium,{premium=it},true,"Blank means unknown. Enter 0 only for a genuinely zero-premium policy.")
        FormDetails("Premium schedule & other details") {
        Field("Insurer (optional)",insurer,{insurer=it})
        Choice("Premium frequency",frequency,PremiumFrequency.entries.map {it.name to it.label}) {frequency=it}
        Field("First tracked premium due (optional)",due,{due=it},hint="YYYY-MM-DD. The first known unpaid/next due; earlier unpaid cycles are not invented.")
        Field("Last premium due (optional)",lastDue,{lastDue=it},hint="YYYY-MM-DD for limited-pay policies. Otherwise the recurring schedule runs through recorded cover end.")
        Toggle("Track this policy as active",active) {active=it}
        Field("Policy terms note / correction",note,{note=it},multi=true)
        DateChoice("Terms effective",effective,{effective=it})
        if(policy==null)Choice("Policy currency",currency,CurrencyCode.entries.map {it.name to it.name}) {currency=it}
        Text("Dates are an offline reminder list, not insurer confirmation of coverage, claim payout, grace period or renewal. Premium payment alone does not extend the recorded cover period. Update renewal terms explicitly.")
        }
    }
}
@Composable fun PremiumSettlementForm(policy:InsurancePolicy,state:BankUiState,model:BankViewModel,done:()->Unit) {
    val next=Insurance.view(state.book.household,policy,LocalDate.now()).nextDue
    var due by rememberSaveable {mutableStateOf(next?.dueOn?.toString() ?: "")};var paid by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var amount by rememberSaveable {mutableStateOf("")};var note by rememberSaveable {mutableStateOf("")}
    Form("Record premium settlement",policy.name,state,"Save premium settlement",{model.household(done) {repo->repo.saveSettlement(PremiumSettlement(UUID.randomUUID(),policy.id,LocalDate.parse(due),LocalDate.parse(paid),Amounts.parse(amount,policy.currency),0,Instant.now(),note))}}) {
        Field("Premium installment due (YYYY-MM-DD)",due,{due=it})
        Field("Settlement observed on (YYYY-MM-DD)",paid,{paid=it},hint="Actual date of this observation; advance settlement of a future due is allowed.")
        Field("Total paid toward this installment",amount,{amount=it},true,"${policy.currency}. Cumulative for this due date. Enter the updated total for partial payments; 0 corrects a mistaken receipt.")
        Field("Settlement note / correction",note,{note=it},multi=true)
        Text("This tracks settlement status only and does not deduct bank cash or add monthly expenses. Record actual premiums paid in the monthly cash-flow summary. Overpayments are not automatically applied to another installment.")
    }
}
