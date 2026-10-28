package com.wealthos.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wealthos.finance.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CalculatorKind(val label:String,val description:String){
    INFLATION("Inflation & purchasing power","Future cost and today's value"),SIP("SIP","Effective monthly compounding"),STEP_UP("Step-up SIP","Annual contribution increases"),
    LUMPSUM("Lumpsum growth","Compound growth or decline"),CAGR("CAGR","Annualised growth without intermediate flows"),XIRR("XIRR","Dated cash flows; explicit ambiguity status"),
    FD("FD maturity","Recorded compounding and ACT/365 stub"),RD("RD maturity","Monthly deposits from the start date"),EMI("EMI & amortization","Monthly reducing balance"),
    PREPAY("Loan prepayment","Keep EMI, shorten term"),RETIREMENT("Retirement corpus","Finite inflation-linked withdrawals"),WITHDRAWAL("Withdrawal planning","Corpus depletion and remaining wealth"),
    GOAL("Goal contributions","Required monthly saving"),FX("Currency conversion","Manual dated INR reference rates"),REAL_RETURN("Real vs nominal returns","Consistent inflation adjustment")
}
private enum class OutputKind {AMOUNT,PERCENT,COUNT}
private data class CalculatorValue(val label:String,val value:BigDecimal?,val kind:OutputKind=OutputKind.AMOUNT)
private data class CalculatorOutput(val values:List<CalculatorValue>,val notes:List<String>,val schedule:List<Contracts.Payment> = emptyList())

