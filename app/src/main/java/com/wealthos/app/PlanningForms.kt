package com.wealthos.app

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.wealthos.finance.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Composable internal fun savedFields(initial:Map<String,String>):SnapshotStateMap<String,String> = rememberSaveable(saver=mapSaver<SnapshotStateMap<String,String>>(
    save={it.toMap()},restore={values->values.map {it.key to it.value as String}.toMutableStateMap()})){initial.toList().toMutableStateMap()}
internal fun percentInput(text:String)=DecimalInput.parse(text).movePointLeft(2)
internal fun percentText(value:BigDecimal)=value.movePointRight(2).stripTrailingZeros().toPlainString()
@Composable internal fun PlanField(fields:SnapshotStateMap<String,String>,key:String,label:String,decimal:Boolean=true,hint:String?=null){Field(label,fields[key] ?: "",{fields[key]=it},decimal,hint)}
@Composable fun ScenarioForm(state:BankUiState,model:BankViewModel,prior:ForecastScenario?,done:(UUID)->Unit) {
    val fields=savedFields(mapOf("id" to UUID.randomUUID().toString(),"key" to (prior?.key?.toString() ?: UUID.randomUUID().toString()),"name" to (prior?.name ?: ""),
        "kind" to (prior?.kind ?: ScenarioKind.BASE).name,"mode" to (prior?.mode ?: ForecastMode.CONTRIBUTION).name,"date" to (prior?.asOf ?: LocalDate.now()).toString(),"months" to (prior?.months ?: 120).toString(),
        "inflation" to percentText(prior?.inflation ?: BigDecimal.ZERO),"cash" to percentText(prior?.cashReturn ?: BigDecimal.ZERO),"return" to percentText(prior?.investmentReturn ?: BigDecimal.ZERO),
        "contribution" to (prior?.monthlyContribution ?: BigDecimal.ZERO).toPlainString(),"step" to percentText(prior?.stepUp ?: BigDecimal.ZERO),"salary" to (prior?.salary ?: BigDecimal.ZERO).toPlainString(),
        "salaryGrowth" to percentText(prior?.salaryGrowth ?: BigDecimal.ZERO),"essential" to (prior?.essential ?: BigDecimal.ZERO).toPlainString(),"discretionary" to (prior?.discretionary ?: BigDecimal.ZERO).toPlainString(),
        "expenseInflation" to percentText(prior?.expenseInflation ?: BigDecimal.ZERO),"premiums" to (prior?.premiums ?: BigDecimal.ZERO).toPlainString(),"reinvest" to (prior?.reinvestDeposits ?: false).toString(),"goals" to (prior?.includeGoals ?: false).toString(),"note" to ""))
    val assetFields=savedFields(state.book.portfolio.instruments.flatMap {i->val p=prior?.let {s->state.book.planning.assets.find {it.scenarioId==s.id && it.instrumentId==i.id}}
        listOf("rate:${i.id}" to percentText(p?.annualReturn ?: prior?.investmentReturn ?: BigDecimal.ZERO),"amount:${i.id}" to (p?.monthlyAmount ?: BigDecimal.ZERO).toPlainString())}.toMap())
    val fxFields=savedFields(CurrencyCode.entries.filter {it!=CurrencyCode.INR}.associate {c->c.name to percentText(prior?.let {s->state.book.planning.fx.find {it.scenarioId==s.id && it.currency==c}?.annualChange} ?: BigDecimal.ZERO)})
    val oldWithdrawals=prior?.let {s->state.book.planning.withdrawals.filter {it.scenarioId==s.id}} ?: emptyList()
    val eventFields=savedFields(oldWithdrawals.mapIndexed {n,w->n.toString() to "${w.month}|${w.instrumentId ?: "cash"}|${w.amount.toPlainString()}|${w.label}"}.toMap())
    val eventInput=savedFields(mapOf("month" to "1","source" to "cash","amount" to "","label" to ""))
    val sources=state.book.portfolio.instruments.filter {it.tracking !in listOf(TrackingMode.LOAN,TrackingMode.DEPOSIT)}
    Form(if(prior==null)"New forecast scenario" else "Revise forecast scenario","Start from your recorded wealth. All future figures are estimates.",state,"Save scenario",{
        model.planning({done(UUID.fromString(fields.getValue("key")))}) {repo->
            val id=UUID.fromString(fields.getValue("id"))
            fun rate(k:String)=percentInput(fields.getValue(k))
            fun amount(k:String)=Amounts.parse(fields.getValue(k),CurrencyCode.INR)
            val s=ForecastScenario(id,UUID.fromString(fields.getValue("key")),fields.getValue("name"),ScenarioKind.valueOf(fields.getValue("kind")),ForecastMode.valueOf(fields.getValue("mode")),LocalDate.parse(fields.getValue("date")),fields.getValue("months").toInt(),rate("inflation"),rate("cash"),rate("return"),amount("contribution"),rate("step"),amount("salary"),rate("salaryGrowth"),amount("essential"),amount("discretionary"),rate("expenseInflation"),amount("premiums"),fields["reinvest"]=="true",fields["goals"]=="true",0,Instant.now(),fields.getValue("note"))
            val assets=state.book.portfolio.instruments.map {i->AssetAssumption(id,i.id,percentInput(assetFields.getValue("rate:${i.id}")),Amounts.parse(assetFields.getValue("amount:${i.id}"),i.currency))}
            val fx=fxFields.map {(currency,value)->FxAssumption(id,CurrencyCode.valueOf(currency),percentInput(value))}
            val events=eventFields.values.map {text->val p=text.split('|',limit=4);val source=p[1].takeUnless {it=="cash"}?.let(UUID::fromString);val c=sources.find {it.id==source}?.currency ?: CurrencyCode.INR
                PlannedWithdrawal(UUID.randomUUID(),id,p[0].toInt(),source,Amounts.parse(p[2],c),p[3])}
            repo.saveScenario(s,assets,fx,events)
        }
    }) {
        PlanField(fields,"name","Scenario name",false)
        PlanField(fields,"contribution","Monthly new investment in INR",hint="A separate modeled investment bucket. Exclude contributions planned for individual holdings.")
        Choice("Horizon",fields.getValue("months"),listOf(60,120,180,240,360).map {it.toString() to "${it/12} years"}) {fields["months"]=it}
        PlanField(fields,"return","Planned investment return % per year",hint="For the new-investment bucket. Current holdings have separate assumptions below. No return is guaranteed.")
        PlanField(fields,"inflation","Purchasing-power inflation % per year")
        FormDetails("Funding & monthly budget",summary=if(fields["mode"]==ForecastMode.CASH_FLOW.name)"Funded from modeled income and remaining cash" else "Planned contributions funded outside starting wealth") {
            Choice("Forecast mode",fields.getValue("mode"),ForecastMode.entries.map {it.name to it.label}) {fields["mode"]=it}
            if(fields["mode"]==ForecastMode.CASH_FLOW.name){
                PlanField(fields,"salary","Monthly net salary / income in INR")
                PlanField(fields,"essential","Monthly essential expenses in INR")
                PlanField(fields,"discretionary","Monthly discretionary / other expenses in INR")
                PlanField(fields,"premiums","Monthly insurance premium budget in INR",hint="Planning budget only. It is not copied from policy installments.")
            }
            Text(if(fields["mode"]==ForecastMode.CASH_FLOW.name)"Income pays expenses, loan obligations and planned contributions. A cash shortfall stops the projection." else "Contributions and planned loan payments come from outside this starting wealth. Salary and living expenses are ignored.",style=MaterialTheme.typography.bodySmall)
        }
        FormDetails("Growth & other assumptions",summary="Step-up ${fields["step"]}% per year; cash return ${fields["cash"]}%") {
            PlanField(fields,"step","Annual contribution step-up %")
            PlanField(fields,"cash","Expected cash return % per year")
            if(fields["mode"]==ForecastMode.CASH_FLOW.name){
                PlanField(fields,"salaryGrowth","Annual income growth %")
                PlanField(fields,"expenseInflation","Expense inflation % per year")
            }
            Choice("Scenario type",fields.getValue("kind"),ScenarioKind.entries.map {it.name to it.label}) {fields["kind"]=it}
            DateChoice("Starting values as of",fields.getValue("date")){fields["date"]=it}
            Toggle("Reinvest estimated deposit maturities",fields["reinvest"]=="true") {fields["reinvest"]=it.toString()}
            Toggle("Withdraw shared financial goals when due",fields["goals"]=="true") {fields["goals"]=it.toString()}
        }
        if(state.book.portfolio.instruments.isNotEmpty())FormDetails("Asset-specific assumptions",summary="${state.book.portfolio.instruments.size} holdings; review their returns and contributions separately"){
        val investmentCategories=setOf(AssetCategory.MUTUAL_FUND,AssetCategory.STOCK,AssetCategory.ETF,AssetCategory.GOLD,AssetCategory.COMMODITY)
        if(state.book.portfolio.instruments.any {it.category in investmentCategories})OutlinedButton(onClick={
            state.book.portfolio.instruments.filter {it.category in investmentCategories}.forEach {assetFields["rate:${it.id}"]=fields.getValue("return")}
        },enabled=!state.saving && !state.demo && runCatching {DecimalMath.rate(percentInput(fields.getValue("return")));true}.getOrDefault(false)){Text("Apply this return to current investments")}
        Text("The button applies your new-investment return to funds, stocks, ETFs, gold and commodities. Other assets keep their separate assumptions.",style=MaterialTheme.typography.bodySmall)

        state.book.portfolio.instruments.forEach {i->
            Text("${i.name} · ${i.currency}",style=MaterialTheme.typography.titleMedium)
            if(i.tracking==TrackingMode.LOAN)Text("Uses the recorded contract rate. Enter the planned monthly total repayment, including interest. Zero means no planned payment.")
            else PlanField(assetFields,"rate:${i.id}","${i.name}: annual return %",hint=if(i.tracking==TrackingMode.DEPOSIT)"Contract rate applies until maturity; this return applies only after reinvestment." else "Native-currency effective annual return.")
            if(i.category!=AssetCategory.FD)PlanField(assetFields,"amount:${i.id}",if(i.tracking==TrackingMode.LOAN)"${i.name}: monthly repayment" else "${i.name}: monthly contribution",hint="${i.currency}. Cash-flow mode needs available cash in this native currency; no FX cash conversion is inferred.")
        }
        }
        val relevantFx=(state.book.accounts.map {it.currency}+state.book.portfolio.instruments.map {it.currency}).filter {it!=CurrencyCode.INR}.map {it.name}.toSet()+fxFields.filterValues {it.toBigDecimalOrNull()?.signum()!=0}.keys
        if(relevantFx.isNotEmpty())FormDetails("Currency assumptions",summary="${relevantFx.size} currencies"){
        fxFields.keys.filter {it in relevantFx}.forEach {currency->PlanField(fxFields,currency,"$currency: annual change in INR per unit %",hint="Starting rate comes from the dated applied FX observation. Positive means this currency strengthens against INR.")}
        }
        FormDetails("Planned withdrawals"){
        Text("Month end, after contributions. Cash withdrawals are INR; named asset withdrawals use native currency. No assumed borrowing covers a shortfall.")
        eventFields.keys.toList().forEach {key->val p=eventFields.getValue(key).split('|',limit=4);val source=sources.find {it.id.toString()==p[1]};Text("${p.getOrElse(3){"Withdrawal"}} · Month ${p[0]} · ${source?.name ?: "INR cash"} · ${p[2]} ${source?.currency ?: CurrencyCode.INR}");TextButton(onClick={eventFields.remove(key)}) {Text("Remove withdrawal ${key.toInt()+1}")}}
        PlanField(eventInput,"label","Withdrawal label",false)
        PlanField(eventInput,"month","Withdrawal month number",false)
        Choice("Withdrawal source",eventInput.getValue("source"),listOf("cash" to "INR cash")+sources.map {it.id.toString() to "${it.name} · ${it.currency}"}) {eventInput["source"]=it}
        PlanField(eventInput,"amount","Withdrawal amount")
        OutlinedButton(onClick={if(eventFields.size<200){val k=((eventFields.keys.mapNotNull {it.toIntOrNull()}.maxOrNull() ?: -1)+1).toString();eventFields[k]="${eventInput["month"]}|${eventInput["source"]}|${eventInput["amount"]}|${eventInput["label"]}"}}) {Text("Add planned withdrawal")}
        }
        if(prior!=null)Field("Scenario note / revision explanation",fields.getValue("note"),{fields["note"]=it},multi=true)
        else FormDetails("Note (optional)"){Field("Scenario note / revision explanation",fields.getValue("note"),{fields["note"]=it},multi=true)}
        FormDetails("Timing & calculation details"){Text("Monthly effective growth = (1 + annual rate)^(1/12) − 1. Contributions occur at month end; step-ups begin in month 13. Unfunded cash stops the projection. Changing a saved scenario appends a revision and preserves its old assumptions.")}
    }
}

