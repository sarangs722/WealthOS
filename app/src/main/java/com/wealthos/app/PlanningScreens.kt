package com.wealthos.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wealthos.finance.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class Calculated<T>(val value:T?=null,val error:String?=null,val loading:Boolean=true)
@Composable internal fun <T> calculateAway(vararg keys:Any?,block:()->T):Calculated<T> {
    val state=produceState<Calculated<T>>(Calculated(),*keys) {
        value=Calculated()
        value=try {withContext(Dispatchers.Default){Calculated(value=block(),loading=false)}}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){Calculated(error=if(e is IllegalArgumentException)e.message ?: "Invalid calculation inputs" else "Calculation unavailable; inspect inputs.",loading=false)}
    }
    return state.value
}
internal fun pct(value:BigDecimal?,hidden:Boolean=false)=if(hidden)"••••" else value?.movePointRight(2)?.setScale(3,RoundingMode.HALF_EVEN)?.toPlainString()?.plus("%") ?: "Unavailable"
@Composable private fun CalculationState(loading:Boolean,error:String?) {if(loading)LinearProgressIndicator(Modifier.fillMaxWidth());error?.let {Text(it,color=MaterialTheme.colorScheme.error)}}

@Composable fun ForecastScreen(state:BankUiState,hidden:Boolean,onAdd:()->Unit,onScenario:(UUID)->Unit,onGoals:()->Unit,onAnalytics:()->Unit) {
    val scenarios=state.book.planning.latestScenarios();var horizon by rememberSaveable{mutableStateOf("120")}
    val result=calculateAway(state.book,horizon){scenarios.associate{it.key to Forecasts.calculate(state.book,it.copy(months=horizon.toInt()))}}
    LazyColumn(Modifier.fillMaxSize().testTag("forecast-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text("Plan your future",Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium);StatusPill("Estimates")}}
        item {NavigationRow("Financial goals","Set a target and monthly saving",WealthIcons.forecast,onGoals)}
        item {SectionHeading("Your scenarios",if(!state.demo && scenarios.isNotEmpty())"New scenario" else null,onAdd)}
        if(scenarios.isEmpty())item{SectionCard("Where could your wealth go?"){
            Text("Start with your assets. Choose your contributions and assumptions.")
            if(!state.demo)Button(onAdd){Text("Create a scenario")}
        }}else{
            item {Choice("Compare horizon",horizon,listOf(60,120,180,240,360).map{it.toString() to "${it/12} years"}){horizon=it};CalculationState(result.loading,result.error)}
            items(scenarios,key={it.key}){scenario->Card(onClick={onScenario(scenario.key)},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row{Text(scenario.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Text(scenario.kind.label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)}
                    Text("${horizon.toInt()/12} years · starts ${dateLabel(scenario.asOf)}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    val r=result.value?.get(scenario.key)
                    if(r!=null){Text(if(r.complete)money(r.points.lastOrNull()?.nominal,hidden=hidden) else "Projection incomplete",style=MaterialTheme.typography.headlineSmall)
                        if(r.complete)Text("Today's value ${money(r.points.last().real,hidden=hidden)}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        r.unavailable?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                        r.gap?.let{Text("Unfunded from month ${it.month}",color=MaterialTheme.colorScheme.error)}
                    }
                }
            }}
        }
        item{DetailsDisclosure("How forecasts work"){
            Text("These are estimates, not guaranteed returns. Each scenario saves its own assumptions and starting date.")
            Text("Comparison uses the selected common horizon. Opening a scenario shows its saved horizon, inputs and monthly results.")
            Text("Forecasts never change balances or holdings. Unfunded cash stops the projection; borrowing or asset sales are not invented.")
        }}
    }
}
@Composable internal fun ForecastChart(points:List<ForecastPoint>,hidden:Boolean) {
    if(hidden){Text("Forecast chart hidden");return}
    val values=points.flatMap {listOf(it.nominal,it.real)}
    val min=(values.minOrNull() ?: BigDecimal.ZERO).min(BigDecimal.ZERO);val max=(values.maxOrNull() ?: BigDecimal.ONE).max(BigDecimal.ONE);val span=max-min
    val palette=forecastChartPalette()
    Text("Nominal: solid line · Today's purchasing power: dashed line",style=MaterialTheme.typography.bodyMedium)
    Canvas(Modifier.fillMaxWidth().height(180.dp).testTag("forecast-chart").semanticsLabel("Estimated nominal wealth (solid line) and today's purchasing power (dashed line) across ${(points.size-1).coerceAtLeast(0)} monthly steps. Exact dates and values follow below.")) {
        val inset=3.dp.toPx()
        fun draw(real:Boolean,color:Color){
            val path=Path()
            val denominator=(points.size-1).coerceAtLeast(1)
            points.forEachIndexed {index,point->
                val x=inset+(size.width-2*inset).coerceAtLeast(0f)*index/denominator
                val value=if(real)point.real else point.nominal
                val y=inset+(size.height-2*inset).coerceAtLeast(0f)*(1f-(value-min).divide(span,DecimalMath.context).toFloat())
                if(index==0)path.moveTo(x,y) else path.lineTo(x,y)
            }
            // One continuous path preserves dash spacing across short monthly segments.
            drawPath(path,color,style=Stroke(width=3.dp.toPx(),pathEffect=if(real)PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(),6.dp.toPx())) else null))
        }
        draw(false,palette.nominal);draw(true,palette.real)
    }
}
@Composable fun ScenarioDetails(s:ForecastScenario,state:BankUiState,hidden:Boolean,onEdit:()->Unit) {
    val result=calculateAway(state.book,s) {Forecasts.calculate(state.book,s)}
    var monthly by rememberSaveable {mutableStateOf(false)}
    var steps by rememberSaveable {mutableStateOf(false)}
    var inputs by rememberSaveable {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize().testTag("scenario-detail"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text(s.name,style=MaterialTheme.typography.headlineMedium);Text("${s.kind.label} · ${s.mode.label} · ${s.months/12} years · Revision ${s.revision}");Text("ESTIMATE · Not guaranteed",color=MaterialTheme.colorScheme.primary)}
        if(!state.demo)item {Button(onClick=onEdit,modifier=Modifier.fillMaxWidth()){Text("Revise scenario")}}
        item {CalculationState(result.loading,result.error)}
        val r=result.value
        if(r!=null){
            r.unavailable?.let {reason->item {SectionCard("Starting data incomplete"){Text(reason)}}}
            r.gap?.let {gap->item {SectionCard("Unfunded cash flow") {Text("Month ${gap.month} · Shortfall ${money(gap.amount,gap.currency,hidden)}",style=MaterialTheme.typography.titleLarge);Text(gap.reason);Text("The projection stops here; no later net worth is claimed.")}}}
            if(r.points.isNotEmpty())item {SectionCard(if(r.complete)"Estimated outcome" else "Last modeled step") {
                val last=r.points.last();Text("${dateLabel(last.date)} · Month ${last.month}")
                Text("Nominal wealth ${money(last.nominal,hidden=hidden)}",style=MaterialTheme.typography.headlineSmall)
                Text("Today's purchasing power ${money(last.real,hidden=hidden)}",style=MaterialTheme.typography.titleLarge)
                Text("Assets ${money(last.assets,hidden=hidden)} · Liabilities ${money(last.liabilities,hidden=hidden)}")
                ForecastChart(r.points,hidden)
            }}
            item {DetailsDisclosure("Saved assumptions") {
                Text("Starting date ${dateLabel(s.asOf)} · Entered ${recordedLabel(s.recordedAt)}")
                Text("Inflation ${pct(s.inflation,hidden)} · Cash return ${pct(s.cashReturn,hidden)} · New investment return ${pct(s.investmentReturn,hidden)}")
                Text("Monthly new investments ${money(s.monthlyContribution,hidden=hidden)} · Annual step-up ${pct(s.stepUp,hidden)}")
                if(s.mode==ForecastMode.CASH_FLOW){Text("Monthly net income ${money(s.salary,hidden=hidden)} · Growth ${pct(s.salaryGrowth,hidden)}");Text("Living/other ${money(s.essential+s.discretionary,hidden=hidden)} · Premium budget ${money(s.premiums,hidden=hidden)} · Expense inflation ${pct(s.expenseInflation,hidden)}")}
                Text("Deposit reinvestment ${s.reinvestDeposits} · Goal withdrawals ${s.includeGoals}")
                state.book.planning.assets.filter {it.scenarioId==s.id}.forEach {a->val i=state.book.portfolio.instruments.find {it.id==a.instrumentId};Text("${i?.name} · Return ${pct(a.annualReturn,hidden)} · Monthly ${money(a.monthlyAmount,i?.currency ?: CurrencyCode.INR,hidden)}${if(i?.tracking==TrackingMode.LOAN)" repayment; contract rate applies" else " contribution"}")}
                state.book.planning.fx.filter {it.scenarioId==s.id}.forEach {Text("${it.currency}: INR-per-unit annual change ${pct(it.annualChange,hidden)}")}
                if(s.note.isNotBlank())Text(if(hidden)"Note hidden" else s.note)
            }}
            item {DisclosureHeader("Starting inputs",inputs,{inputs=!inputs})}
            if(inputs)itemsIndexed(r.inputs,key={index,_->"input:$index"}) {_,input->SectionCard(input.name){Text(money(input.value,input.currency,hidden));Text("${input.date?.let(::dateLabel) ?: "Unknown date"} · ${input.status}")}}
            item {DetailsDisclosure("Timing and limitations"){r.warnings.forEach {Text(it)}}}
            if(r.points.isNotEmpty())item {DisclosureHeader("Projection steps",steps,{steps=!steps})}
            if(steps)item {TextButton(onClick={monthly=!monthly}){Text(if(monthly)"Show annual steps" else "Show every monthly step")}}
            if(steps)items(r.points.filter {monthly || it.month%12==0 || it==r.points.last()},key={"month:${it.month}"}) {p->SectionCard("${dateLabel(p.date)} · Month ${p.month}") {
                Text("Nominal ${money(p.nominal,hidden=hidden)} · Real ${money(p.real,hidden=hidden)}")
                Text("Cash ${money(p.cash,hidden=hidden)} · Contributions ${money(p.contributions,hidden=hidden)}")
                if(s.mode==ForecastMode.CASH_FLOW)Text("Income ${money(p.salary,hidden=hidden)} · Outflows ${money(p.outflows,hidden=hidden)}")
            }}
        }
        val history=state.book.planning.scenarios.filter {it.key==s.key}.sortedByDescending {it.revision}
        if(history.size>1)item {Text("Retained scenario revisions",style=MaterialTheme.typography.titleLarge)}
        items(history.drop(1),key={"history:${it.id}"}) {old->SectionCard("Revision ${old.revision}"){Text("${old.kind.label} · ${old.mode.label} · ${dateLabel(old.asOf)} · ${old.months} months");Text("Inflation ${pct(old.inflation,hidden)} · Return ${pct(old.investmentReturn,hidden)} · Contributions ${money(old.monthlyContribution,hidden=hidden)}");Text(if(hidden)"Note hidden" else old.note)}}
    }
}
@Composable fun GoalsScreen(state:BankUiState,hidden:Boolean,onAdd:()->Unit,onEdit:(UUID)->Unit) {
    val goals=state.book.planning.latestGoals()
    val results=calculateAway(goals) {goals.associate {it.key to Goals.at(it,LocalDate.now())}}
    LazyColumn(Modifier.fillMaxSize().testTag("goals-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text("Financial goals",style=MaterialTheme.typography.headlineMedium);Text("Targets and monthly contributions are planning estimates. Earmarked existing funds are not new assets.")}
        if(!state.demo)item {Button(onClick=onAdd,modifier=Modifier.fillMaxWidth()){Text("New goal")}}
        item {CalculationState(results.loading,results.error)}
        if(goals.isEmpty())item {Text("No goals yet. Record a target date and distinguish a future amount from today's purchasing power.")}
        items(goals,key={it.key}) {g->SectionCard(g.name) {
            Text("Due ${dateLabel(g.due)} · ${g.currency} · Revision ${g.revision}")
            Text(if(g.active)"Active goal" else "Inactive / completed goal")
            Text("Target ${money(g.target,g.currency,hidden)} · ${if(g.targetInTodayMoney)"Purchasing power at ${dateLabel(g.basisDate)}" else "Future amount"}")
            Text("Declared existing earmark ${money(g.allocated,g.currency,hidden)}")
            results.value?.get(g.key)?.let {r->Text("Estimated future target ${money(r.targetFuture,g.currency,hidden)}");Text("Required monthly contribution ${money(r.monthlyRequired,g.currency,hidden)}",style=MaterialTheme.typography.titleLarge);Text(r.status)}
            Text("Return ${pct(g.annualReturn,hidden)} · Inflation ${pct(g.inflation,hidden)}")
            if(!state.demo)OutlinedButton(onClick={onEdit(g.key)}){Text("Revise goal")}
            if(g.note.isNotBlank())Text(if(hidden)"Note hidden" else g.note)
            val old=state.book.planning.goals.filter {it.key==g.key && it.revision<g.revision}.sortedByDescending {it.revision}
            old.forEach {Text("Retained revision ${it.revision} · ${money(it.target,it.currency,hidden)} · Due ${dateLabel(it.due)}");Text(if(hidden)"Note hidden" else it.note)}
        }}
    }
}
@Composable fun AnalyticsScreen(state:BankUiState,hidden:Boolean) {
    var days by rememberSaveable {mutableStateOf("30")}
    val today=LocalDate.now();val prior=today.minusDays(days.toLong())
    val calculation=calculateAway(state.book,days) {
        Triple(WealthValuation.at(state.book,today),WealthValuation.at(state.book,prior),Performance.portfolioXirr(state.book,today)) to
            state.book.portfolio.instruments.filter {it.tracking==TrackingMode.TRANSACTIONS}.associateWith {Performance.instrument(state.book,it.id,today)}
    }
    LazyColumn(Modifier.fillMaxSize().testTag("analytics-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text("Wealth insights",style=MaterialTheme.typography.headlineMedium)}
        item {HistoryCard(state.book,hidden)}
        item {Choice("Movement period",days,listOf("30" to "30 days","90" to "90 days","365" to "365 days")){days=it};CalculationState(calculation.loading,calculation.error)}
        val data=calculation.value
        if(data!=null){val(current,old,xirr)=data.first
            item {SectionCard("Recorded wealth movement") {
                Text("${dateLabel(prior)} → ${dateLabel(today)}")
                Text("Starting ${money(old.netWorthInr,hidden=hidden)} · Current ${money(current.netWorthInr,hidden=hidden)}")
                Text("Change ${money(if(old.netWorthInr!=null && current.netWorthInr!=null)current.netWorthInr!!-old.netWorthInr!! else null,hidden=hidden)}",style=MaterialTheme.typography.titleLarge)
                Text("Missing historical observations leave movement unavailable. Values between observations are carried forward; contract/corporate-action estimates remain estimates.")
                current.inputs.forEach {Text("${it.account.name} · ${it.observation?.effectiveDate?.let(::dateLabel) ?: "Missing"} · ${if(it.provisional)"Provisional" else "Observed / carried"}")}
                current.assets.forEach {Text("${it.instrument.name} · ${it.date?.let(::dateLabel) ?: "Unknown"} · ${it.status}")}
            }}
            item {SectionCard("Transaction-investment portfolio XIRR") {
                Text(pct(xirr.rate,hidden),style=MaterialTheme.typography.headlineMedium);Text(xirr.status)
                Text("Investment holdings need dated transaction history and FX. Value-only investment snapshots make this return unavailable. Cash, property, deposits and loans are excluded.")
                xirr.flows.forEach {Text("${dateLabel(it.date)} · ${money(it.amount,hidden=hidden)}")}
            }}
            items(data.second.entries.toList(),key={it.key.id}) {(i,p)->SectionCard(i.name) {
                Text("${i.currency} · ${dateLabel(today)}")
                Text("Contributed capital ${money(p.invested,i.currency,hidden)} · Market value ${money(p.value,i.currency,hidden)}")
                Text("Realized ${money(p.realized,i.currency,hidden)} · Unrealized ${money(p.unrealized,i.currency,hidden)}")
                Text("Simple gain / cumulative contributed capital ${pct(p.simpleReturn,hidden)}")
                Text("Includes known opening basis, purchases and external fees; retained reinvested distributions are internal. This is not annualised or time-weighted.",style=MaterialTheme.typography.bodyMedium)
                Text("XIRR ${pct(p.xirr.rate,hidden)} · ${p.xirr.status}")
                Text("CAGR ${pct(p.cagr.rate,hidden)} · ${p.cagr.status}")
                p.cashFlows.forEach {Text("${dateLabel(it.date)} · ${money(it.amount,i.currency,hidden)}")}
            }}
        }
    }
}