@Composable fun ToolsScreen(onCalculator:(CalculatorKind)->Unit) {
    var query by rememberSaveable{mutableStateOf("")}
    val groups=listOf("Grow your money" to listOf(CalculatorKind.SIP,CalculatorKind.STEP_UP,CalculatorKind.LUMPSUM,CalculatorKind.FD,CalculatorKind.RD),
        "Loans" to listOf(CalculatorKind.EMI,CalculatorKind.PREPAY),
        "Plan ahead" to listOf(CalculatorKind.RETIREMENT,CalculatorKind.WITHDRAWAL,CalculatorKind.GOAL,CalculatorKind.INFLATION),
        "Returns & currency" to listOf(CalculatorKind.CAGR,CalculatorKind.XIRR,CalculatorKind.FX,CalculatorKind.REAL_RETURN))
    fun title(c:CalculatorKind)=when(c){CalculatorKind.INFLATION->"Inflation";CalculatorKind.EMI->"EMI";CalculatorKind.RETIREMENT->"Retirement";CalculatorKind.WITHDRAWAL->"Withdrawals";CalculatorKind.GOAL->"Goal savings";CalculatorKind.FX->"Currency";CalculatorKind.REAL_RETURN->"Real returns";else->c.label}
    LazyColumn(Modifier.fillMaxSize().testTag("tools-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {OutlinedTextField(query,{query=it},label={Text("Search calculators")},placeholder={Text("SIP, EMI, inflation…")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        groups.forEach{(label,tools)->
            val matches=tools.filter{it.label.contains(query,true)||it.description.contains(query,true)||title(it).contains(query,true)}
            if(matches.isNotEmpty())item{SectionHeading(label)}
            items(matches.chunked(2)){pair->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                pair.forEach{c->Card(onClick={onCalculator(c)},modifier=Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
                    Column(Modifier.fillMaxWidth().heightIn(min=96.dp).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        Icon(WealthIcons.calculator,null,tint=MaterialTheme.colorScheme.primary)
                        Text(title(c),style=MaterialTheme.typography.titleMedium)
                    }
                }}
                if(pair.size==1)Spacer(Modifier.weight(1f))
            }}
        }
        if(CalculatorKind.entries.none{it.label.contains(query,true)||it.description.contains(query,true)||title(it).contains(query,true)})item{Text("No calculator found. Try SIP, EMI or inflation.")}
    }
}
@Composable fun CalculatorScreen(kind:CalculatorKind,hidden:Boolean) {
    val fields=savedFields(mapOf("currency" to "INR","principal" to "","monthly" to "","target" to "","final" to "","rate" to "0","inflation" to "0","step" to "0","months" to "120","atMonth" to "1","extra" to "0","fee" to "0","years" to "0","start" to LocalDate.now().toString(),"end" to LocalDate.now().plusYears(1).toString(),"convention" to DepositConvention.QUARTERLY.name,"beginning" to "false","flows" to "","sourceFx" to "1","targetFx" to "","targetCurrency" to "USD","rateMode" to "nominal"))
    var output by remember {mutableStateOf<CalculatorOutput?>(null)};var error by remember {mutableStateOf<String?>(null)};var evaluatedInputs by remember {mutableStateOf<Map<String,String>?>(null)};var calculating by remember {mutableStateOf(false)};var allRows by rememberSaveable {mutableStateOf(false)}
    val scope=rememberCoroutineScope();val currency=CurrencyCode.valueOf(fields.getValue("currency"))
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(22.dp).testTag("calculator-form"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(kind.label,style=MaterialTheme.typography.headlineMedium)
        Text("Inputs are visible while editing, including when read-view amounts are obscured. Outputs use HALF_EVEN rounding at currency precision; calculations use decimal arithmetic.")
        Choice("Input currency",fields.getValue("currency"),CurrencyCode.entries.map {it.name to it.name}) {fields["currency"]=it;fields["sourceFx"]=if(it=="INR")"1" else "";output=null}
        when(kind){
            CalculatorKind.INFLATION->{PlanField(fields,"principal","Amount in $currency");PlanField(fields,"inflation","Annual inflation %");PlanField(fields,"months","Duration in months",false)}
            CalculatorKind.SIP,CalculatorKind.STEP_UP->{PlanField(fields,"monthly","Monthly contribution in $currency");PlanField(fields,"rate","Effective annual return %");PlanField(fields,"months","Duration in months",false)
                if(kind==CalculatorKind.STEP_UP)PlanField(fields,"step","Annual SIP step-up %")
                Toggle("Contributions at beginning of month",fields["beginning"]=="true"){fields["beginning"]=it.toString()}}
            CalculatorKind.LUMPSUM->{PlanField(fields,"principal","Starting amount in $currency");PlanField(fields,"rate","Effective annual return %");PlanField(fields,"months","Duration in months",false)}
            CalculatorKind.CAGR->{PlanField(fields,"principal","Starting value in $currency");PlanField(fields,"final","Ending value in $currency");PlanField(fields,"start","Starting date (YYYY-MM-DD)",false);PlanField(fields,"end","Ending date (YYYY-MM-DD)",false)}
            CalculatorKind.XIRR->{Text("One date,amount per line. Outflows negative, receipts positive. Include terminal market value explicitly. Same-day flows are combined. No sample data is inserted.")
                OutlinedTextField(fields.getValue("flows"),{if(it.length<=20000)fields["flows"]=it},label={Text("Dated cash flows")},modifier=Modifier.fillMaxWidth(),minLines=6)}
            CalculatorKind.FD,CalculatorKind.RD->{PlanField(fields,if(kind==CalculatorKind.FD)"principal" else "monthly",if(kind==CalculatorKind.FD)"Principal in $currency" else "Monthly installment in $currency");PlanField(fields,"rate","Nominal annual interest %");PlanField(fields,"start","Start date (YYYY-MM-DD)",false)
                if(kind==CalculatorKind.FD)PlanField(fields,"end","Maturity date (YYYY-MM-DD)",false) else PlanField(fields,"months","Number of monthly installments",false)
                Choice("Deposit convention",fields.getValue("convention"),DepositConvention.entries.map {it.name to it.label}){fields["convention"]=it}}
            CalculatorKind.EMI,CalculatorKind.PREPAY->{PlanField(fields,"principal","Outstanding principal in $currency");PlanField(fields,"rate","Nominal annual loan interest %");PlanField(fields,"months","Remaining term in months",false)
                if(kind==CalculatorKind.PREPAY){PlanField(fields,"atMonth","Prepayment after installment number",false);PlanField(fields,"extra","Additional principal payment in $currency");PlanField(fields,"fee","One-off prepayment fee in $currency")}}
            CalculatorKind.RETIREMENT->{PlanField(fields,"monthly","Today's monthly retirement spending in $currency");PlanField(fields,"years","Years until retirement",false);PlanField(fields,"months","Retirement duration in months",false);PlanField(fields,"rate","Effective annual retirement return %");PlanField(fields,"inflation","Annual spending inflation %")}
            CalculatorKind.WITHDRAWAL->{PlanField(fields,"principal","Starting corpus in $currency");PlanField(fields,"monthly","First monthly withdrawal in $currency");PlanField(fields,"rate","Effective annual return %");PlanField(fields,"inflation","Annual withdrawal inflation %");PlanField(fields,"months","Duration in months",false)}
            CalculatorKind.GOAL->{PlanField(fields,"target","Future target amount in $currency",hint="Enter the future nominal target. Use Inflation first if the target is stated in today's money.");PlanField(fields,"principal","Existing allocated corpus in $currency");PlanField(fields,"rate","Effective annual return %");PlanField(fields,"months","Months until target",false)}
            CalculatorKind.FX->{PlanField(fields,"principal","Amount in $currency");Choice("Target currency",fields.getValue("targetCurrency"),CurrencyCode.entries.map {it.name to it.name}){fields["targetCurrency"]=it;fields["targetFx"]=if(it=="INR")"1" else ""};PlanField(fields,"sourceFx","INR per one $currency",hint="Enter 1 if the input currency is INR. Foreign references must be entered manually.");PlanField(fields,"targetFx","INR per target-currency unit",hint="Enter 1 if the target currency is INR. No foreign quote is prefilled.");PlanField(fields,"start","Reference observation date (YYYY-MM-DD)",false)}
            CalculatorKind.REAL_RETURN->{Choice("Convert from",fields.getValue("rateMode"),listOf("nominal" to "Nominal return","real" to "Real return")){fields["rateMode"]=it};PlanField(fields,"rate",if(fields["rateMode"]=="real")"Real annual return %" else "Nominal annual return %");PlanField(fields,"inflation","Annual inflation %")}
        }
        Button(enabled=!calculating,onClick={
            val values=fields.toMap();evaluatedInputs=values;calculating=true;error=null;output=null
            scope.launch {
                try {output=withContext(Dispatchers.Default){calculateTool(kind,values)}}
                catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(e:Exception){error=if(e is IllegalArgumentException || e is ArithmeticException)e.message ?: "Check the inputs." else "Calculation unavailable."}
                finally {calculating=false}
            }
        },modifier=Modifier.fillMaxWidth()){Text(if(calculating)"Calculating…" else "Calculate")}
        if(evaluatedInputs==fields.toMap())error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        output?.takeIf {evaluatedInputs==fields.toMap()}?.let {result->SectionCard("Calculation result") {
            val outCurrency=if(kind==CalculatorKind.FX)CurrencyCode.valueOf(fields.getValue("targetCurrency")) else currency
            result.values.forEach {line->Text("${line.label}: ${when(line.kind){OutputKind.AMOUNT->money(line.value,outCurrency,hidden);OutputKind.PERCENT->pct(line.value,hidden);OutputKind.COUNT->if(hidden)"••" else line.value?.stripTrailingZeros()?.toPlainString() ?: "Unavailable"}}",style=MaterialTheme.typography.titleMedium)}
            if(kind in listOf(CalculatorKind.XIRR,CalculatorKind.CAGR,CalculatorKind.WITHDRAWAL))result.notes.firstOrNull()?.let{Text(it)}
            DetailsDisclosure("Calculation details"){result.notes.forEach {Text(it)}}
            if(result.schedule.isNotEmpty()){
                TextButton(onClick={allRows=!allRows}){Text(if(allRows)"Show first 12 and final installment" else "Show complete amortization")}
                result.schedule.filter {allRows || it.number<=12 || it==result.schedule.last()}.forEach {p->Text("Month ${p.number} · Principal ${money(p.principal,currency,hidden)} · Interest ${money(p.interest,currency,hidden)} · Closing ${money(p.closing,currency,hidden)}")}
            }
        }}
    }
}
private fun calculateTool(kind:CalculatorKind,f:Map<String,String>):CalculatorOutput {
    val currency=CurrencyCode.valueOf(f.getValue("currency"));val mc=DecimalMath.context
    fun amount(key:String)=Amounts.parse(f.getValue(key),currency).also {FinancialMath.nonnegative(it)}
    fun rate(key:String)=percentInput(f.getValue(key)).also {DecimalMath.rate(it)}
    fun n()=f.getValue("months").toInt().also {FinancialMath.months(it)}
    fun date(key:String)=LocalDate.parse(f.getValue(key)).also {require(it.year in 1900..2200)}
    fun line(label:String,value:BigDecimal?,kind:OutputKind=OutputKind.AMOUNT)=CalculatorValue(label,value,kind)
    val note="Estimate, before tax and unentered fees. Effective rates compound geometrically; nominal deposit/loan rates use the selected convention."
    return when(kind){
        CalculatorKind.INFLATION->{val factor=FinancialMath.factor(rate("inflation"),n());CalculatorOutput(listOf(line("Future cost",amount("principal").multiply(factor,mc)),line("Today's purchasing power of the same future amount",amount("principal").divide(factor,mc))),listOf(note,"Duration is measured in full months."))}
        CalculatorKind.SIP,CalculatorKind.STEP_UP->{val r=FinancialMath.sip(amount("monthly"),rate("rate"),n(),if(kind==CalculatorKind.STEP_UP)rate("step") else BigDecimal.ZERO,f["beginning"]=="true");CalculatorOutput(listOf(line("Estimated maturity",r.value),line("Total contributions",r.contributions),line("Estimated gain",r.gain)),listOf(note,"Annual step-ups occur before month 13, 25, etc. Contributions follow your beginning/end-of-month selection."))}
        CalculatorKind.LUMPSUM->{val principal=amount("principal");val end=FinancialMath.growth(principal,rate("rate"),n());CalculatorOutput(listOf(line("Estimated future value",end),line("Estimated gain",end-principal)),listOf(note))}
        CalculatorKind.CAGR->{val r=FinancialMath.cagr(amount("principal"),amount("final"),date("start"),date("end"));CalculatorOutput(listOf(line("Annualised CAGR",r.rate,OutputKind.PERCENT)),listOf(r.status,"Assumes no intermediate deposits, distributions or withdrawals."))}
        CalculatorKind.XIRR->{val flows=f.getValue("flows").lineSequence().filter {it.isNotBlank()}.map {row->val p=row.split(',');require(p.size==2){"Use YYYY-MM-DD,amount on each line."};DatedCashFlow(LocalDate.parse(p[0].trim()),Amounts.parse(p[1].trim(),currency))}.toList();val r=FinancialMath.xirr(flows);CalculatorOutput(listOf(line("Annualised XIRR",r.rate,OutputKind.PERCENT)),listOf(r.status,"ACT/365. Multiple sign changes are conservatively unavailable because uniqueness is not established. Supported log-rate bracket: -20 through 20; bounded decimal bisection."))}
        CalculatorKind.FD->{val principal=amount("principal");val r=rate("rate");require(r>=BigDecimal.ZERO);val start=date("start");val end=date("end");require(end>start && end<=start.plusYears(100));val value=Contracts.accrued(principal,r,start,end,DepositConvention.valueOf(f.getValue("convention")));CalculatorOutput(listOf(line("Gross maturity estimate",value),line("Gross interest",value-principal)),listOf(note,"Full periods use nominal compounding; remaining stub uses simple ACT/365. No bank-specific penalty, tax or interest payout is inferred."))}
        CalculatorKind.RD->{val monthly=amount("monthly");val r=rate("rate");require(r>=BigDecimal.ZERO);val count=n();val total=monthly*count.toBigDecimal();val value=FinancialMath.rd(monthly,r,date("start"),count,DepositConvention.valueOf(f.getValue("convention")));CalculatorOutput(listOf(line("Gross maturity estimate",value),line("Total installments",total),line("Gross interest",value-total)),listOf(note,"First installment on the start date; subsequent installments at original-anchor plus whole months. Maturity after N months. Bank-specific RD formulas may differ."))}
        CalculatorKind.EMI,CalculatorKind.PREPAY->{val principal=amount("principal");val r=rate("rate");require(r>=BigDecimal.ZERO);val count=n();val emi=Contracts.emi(principal,r,count,currency.scale)
            if(kind==CalculatorKind.EMI){val schedule=Contracts.schedule(principal,r,count,currency.scale);CalculatorOutput(listOf(line("Monthly EMI",emi),line("Total interest",schedule.fold(BigDecimal.ZERO){a,p->a+p.interest}),line("Total principal and interest",schedule.fold(BigDecimal.ZERO){a,p->a+p.principal+p.interest})),listOf(note,"Fixed nominal annual interest / 12 on monthly reducing principal. Interest/EMI round HALF_EVEN at currency precision; final installment clears rounding residual. No floating resets or lender day-count rules assumed."),schedule)}
            else{val p=FinancialMath.prepay(principal,r,count,f.getValue("atMonth").toInt(),amount("extra"),amount("fee"),currency.scale);CalculatorOutput(listOf(line("Original monthly EMI",emi),line("Revised term in months",p.revised.size.toBigDecimal(),OutputKind.COUNT),line("Months saved",(count-p.revised.size).toBigDecimal(),OutputKind.COUNT),line("Interest saved after entered fee",p.interestSaved)),listOf(note,"Extra principal is applied after the selected scheduled installment. EMI stays unchanged; the term shortens. No legal fee exemption is inferred."),p.revised)}}
        CalculatorKind.RETIREMENT->{val years=f.getValue("years").toInt();require(years in 0..50);val spending=FinancialMath.growth(amount("monthly"),rate("inflation"),years*12);val corpus=FinancialMath.retirementCorpus(spending,rate("rate"),rate("inflation"),n());CalculatorOutput(listOf(line("First monthly spending at retirement",spending),line("Required corpus at retirement",corpus)),listOf(note,"Finite horizon; end-of-month withdrawals rise with monthly inflation. No pension income, taxes, bequest or lifelong adequacy is assumed."))}
        CalculatorKind.WITHDRAWAL->{val r=FinancialMath.withdrawal(amount("principal"),amount("monthly"),rate("rate"),rate("inflation"),n());CalculatorOutput(listOf(line("Remaining corpus",r.remaining),line("Total actually withdrawable",r.withdrawn),line("First underfunded month",r.exhaustedMonth?.toBigDecimal(),OutputKind.COUNT)),listOf(note,if(r.exhaustedMonth==null)"All modeled withdrawals funded for the selected horizon; this is not a lifetime guarantee." else "Final month is only partially funded. No negative corpus or invented borrowing."))}
        CalculatorKind.GOAL->CalculatorOutput(listOf(line("Required end-of-month contribution",FinancialMath.requiredMonthly(amount("target"),amount("principal"),rate("rate"),n()))),listOf(note,"Existing earmark grows at the same effective rate; target is a future nominal amount. Contributions are zero when already funded."))
        CalculatorKind.FX->{val source=DecimalInput.positive(f.getValue("sourceFx"));val target=DecimalInput.positive(f.getValue("targetFx"));val c=CurrencyCode.valueOf(f.getValue("targetCurrency"));require(currency!=CurrencyCode.INR || source.compareTo(BigDecimal.ONE)==0){"INR source reference must be 1."};require(c!=CurrencyCode.INR || target.compareTo(BigDecimal.ONE)==0){"INR target reference must be 1."};require(c!=currency || source.compareTo(target)==0){"Same-currency reference rates must match."};val d=date("start");require(d<=LocalDate.now());CalculatorOutput(listOf(line("Converted amount in $c",FinancialMath.convert(amount("principal"),source,target))),listOf("Manual reference dated ${dateLabel(d)}. Conversion = amount × source INR rate ÷ target INR rate. No spread, tax or execution price is inferred; no network data is fetched."))}
        CalculatorKind.REAL_RETURN->CalculatorOutput(listOf(if(f["rateMode"]=="real")line("Nominal annual return",FinancialMath.nominalRate(rate("rate"),rate("inflation")),OutputKind.PERCENT) else line("Real annual return",FinancialMath.realRate(rate("rate"),rate("inflation")),OutputKind.PERCENT)),listOf("Real = (1 + nominal) / (1 + inflation) − 1. Nominal = (1 + real) × (1 + inflation) − 1. Returns and inflation must exceed -100%. This is an estimate, not an investment recommendation."))
    }
}
