package com.wealthos.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wealthos.finance.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Composable fun CashFlowScreen(state:BankUiState,hidden:Boolean,onEdit:(YearMonth,CurrencyCode)->Unit,onIncome:()->Unit) {
    var monthText by rememberSaveable {mutableStateOf(YearMonth.now().toString())};var currencyName by rememberSaveable {mutableStateOf(CurrencyCode.INR.name)}
    var error by remember {mutableStateOf(false)}
    val month=runCatching {YearMonth.parse(monthText)}.getOrNull()?.takeIf {it.year>=1900 && it<=YearMonth.now()}
    val currency=CurrencyCode.valueOf(currencyName);val h=state.book.household
    val result=month?.let {CashFlows.at(h,it,currency)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text("Monthly cash flow",style=MaterialTheme.typography.headlineMedium);Text("Summary-level tracking. Income, expenses and investments never change bank balances automatically.")}
        item {Field("Review month (YYYY-MM)",monthText,{monthText=it;error=false})}
        item {Choice("Review currency",currencyName,CurrencyCode.entries.map {it.name to it.name}) {currencyName=it}}
        if(error)item {Text("Enter a month from 1900 through the current month.",color=MaterialTheme.colorScheme.error)}
        if(!state.demo)item {Button(onClick={if(month!=null)onEdit(month,currency) else error=true},modifier=Modifier.fillMaxWidth()) {Text(if(result?.summary==null)"Add monthly summary" else "Revise monthly summary")}}
        item {OutlinedButton(onClick=onIncome,modifier=Modifier.fillMaxWidth()) {Text("Income sources & salary")}}
        if(result?.summary==null)item {SectionCard("No monthly summary") {Text("Enter actual net income and monthly outflow totals. Expected salary and policy budgets are not substituted for actual figures.")}}
        else item {SectionCard("$month · $currency") {
            val s=requireNotNull(result.summary)
            Text(if(month==YearMonth.now())"Current month / month-to-date" else "Recorded month")
            Text("Net income ${money(result.income,currency,hidden)}")
            Text("Living / other expenses ${money(result.livingExpenses,currency,hidden)}")
            Text("Premiums + principal/interest obligations ${money(result.obligations,currency,hidden)}")
            Text("Surplus before investments ${money(result.surplusBeforeInvestments,currency,hidden)}",style=MaterialTheme.typography.titleLarge)
            Text("Investment contributions ${money(s.investments,currency,hidden)}")
            Text("Remaining after investments ${money(result.remainingCash,currency,hidden)}",style=MaterialTheme.typography.titleMedium)
            if(!s.incomeConfirmed || !s.expensesConfirmed)Text("Incomplete: income or outflows have not been confirmed.",color=MaterialTheme.colorScheme.error)
            if(s.incomeMode==IncomeMode.RECORDED_SOURCES && result.missingSources.isNotEmpty())Text("Missing received-income records: ${result.missingSources.joinToString {it.name}}. Add explicit zero records for known zero-income months.")
            Text("Actual source records ${money(result.recordedIncome,currency,hidden)} · ${s.incomeMode.label}")
            if(s.incomeMode==IncomeMode.MANUAL_TOTAL)Text("Manual total minus source records ${money(s.manualIncome!!-result.recordedIncome,currency,hidden)}. Source records are a reconciliation reference, never an extra addition.")
            Text("Revision ${s.revision} · Entered ${recordedLabel(s.recordedAt)}",style=MaterialTheme.typography.bodyMedium)
            if(s.note.isNotBlank())Text(if(hidden)"Note hidden" else s.note)
        }}
        val history=h.cashFlows.filter {month!=null && it.month==month && it.currency==currency}.sortedByDescending {it.revision}
        if(history.size>1)item {Text("Summary revision history",style=MaterialTheme.typography.titleLarge)}
        items(history.drop(1),key={it.id}) {s->SectionCard("Revision ${s.revision} · retained") {
            Text("Income mode ${s.incomeMode.label} · Manual net ${money(s.manualIncome,s.currency,hidden)}")
            Text("Essential ${money(s.essential,s.currency,hidden)} · Discretionary ${money(s.discretionary,s.currency,hidden)}")
            Text("Premiums ${money(s.premiums,s.currency,hidden)} · Loan principal ${money(s.loanPrincipal,s.currency,hidden)} · Interest ${money(s.loanInterest,s.currency,hidden)}")
            Text("Other ${money(s.otherOutflows,s.currency,hidden)} · Investments ${money(s.investments,s.currency,hidden)}")
            Text(if(hidden)"Note hidden" else s.note)
        }}
        val months=h.cashFlows.groupBy {it.month to it.currency}.values.map {it.maxBy {r->r.revision}}.sortedByDescending {it.month}
        if(months.isNotEmpty())item {Text("Recorded months",style=MaterialTheme.typography.titleLarge)}
        items(months,key={it.id}) {s->OutlinedButton(onClick={monthText=s.month.toString();currencyName=s.currency.name},modifier=Modifier.fillMaxWidth()) {Text("${s.month} · ${s.currency} · Revision ${s.revision}")}}
    }
}