@Composable fun GoalForm(state:BankUiState,model:BankViewModel,prior:FinancialGoal?,done:()->Unit) {
    val f=savedFields(mapOf("name" to (prior?.name ?: ""),"currency" to (prior?.currency ?: CurrencyCode.INR).name,"target" to (prior?.target?.toPlainString() ?: ""),"due" to (prior?.due ?: LocalDate.now().plusYears(5)).toString(),"today" to (prior?.targetInTodayMoney ?: true).toString(),"allocated" to (prior?.allocated ?: BigDecimal.ZERO).toPlainString(),"return" to percentText(prior?.annualReturn ?: BigDecimal.ZERO),"inflation" to percentText(prior?.inflation ?: BigDecimal.ZERO),"note" to "","key" to (prior?.key?.toString() ?: UUID.randomUUID().toString())))
    if("basis" !in f)f["basis"]=(prior?.basisDate ?: LocalDate.now()).toString()
    if("active" !in f)f["active"]=(prior?.active ?: true).toString()
    Form(if(prior==null)"New financial goal" else "Revise financial goal","A goal and its earmarked amount do not add to net worth. The allocated amount is your declared share of existing wealth.",state,"Save goal",{
        model.planning(done) {repo->
            val currency=CurrencyCode.valueOf(f.getValue("currency"))
            val record=FinancialGoal(UUID.randomUUID(),UUID.fromString(f.getValue("key")),f.getValue("name"),currency,
                Amounts.parse(f.getValue("target"),currency),LocalDate.parse(f.getValue("due")),f["today"]=="true",
                Amounts.parse(f.getValue("allocated"),currency),percentInput(f.getValue("return")),percentInput(f.getValue("inflation")),
                0,Instant.now(),f.getValue("note"),LocalDate.parse(f.getValue("basis")),f["active"]=="true")
            repo.saveGoal(record)
        }
    }) {
        PlanField(f,"name","Goal name",false)
        PlanField(f,"target","Target amount in ${f["currency"]}")
        DateChoice("Target date",f.getValue("due")){f["due"]=it}
        Toggle("Target is stated in today's money",f["today"]=="true") {f["today"]=it.toString()}
        FormDetails("Funding & growth",summary="Set aside ${f["allocated"]} ${f["currency"]}; return ${f["return"]}%; inflation ${f["inflation"]}%") {
            PlanField(f,"allocated","Existing amount earmarked in ${f["currency"]}",hint="Planning only. Avoid assigning the same funds to incompatible goals.")
            PlanField(f,"return","Assumed annual return %")
            PlanField(f,"inflation","Assumed annual target inflation %")
            DateChoice("Reference date",f.getValue("basis")){f["basis"]=it}
            if(prior==null)Choice("Goal currency",f.getValue("currency"),CurrencyCode.entries.map {it.name to it.name}) {f["currency"]=it}
            Toggle("Track this goal as active",f["active"]=="true") {f["active"]=it.toString()}
            Text("Earmarks do not add assets. Deactivating a goal keeps its history and stops planned contributions/withdrawals.",style=MaterialTheme.typography.bodySmall)
        }
        if(prior!=null)Field("Goal note / revision explanation",f.getValue("note"),{f["note"]=it},multi=true)
        else FormDetails("Note (optional)"){Field("Goal note / revision explanation",f.getValue("note"),{f["note"]=it},multi=true)}
        FormDetails("How contributions are calculated"){Text("End-of-month payments through the target month. Near-term/overdue goals show an amount comparison. Rates are assumptions; no cash movement or additional asset is created.")}

    }
}
