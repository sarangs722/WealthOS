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
    Form(if(prior==null)"New forecast scenario" else "Revise forecast scenario","Independently saved assumptions. Rates are your estimates, not recommendations. All values are before tax unless explicitly entered.",state,"Save scenario",{
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
        Choice("Scenario type",fields.getValue("kind"),ScenarioKind.entries.map {it.name to it.label}) {fields["kind"]=it}
        Choice("Forecast mode",fields.getValue("mode"),ForecastMode.entries.map {it.name to it.label}) {fields["mode"]=it}
        PlanField(fields,"date","Starting valuation date (YYYY-MM-DD)",false,"Use today or an earlier date with complete observations. No future observations are fabricated.")
        Choice("Horizon",fields.getValue("months"),listOf(60,120,180,240,360).map {it.toString() to "${it/12} years"}) {fields["months"]=it}
        PlanField(fields,"inflation","Purchasing-power inflation % per year")
        PlanField(fields,"cash","Expected cash return % per year")
        PlanField(fields,"return","Planned investment return % per year")
        PlanField(fields,"contribution","Monthly new investment in INR",hint="A separate modeled investment bucket. Exclude amounts already entered for specific assets below.")
        PlanField(fields,"step","Annual contribution step-up %")
        if(fields["mode"]==ForecastMode.CASH_FLOW.name){
            Text("Cash-flow assumptions · INR",style=MaterialTheme.typography.titleLarge)
            PlanField(fields,"salary","Monthly net salary / income in INR")
            PlanField(fields,"salaryGrowth","Annual income growth %")
            PlanField(fields,"essential","Monthly essential expenses in INR")
            PlanField(fields,"discretionary","Monthly discretionary / other expenses in INR")
            PlanField(fields,"expenseInflation","Expense inflation % per year")
            PlanField(fields,"premiums","Monthly insurance premium budget in INR",hint="Explicit monthly budget, not an automatic copy of policy installments. Tracked loan payments are entered per loan below.")
        } else Text("Contributions and loan payments are funded outside the modeled starting wealth. Salary, expenses and premiums are ignored in this mode.")
        Toggle("Reinvest estimated deposit maturities",fields["reinvest"]=="true") {fields["reinvest"]=it.toString()}
        Toggle("Withdraw shared financial goals when due",fields["goals"]=="true") {fields["goals"]=it.toString()}
        DetailsDisclosure("Asset-specific assumptions"){
        state.book.portfolio.instruments.forEach {i->
            Text("${i.name} · ${i.currency}",style=MaterialTheme.typography.titleMedium)
            if(i.tracking==TrackingMode.LOAN)Text("Uses the recorded contract rate. Enter the planned monthly total repayment, including interest. Zero means no planned payment.")
            else PlanField(assetFields,"rate:${i.id}","${i.name}: annual return %",hint=if(i.tracking==TrackingMode.DEPOSIT)"Contract rate applies until maturity; this return applies only after reinvestment." else "Native-currency effective annual return.")
            if(i.category!=AssetCategory.FD)PlanField(assetFields,"amount:${i.id}",if(i.tracking==TrackingMode.LOAN)"${i.name}: monthly repayment" else "${i.name}: monthly contribution",hint="${i.currency}. Cash-flow mode needs available cash in this native currency; no FX cash conversion is inferred.")
        }
        }
        if(fxFields.isNotEmpty())DetailsDisclosure("Currency assumptions"){
        fxFields.keys.toList().forEach {currency->PlanField(fxFields,currency,"$currency: annual change in INR per unit %",hint="Starting rate comes from the dated applied FX observation. Positive means this currency strengthens against INR.")}
        }
        DetailsDisclosure("Planned withdrawals"){
        Text("Month end, after contributions. Cash withdrawals are INR; named asset withdrawals use native currency. No assumed borrowing covers a shortfall.")
        eventFields.keys.toList().forEach {key->val p=eventFields.getValue(key).split('|',limit=4);val source=sources.find {it.id.toString()==p[1]};Text("${p.getOrElse(3){"Withdrawal"}} · Month ${p[0]} · ${source?.name ?: "INR cash"} · ${p[2]} ${source?.currency ?: CurrencyCode.INR}");TextButton(onClick={eventFields.remove(key)}) {Text("Remove withdrawal ${key.toInt()+1}")}}
        PlanField(eventInput,"label","Withdrawal label",false)
        PlanField(eventInput,"month","Withdrawal month number",false)
        Choice("Withdrawal source",eventInput.getValue("source"),listOf("cash" to "INR cash")+sources.map {it.id.toString() to "${it.name} · ${it.currency}"}) {eventInput["source"]=it}
        PlanField(eventInput,"amount","Withdrawal amount")
        OutlinedButton(onClick={if(eventFields.size<200){val k=((eventFields.keys.mapNotNull {it.toIntOrNull()}.maxOrNull() ?: -1)+1).toString();eventFields[k]="${eventInput["month"]}|${eventInput["source"]}|${eventInput["amount"]}|${eventInput["label"]}"}}) {Text("Add planned withdrawal")}
        }
        Field("Scenario note / revision explanation",fields.getValue("note"),{fields["note"]=it},multi=true)
        DetailsDisclosure("Timing & calculation details"){Text("Monthly effective growth = (1 + annual rate)^(1/12) − 1. Contributions occur at month end; step-ups begin in month 13. Unfunded cash stops the projection. Changing a saved scenario appends a revision and preserves its old assumptions.")}
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
        if(prior==null)Choice("Goal currency",f.getValue("currency"),CurrencyCode.entries.map {it.name to it.name}) {f["currency"]=it}
        PlanField(f,"target","Target amount in ${f["currency"]}")
        PlanField(f,"due","Target date (YYYY-MM-DD)",false)
        Toggle("Target is stated in today's money",f["today"]=="true") {f["today"]=it.toString()}
        PlanField(f,"basis","Purchasing-power / earmark reference date (YYYY-MM-DD)",false,"Inflation is anchored to this saved date. Review the declared earmark rather than assuming past growth.")
        PlanField(f,"allocated","Existing amount earmarked in ${f["currency"]}",hint="Planning input only; confirm actual liquidity and avoid assigning the same funds to incompatible goals.")
        PlanField(f,"return","Assumed annual return %")
        PlanField(f,"inflation","Assumed annual target inflation %")
        Toggle("Track this goal as active",f["active"]=="true") {f["active"]=it.toString()}
        Text("Deactivate a completed or paused goal to exclude it from contributions and scenario goal withdrawals. This never posts a cash movement or removes history.")
        Field("Goal note / revision explanation",f.getValue("note"),{f["note"]=it},multi=true)
        Text("Required contributions use end-of-month payments through the target month. Near-term and overdue goals show a direct amount comparison instead of an invented monthly annuity. Including goals in a forecast withdraws their target amounts, not an additional asset.")
    }
}