@Composable fun IncomeSourcesScreen(state:BankUiState,hidden:Boolean,onAdd:()->Unit,onSource:(UUID)->Unit) {
    val h=state.book.household;val month=YearMonth.now()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text("Income & salary",style=MaterialTheme.typography.headlineMedium);Text("Expected rates are planning inputs. Record actual net receipts by month; neither changes account cash.")}
        if(!state.demo)item {Button(onClick=onAdd,modifier=Modifier.fillMaxWidth()) {Text("Add income source")}}
        if(h.sources.isEmpty())item {Text("No income sources yet. Add salary, rent, freelance work or another source.")}
        items(h.sources,key={it.id}) {source->
            val actual=CashFlows.income(h,month,source.currency).find {it.sourceId==source.id}
            val expected=CashFlows.rate(h,source.id,LocalDate.now())
            Card(onClick={onSource(source.id)},modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(source.name,style=MaterialTheme.typography.titleMedium)
                    Text("${source.kind.label} · ${source.currency} · ${if(source.active(month))"Active this month" else "Outside active dates"}")
                    Text("$month received ${money(actual?.net,source.currency,hidden)}")
                    Text("Last expected monthly net ${money(expected?.monthlyNet,source.currency,hidden)}",style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
@Composable fun IncomeSourceDetails(source:IncomeSource,state:BankUiState,hidden:Boolean,onAction:(String)->Unit) {
    val h=state.book.household
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text(source.name,style=MaterialTheme.typography.headlineMedium);Text("${source.kind.label} · ${source.currency} · Known from ${dateLabel(source.startedOn)}${source.endedOn?.let {" · Ended ${dateLabel(it)}"} ?: ""}")}
        if(!state.demo)item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={onAction("income-record")},modifier=Modifier.fillMaxWidth()) {Text("Record received income")}
            OutlinedButton(onClick={onAction("income-rate")},modifier=Modifier.fillMaxWidth()) {Text("Expected income change")}
            TextButton(onClick={onAction("edit-income-source")}) {Text("Edit source details")}
        }}
        item {Text("Received income history",style=MaterialTheme.typography.titleLarge)}
        val records=h.income.filter {it.sourceId==source.id}.sortedWith(compareByDescending<IncomeRecord> {it.month}.thenByDescending {it.revision})
        if(records.isEmpty())item {Text("No actual monthly records. An expected salary is not a received payment.")}
        items(records,key={it.id}) {r->SectionCard("${r.month} · Revision ${r.revision}") {
            Text("Net received ${money(r.net,source.currency,hidden)}",style=MaterialTheme.typography.titleLarge)
            if(r.gross!=null)Text("Gross ${money(r.gross,source.currency,hidden)} · Deductions ${money(r.deductions,source.currency,hidden)}")
            if(r.note.isNotBlank())Text(if(hidden)"Note hidden" else r.note)
            Text("Entered ${recordedLabel(r.recordedAt)}",style=MaterialTheme.typography.bodyMedium)
        }}
        item {Text("Expected rate history · planning only",style=MaterialTheme.typography.titleLarge)}
        items(h.rates.filter {it.sourceId==source.id}.sortedWith(compareByDescending<IncomeRate>{it.effectiveFrom}.thenByDescending {it.revision}),key={it.id}) {r->SectionCard("${dateLabel(r.effectiveFrom)} · Revision ${r.revision}") {
            Text("Monthly net ${money(r.monthlyNet,source.currency,hidden)}")
            Text(if(hidden)"Note hidden" else r.note)
            Text("Entered ${recordedLabel(r.recordedAt)}",style=MaterialTheme.typography.bodyMedium)
        }}
    }
}

