package com.wealthos.finance

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ForecastInput(val name:String,val currency:CurrencyCode,val value:BigDecimal?,val date:LocalDate?,val status:String)
data class ForecastPoint(val month:Int,val date:LocalDate,val assets:BigDecimal,val liabilities:BigDecimal,val nominal:BigDecimal,val real:BigDecimal,
    val cash:BigDecimal,val contributions:BigDecimal,val salary:BigDecimal,val outflows:BigDecimal)
data class FundingGap(val month:Int,val currency:CurrencyCode,val amount:BigDecimal,val reason:String)
data class ForecastResult(val points:List<ForecastPoint>,val inputs:List<ForecastInput>,val warnings:List<String>,val unavailable:String?,val gap:FundingGap?) {
    val complete get()=unavailable==null && gap==null
}
object Forecasts {
    private val mc=DecimalMath.context
    fun validate(s:ForecastScenario) {
        require(s.name.isNotBlank() && s.name.length<=80 && s.note.length<=500 && s.months in 1..360 && s.asOf.year>=1900)
        listOf(s.inflation,s.cashReturn,s.investmentReturn,s.stepUp,s.salaryGrowth,s.expenseInflation).forEach {DecimalMath.rate(it)}
        listOf(s.monthlyContribution,s.salary,s.essential,s.discretionary,s.premiums).forEach {FinancialMath.nonnegative(it)}
    }
    fun calculate(book:BankBook,s:ForecastScenario):ForecastResult {
        validate(s)
        val warnings=mutableListOf("Estimate, not a prediction. No tax or fees beyond amounts explicitly modeled.",
            "Growth first; salary, outflows and contributions occur at each month end. Step-ups start in month 13.",
            "Native-currency cash funds native obligations. No automatic FX cash conversions or asset sales.")
        if(s.mode==ForecastMode.CONTRIBUTION)warnings+="Contributions and loan payments are assumed funded externally. Salary, living costs and premiums are outside this mode."
        val starting=WealthValuation.at(book,s.asOf)
        val inputs=starting.inputs.map {ForecastInput(it.account.name,it.account.currency,it.value,it.observation?.effectiveDate,if(it.provisional)"Provisional cash" else "Observed / carried cash")}+
            starting.assets.map {ForecastInput(it.instrument.name,it.instrument.currency,it.nativeValue,it.date,it.status)}
        fun absent(reason:String)=ForecastResult(emptyList(),inputs,warnings,reason,null)
        if(inputs.any {it.value==null})return absent("Starting balances/valuations are incomplete; update the listed inputs.")
        if(starting.staleAccounts>0 || starting.staleFx>0)warnings+="Starting observations or FX are stale; inspect their dates."
        val assetPlans=book.planning.assets.filter {it.scenarioId==s.id}.associateBy {it.instrumentId}
        if(starting.assets.any {it.instrument.id !in assetPlans})warnings+="Assets without a saved override use this scenario's general investment return and zero new contributions/payments."
        val fxPlans=book.planning.fx.filter {it.scenarioId==s.id}.associateBy {it.currency}
        val withdrawals=book.planning.withdrawals.filter {it.scenarioId==s.id}
        val cash=linkedMapOf<CurrencyCode,BigDecimal>()
        starting.inputs.forEach {cash[it.account.currency]=(cash[it.account.currency] ?: BigDecimal.ZERO)+it.value!!}
        cash.putIfAbsent(CurrencyCode.INR,BigDecimal.ZERO)
        val initialFx=CurrencyCode.entries.associateWith {if(it==CurrencyCode.INR)BigDecimal.ONE else WealthValuation.rate(book.portfolio,it,s.asOf)?.inrPerUnit}
        val currencies=(inputs.map {it.currency}+if(s.includeGoals)book.planning.latestGoals().filter {it.active}.map {it.currency} else emptyList()).toSet()
        if(currencies.any {initialFx[it]==null})return absent("Starting FX is missing for a modeled currency.")
        if(currencies.any {it!=CurrencyCode.INR && fxPlans[it]==null})warnings+="Unspecified foreign-exchange changes are assumed zero; starting reference FX is held constant."
        val fxMonthly=fxPlans.mapValues {BigDecimal.ONE+DecimalMath.monthly(it.value.annualChange)}
        fun fx(currency:CurrencyCode,n:Int)=requireNotNull(initialFx[currency]).multiply((fxMonthly[currency] ?: BigDecimal.ONE).pow(n,mc),mc)
        fun move(currency:CurrencyCode,amount:BigDecimal){cash[currency]=(cash[currency] ?: BigDecimal.ZERO)+amount}
        data class State(val instrument:Instrument,var value:BigDecimal,val annual:BigDecimal,val monthly:BigDecimal,val amount:BigDecimal,
            val contract:FinancialContract?,val adjustment:BigDecimal,var matured:Boolean=false,val events:MutableList<ContractEvent> = mutableListOf())
        val assets=starting.assets.map {v->val i=v.instrument;val a=assetPlans[i.id];val c=book.portfolio.contracts.find {it.instrumentId==i.id}
            val rate=a?.annualReturn ?: s.investmentReturn
            if(i.tracking in listOf(TrackingMode.LOAN,TrackingMode.DEPOSIT) && c==null)return absent("${i.name}: contract information is missing.")
            val past=book.portfolio.contractEvents.filter {it.instrumentId==i.id && it.date<=s.asOf}
            val contractual=if(i.tracking==TrackingMode.DEPOSIT)c?.let {Contracts.depositValue(it,past,s.asOf)} else null
            State(i,v.nativeValue!!,rate,BigDecimal.ONE+DecimalMath.monthly(rate),a?.monthlyAmount ?: BigDecimal.ZERO,c,
                if(contractual!=null)v.nativeValue-contractual else BigDecimal.ZERO,
                matured=i.tracking==TrackingMode.DEPOSIT && past.any {it.kind==ContractEventKind.MATURITY},events=past.toMutableList())}
        if(assets.any {it.instrument.tracking==TrackingMode.DEPOSIT})warnings+="Deposits project contractual increments from the starting value; future interest is retained until maturity. RD installments require explicit planned amounts. Actual bank rules/tax may differ."
        if(assets.any {it.instrument.tracking==TrackingMode.LOAN})warnings+="Loans use recorded fixed nominal rates / 12, monthly interest rounded to currency precision and explicit planned payments. Variable-rate resets and arrears are not inferred."
        val annualFactors=mapOf("cash" to (BigDecimal.ONE+DecimalMath.monthly(s.cashReturn)),"expense" to (BigDecimal.ONE+DecimalMath.monthly(s.expenseInflation)))
        val inflationMonthly=BigDecimal.ONE+DecimalMath.monthly(s.inflation)
        val goals=if(s.includeGoals)book.planning.latestGoals().filter {it.active} else emptyList()
        if(goals.any {it.due<=s.asOf})return absent("A selected goal is due on/before the starting date. Revise goals or exclude goal withdrawals.")
        val plannedMonthly=BigDecimal.ONE+DecimalMath.monthly(s.investmentReturn)
        var planned=BigDecimal.ZERO;val points=mutableListOf<ForecastPoint>();var gap:FundingGap?=null
        fun point(n:Int,date:LocalDate,contribution:BigDecimal=BigDecimal.ZERO,salary:BigDecimal=BigDecimal.ZERO,outflow:BigDecimal=BigDecimal.ZERO):ForecastPoint {
            val positiveCash=cash.entries.filter {it.value.signum()>0}.fold(BigDecimal.ZERO){a,e->a+e.value.multiply(fx(e.key,n),mc)}
            val cashDebt=cash.entries.filter {it.value.signum()<0}.fold(BigDecimal.ZERO){a,e->a-e.value.multiply(fx(e.key,n),mc)}
            val owned=assets.filter {it.instrument.tracking!=TrackingMode.LOAN}.fold(planned){a,v->a+v.value.multiply(fx(v.instrument.currency,n),mc)}+positiveCash
            val debt=assets.filter {it.instrument.tracking==TrackingMode.LOAN}.fold(cashDebt){a,v->a+v.value.multiply(fx(v.instrument.currency,n),mc)}
            val nominal=owned-debt
            return ForecastPoint(n,date,owned,debt,nominal,nominal.divide(inflationMonthly.pow(n,mc),mc),positiveCash-cashDebt,contribution,salary,outflow)
        }
        points+=point(0,s.asOf)
        for(n in 1..s.months){val date=s.asOf.plusMonths(n.toLong());val previous=s.asOf.plusMonths((n-1).toLong())
            cash.keys.toList().forEach {currency->val v=cash[currency]!!;if(v.signum()>0)cash[currency]=v.multiply(annualFactors.getValue("cash"),mc)}
            planned=planned.multiply(plannedMonthly,mc)
            val step=(BigDecimal.ONE+s.stepUp).pow((n-1)/12,mc)
            var contributions=BigDecimal.ZERO;var outflow=BigDecimal.ZERO
            val salary=if(s.mode==ForecastMode.CASH_FLOW)s.salary.multiply((BigDecimal.ONE+s.salaryGrowth).pow((n-1)/12,mc),mc) else BigDecimal.ZERO
            if(s.mode==ForecastMode.CASH_FLOW){move(CurrencyCode.INR,salary)
                val spending=(s.essential+s.discretionary).multiply(annualFactors.getValue("expense").pow(n-1,mc),mc)+s.premiums
                move(CurrencyCode.INR,-spending);outflow+=spending}
            for(v in assets){val i=v.instrument;val c=v.contract
                when {
                    i.tracking==TrackingMode.LOAN->{val interest=v.value.multiply(c!!.annualRate,mc).divide(BigDecimal(12),i.currency.scale,java.math.RoundingMode.HALF_EVEN)
                        val payment=v.amount.min(v.value+interest);v.value=(v.value+interest-payment).max(BigDecimal.ZERO)
                        if(s.mode==ForecastMode.CASH_FLOW){move(i.currency,-payment);outflow+=payment.multiply(fx(i.currency,n),mc)}
                        if(payment<interest && v.value.signum()>0 && n==1)warnings+="${i.name}: planned payment is below interest; estimated liability grows."}
                    i.tracking==TrackingMode.DEPOSIT && !v.matured->{
                        v.value=(Contracts.depositValue(c!!,v.events,date)!!+v.adjustment).max(BigDecimal.ZERO)
                        if(date>=c.end){v.matured=true;if(!s.reinvestDeposits){move(i.currency,v.value);v.value=BigDecimal.ZERO}}
                        else if(v.amount.signum()>0 && i.category==AssetCategory.RD){val amount=v.amount.multiply(step,mc)
                            v.events+=ContractEvent(UUID(0,n.toLong()),i.id,ContractEventKind.CONTRIBUTION,date,100000+n,amount,recordedAt=s.recordedAt)
                            v.value+=amount;contributions+=amount.multiply(fx(i.currency,n),mc);if(s.mode==ForecastMode.CASH_FLOW)move(i.currency,-amount)}
                    }
                    else->{v.value=v.value.multiply(v.monthly,mc)
                        // Mature deposits do not receive further scheduled RD/FD contributions.
                        if(i.tracking!=TrackingMode.DEPOSIT){val amount=v.amount.multiply(step,mc);v.value+=amount;contributions+=amount.multiply(fx(i.currency,n),mc)
                            if(s.mode==ForecastMode.CASH_FLOW)move(i.currency,-amount)}}
                }
            }
            val monthly=s.monthlyContribution.multiply(step,mc);planned+=monthly;contributions+=monthly
            if(s.mode==ForecastMode.CASH_FLOW)move(CurrencyCode.INR,-monthly)
            fun withdraw(source:UUID?,amount:BigDecimal,label:String) {
                if(source==null){move(CurrencyCode.INR,-amount);outflow+=amount} else {
                    val asset=assets.find {it.instrument.id==source}
                    if(asset==null || asset.instrument.tracking in listOf(TrackingMode.LOAN,TrackingMode.DEPOSIT)) {gap=FundingGap(n,CurrencyCode.INR,amount,"$label: choose a non-contract asset or cash.");return}
                    if(asset.value<amount){gap=FundingGap(n,asset.instrument.currency,amount-asset.value,"$label: asset withdrawal is not fully funded.");return}
                    asset.value-=amount;outflow+=amount.multiply(fx(asset.instrument.currency,n),mc)
                }
            }
            withdrawals.filter {it.month==n}.forEach {withdraw(it.instrumentId,it.amount,it.label)}
            goals.filter {it.due>previous && it.due<=date}.forEach {g->
                val targetMonths=ChronoUnit.MONTHS.between(java.time.YearMonth.from(g.basisDate),java.time.YearMonth.from(g.due)).toInt().coerceAtLeast(0)
                val target=if(g.targetInTodayMoney)FinancialMath.growth(g.target,g.inflation,targetMonths) else g.target
                move(g.currency,-target);outflow+=target.multiply(fx(g.currency,n),mc)
            }
            if(gap==null)cash.entries.firstOrNull {it.value.signum()<0}?.let {gap=FundingGap(n,it.key,-it.value,"Cash obligations/contributions exceed modeled cash. No borrowing or asset sale was assumed.")}
            points+=point(n,date,contributions,salary,outflow)
            if(gap!=null)break
        }
        return ForecastResult(points,inputs,warnings.distinct(),null,gap)
    }
}