@Composable internal fun EmergencyOverview(book:BankBook,hidden:Boolean) {
    val reserve=EmergencyFunds.at(book,LocalDate.now());val target=reserve.target
    SectionCard("Emergency fund"){
        if(target==null && reserve.lines.none{it.allocation.nativeAmount.signum()>0})Text("Build your safety net",style=MaterialTheme.typography.headlineSmall)
        else AmountDisplay(money(reserve.allocatedInr,hidden=hidden),style=MaterialTheme.typography.headlineLarge)
        Text(target?.let{"Target ${money(it.targetInr,hidden=hidden)}"} ?: "No target set",color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(target!=null && !hidden)reserve.percentage?.let{LinearProgressIndicator(progress={it.movePointLeft(2).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())}
        if(reserve.allocatedInr==null){StatusPill("Incomplete data",true);Text("Known subtotal ${money(reserve.knownInr,hidden=hidden)}")}
        if(reserve.lines.any{it.warning!=null})StatusPill("Some allocations need review",true)
        reserve.monthsCovered?.let{Text(if(hidden)"Coverage hidden" else "${it.setScale(1,RoundingMode.HALF_EVEN).toPlainString()} months of recorded outflows",style=MaterialTheme.typography.bodyMedium)}
        DetailsDisclosure("How this fund is counted"){
            Text("These are earmarks of assets you already own, not extra wealth. Progress is capped at the available value.")
            Text("Shortfall ${money(reserve.shortfall,hidden=hidden)}")
            Text("Coverage uses the outflows recorded for ${reserve.referenceMonth ?: "your chosen month"}; a current-month summary may be incomplete.")
        }
    }
}
@Composable fun ProtectScreen(state:BankUiState,hidden:Boolean,onTarget:()->Unit,onAllocate:()->Unit,onAddPolicy:()->Unit,onPolicy:(UUID)->Unit) {
    val h=state.book.household;val today=LocalDate.now();val reserve=EmergencyFunds.at(state.book,today)
    val views=h.policies.map{Insurance.view(h,it,today)}
    var sources by rememberSaveable{mutableStateOf(false)};var history by rememberSaveable{mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize().testTag("protect-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item {EmergencyOverview(state.book,hidden)}
        if(!state.demo)item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            OutlinedButton(onTarget,Modifier.weight(1f)){Text(if(reserve.target==null)"Set target" else "Edit target")}
            Button(onAllocate,Modifier.weight(1f)){Text("Choose funds")}
        }}
        if(reserve.lines.any{it.allocation.nativeAmount.signum()>0})item{DisclosureHeader("Selected funds",sources,{sources=!sources},"${reserve.lines.count{it.allocation.nativeAmount.signum()>0}} allocations")}
        if(sources)items(reserve.lines.filter{it.allocation.nativeAmount.signum()>0},key={"reserve:${it.allocation.id}"}){line->SectionCard(line.source.name){
            Text("Requested ${money(line.allocation.nativeAmount,line.source.currency,hidden)} · Available ${money(line.source.available,line.source.currency,hidden)}")
            Text("Counted ${money(line.eligibleNative,line.source.currency,hidden)} · ${line.source.status}")
            line.warning?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }}
        item {SectionHeading("Insurance",if(!state.demo && views.isNotEmpty())"Add policy" else null,onAddPolicy)}
        if(views.isEmpty())item{SectionCard("Protect what matters"){
            Text("Keep your term and health policies in one place.")
            if(!state.demo)Button(onAddPolicy){Text("Add insurance policy")}
        }}
        items(views,key={it.policy.id}){view->Card(onClick={onPolicy(view.policy.id)},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Column(Modifier.weight(1f)){Text(view.policy.name,style=MaterialTheme.typography.titleMedium);Text(view.policy.kind.label,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Column(Modifier.weight(1f),horizontalAlignment=androidx.compose.ui.Alignment.End){Text(money(view.terms?.cover,view.policy.currency,hidden),style=MaterialTheme.typography.titleMedium);Text("Cover",style=MaterialTheme.typography.labelMedium)}
                }
                Text(view.status,style=MaterialTheme.typography.bodyMedium)
                view.nextDue?.let{Text("Premium due ${dateLabel(it.dueOn)} · ${money(it.outstanding,view.policy.currency,hidden)}",style=MaterialTheme.typography.bodyMedium,color=if(it.dueOn<=today.plusDays(30))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }}
        if(h.allocations.isNotEmpty())item{DisclosureHeader("Fund history",history,{history=!history})}
        if(history)items(h.allocations.sortedWith(compareByDescending<EmergencyAllocation>{it.date}.thenByDescending{it.revision}),key={"earmark-history:${it.id}"}){allocation->
            val source=EmergencyFunds.sources(state.book,today).find{it.key==allocation.sourceKey}
            SectionCard("${source?.name ?: "Existing asset"} · ${dateLabel(allocation.date)} · Revision ${allocation.revision}"){
                Text(money(allocation.nativeAmount,source?.currency ?: CurrencyCode.INR,hidden)+(if(allocation.nativeAmount.signum()==0)" · released" else " · earmarked"))
                if(allocation.note.isNotBlank())Text(if(hidden)"Note hidden" else allocation.note)
            }
        }
        item{DetailsDisclosure("About recorded cover"){
            Text("Insurance limits are not assets. Family floater limits are shared; overlapping policies are not assumed additive.")
            Text("Recorded dates do not confirm claim eligibility, grace periods, insurer acceptance or automatic renewal.")
        }}
    }
}
@Composable fun PolicyDetails(policy:InsurancePolicy,state:BankUiState,hidden:Boolean,onEdit:()->Unit,onSettlement:()->Unit) {
    val h=state.book.household;val today=LocalDate.now();val view=Insurance.view(h,policy,today)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text(policy.name,style=MaterialTheme.typography.headlineMedium);Text("${policy.kind.label} · ${policy.insurer} · ${policy.currency}")}
        item {SectionCard("Recorded protection") {
            Text(view.status,style=MaterialTheme.typography.titleMedium)
            Text("Cover limit ${money(view.terms?.cover,policy.currency,hidden)}")
            view.terms?.let {Text("${it.scope.label} · ${dateLabel(it.validFrom)} – ${dateLabel(it.validUntil)}")}
            Text("Monthly recurring-budget equivalent ${money(view.monthlyBudget,policy.currency,hidden)}")
            Text("Budget equivalent spreads the recorded recurring installment over its frequency. It is not actual monthly spending. One-off premiums have no recurring monthly budget.",style=MaterialTheme.typography.bodyMedium)
        }}
        if(!state.demo)item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick=onSettlement,modifier=Modifier.fillMaxWidth()) {Text("Record premium settlement")}
            OutlinedButton(onClick=onEdit,modifier=Modifier.fillMaxWidth()) {Text("Record renewal / edit terms")}
        }}
        item {Text("Tracked installments needing confirmation",style=MaterialTheme.typography.titleLarge)}
        val dues=Insurance.dues(h,policy.id,today,today.plusYears(1)).take(18)
        if(dues.isEmpty())item {Text("No unpaid tracked installments in the next year. This does not confirm continued coverage or a renewal.")}
        items(dues,key={"${it.terms.id}:${it.dueOn}"}) {due->SectionCard(dateLabel(due.dueOn)) {
            Text("Recorded premium ${money(due.terms.premium,policy.currency,hidden)} · Cumulative settlement ${money(due.paid,policy.currency,hidden)}")
            Text("Outstanding ${money(due.outstanding,policy.currency,hidden)}${if(due.dueOn<today)" · past due date" else ""}")
        }}
        item {Text("Policy terms history",style=MaterialTheme.typography.titleLarge)}
        items(h.terms.filter {it.policyId==policy.id}.sortedWith(compareByDescending<InsuranceTerms>{it.effectiveFrom}.thenByDescending {it.revision}),key={it.id}) {t->SectionCard("Effective ${dateLabel(t.effectiveFrom)} · Revision ${t.revision}") {
            Text("${if(t.active)"Tracked active" else "Marked inactive"} · ${dateLabel(t.validFrom)} – ${dateLabel(t.validUntil)}")
            Text("Limit ${money(t.cover,policy.currency,hidden)} · ${t.scope.label}")
            Text("Installment ${money(t.premium,policy.currency,hidden)} · ${t.frequency.label}")
            Text("First tracked due ${t.anchorDue?.let(::dateLabel) ?: "Unknown"} · Last due ${t.lastPremiumDue?.let(::dateLabel) ?: "Through recorded cover end"}")
            if(t.note.isNotBlank())Text(if(hidden)"Note hidden" else t.note)
            Text("Entered ${recordedLabel(t.recordedAt)}",style=MaterialTheme.typography.bodyMedium)
        }}
        item {Text("Cumulative settlement observations",style=MaterialTheme.typography.titleLarge)}
        items(h.settlements.filter {it.policyId==policy.id}.sortedWith(compareByDescending<PremiumSettlement>{it.dueOn}.thenByDescending {it.revision}),key={it.id}) {s->SectionCard("Due ${dateLabel(s.dueOn)} · Revision ${s.revision}") {
            Text("Total paid ${money(s.amount,policy.currency,hidden)} as of ${dateLabel(s.paidOn)}")
            if(s.note.isNotBlank())Text(if(hidden)"Note hidden" else s.note)
            Text("Entered ${recordedLabel(s.recordedAt)}",style=MaterialTheme.typography.bodyMedium)
        }}
    }
}
