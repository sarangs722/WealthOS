package com.wealthos.finance

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Decimal transcendental approximations, 160 significant digits, including guard precision for large horizons. */
object DecimalMath {
    val context=MathContext(160,RoundingMode.HALF_EVEN)
    private val epsilon=BigDecimal("1E-162")
    private val two=BigDecimal(2)
    fun exp(value:BigDecimal):BigDecimal {
        require(value.abs()<=BigDecimal(4000)) {"Exponent exceeds the supported calculation range."}
        var x=value;var squares=0
        while(x.abs()>BigDecimal("0.1")){x=x.divide(two,context);squares++}
        var term=BigDecimal.ONE;var sum=term;var n=1
        do {term=term.multiply(x,context).divide(n.toBigDecimal(),context);sum=sum.add(term,context);n++}while(term.abs()>epsilon && n<200)
        require(n<200) {"Exponential did not converge."}
        repeat(squares){sum=sum.multiply(sum,context)}
        return sum
    }
    fun ln(value:BigDecimal):BigDecimal {
        require(value>BigDecimal.ZERO) {"Logarithm requires a positive value."}
        var x=value;var roots=0
        while((x<BigDecimal("0.9") || x>BigDecimal("1.1")) && roots<32){x=sqrt(x);roots++}
        require(roots<32) {"Logarithm exceeds the supported range."}
        val z=(x-BigDecimal.ONE).divide(x+BigDecimal.ONE,context);val z2=z.multiply(z,context)
        var power=z;var sum=z;var n=3;var add:BigDecimal
        do {power=power.multiply(z2,context);add=power.divide(n.toBigDecimal(),context);sum=sum.add(add,context);n+=2}while(add.abs()>epsilon && n<1000)
        require(n<1000) {"Logarithm did not converge."}
        return sum.multiply(two.pow(roots+1),context)
    }
    fun pow(base:BigDecimal,exponent:BigDecimal):BigDecimal {
        require(base>BigDecimal.ZERO) {"Growth base must be positive."}
        if(exponent.signum()==0 || base.compareTo(BigDecimal.ONE)==0)return BigDecimal.ONE
        return exp(ln(base).multiply(exponent,context))
    }
    // java.math.BigDecimal.sqrt requires Android API 33; use bounded Newton iteration on API 29 too.
    fun sqrt(value:BigDecimal):BigDecimal {
        require(value>=BigDecimal.ZERO);if(value.signum()==0)return value
        var x=BigDecimal.ONE.scaleByPowerOfTen((value.precision()-value.scale()+1)/2)
        repeat(200){val next=(x+value.divide(x,context)).divide(two,context)
            if((next-x).abs()<=next.abs().multiply(BigDecimal("1E-158"),context))return next
            x=next}
        throw IllegalArgumentException("Square root did not converge.")
    }
    fun rate(annual:BigDecimal):BigDecimal {
        require(annual>BigDecimal("-1") && annual<=BigDecimal(10)) {"Annual rate must be greater than -100% and at most 1000%."}
        return annual
    }
    fun monthly(annual:BigDecimal)=pow(BigDecimal.ONE+rate(annual),BigDecimal.ONE.divide(BigDecimal(12),context))-BigDecimal.ONE
}

data class DatedCashFlow(val date:LocalDate,val amount:BigDecimal)
data class RateResult(val rate:BigDecimal?,val status:String,val flows:List<DatedCashFlow> = emptyList())
data class GrowthResult(val value:BigDecimal,val contributions:BigDecimal) {val gain get()=value-contributions}
data class WithdrawalResult(val remaining:BigDecimal,val withdrawn:BigDecimal,val exhaustedMonth:Int?)
data class PrepaymentResult(val original:List<Contracts.Payment>,val revised:List<Contracts.Payment>,val fee:BigDecimal) {
    val interestSaved get()=original.fold(BigDecimal.ZERO){a,p->a+p.interest}-revised.fold(BigDecimal.ZERO){a,p->a+p.interest}-fee
}
object FinancialMath {
    private val mc=DecimalMath.context
    fun nonnegative(value:BigDecimal):BigDecimal {require(value>=BigDecimal.ZERO && value.precision()-value.scale()<=24) {"Use a nonnegative amount with at most 24 integer digits."};return value}
    fun months(value:Int){require(value in 1..1200) {"Horizon must be between 1 and 1200 months."}}
    fun factor(annual:BigDecimal,months:Int):BigDecimal {require(months in 0..1200);return DecimalMath.pow(BigDecimal.ONE+DecimalMath.rate(annual),months.toBigDecimal().divide(BigDecimal(12),mc))}
    fun growth(principal:BigDecimal,annual:BigDecimal,months:Int)=nonnegative(principal).multiply(factor(annual,months),mc)
    fun sip(monthly:BigDecimal,annual:BigDecimal,months:Int,stepUp:BigDecimal=BigDecimal.ZERO,beginning:Boolean=false):GrowthResult {
        nonnegative(monthly);months(months);DecimalMath.rate(stepUp)
        val f=BigDecimal.ONE+DecimalMath.monthly(annual);var value=BigDecimal.ZERO;var total=BigDecimal.ZERO
        repeat(months){i->val payment=monthly.multiply((BigDecimal.ONE+stepUp).pow(i/12,mc),mc);total+=payment
            value=if(beginning)(value+payment).multiply(f,mc) else value.multiply(f,mc)+payment}
        return GrowthResult(value,total)
    }
    fun requiredMonthly(target:BigDecimal,existing:BigDecimal,annual:BigDecimal,months:Int,beginning:Boolean=false):BigDecimal {
        nonnegative(target);nonnegative(existing);months(months)
        val short=(target-growth(existing,annual,months)).max(BigDecimal.ZERO)
        return short.divide(sip(BigDecimal.ONE,annual,months,beginning=beginning).value,mc)
    }
    fun realRate(nominal:BigDecimal,inflation:BigDecimal)=(BigDecimal.ONE+DecimalMath.rate(nominal)).divide(BigDecimal.ONE+DecimalMath.rate(inflation),mc)-BigDecimal.ONE
    fun nominalRate(real:BigDecimal,inflation:BigDecimal)=(BigDecimal.ONE+DecimalMath.rate(real)).multiply(BigDecimal.ONE+DecimalMath.rate(inflation),mc)-BigDecimal.ONE
    fun convert(amount:BigDecimal,sourceInr:BigDecimal,targetInr:BigDecimal):BigDecimal {nonnegative(amount);require(sourceInr>BigDecimal.ZERO && targetInr>BigDecimal.ZERO);return amount.multiply(sourceInr,mc).divide(targetInr,mc)}
    fun cagr(initial:BigDecimal,final:BigDecimal,start:LocalDate,end:LocalDate):RateResult {
        if(initial<=BigDecimal.ZERO || final<BigDecimal.ZERO || end<=start)return RateResult(null,"CAGR requires positive starting value, nonnegative ending value and increasing dates.")
        if(final.signum()==0)return RateResult(BigDecimal("-1"),"ACT/365 annualised; no intervening cash flows assumed")
        return RateResult(DecimalMath.pow(final.divide(initial,mc),BigDecimal(365).divide(ChronoUnit.DAYS.between(start,end).toBigDecimal(),mc))-BigDecimal.ONE,"ACT/365 annualised; no intervening cash flows assumed")
    }
    /** Uniqueness is established for one chronological sign change. Other patterns are explicitly unavailable. */
    fun xirr(input:List<DatedCashFlow>,maxIterations:Int=220):RateResult {
        require(input.size<=2000 && maxIterations in 0..300)
        val flows=input.groupBy {it.date}.map {(date,rows)->DatedCashFlow(date,rows.fold(BigDecimal.ZERO){a,r->a+r.amount})}.filter {it.amount.signum()!=0}.sortedBy {it.date}
        fun unavailable(reason:String)=RateResult(null,reason,flows)
        if(flows.size<2 || flows.none {it.amount.signum()<0} || flows.none {it.amount.signum()>0})return unavailable("XIRR needs positive and negative cash flows on different dates.")
        if(ChronoUnit.DAYS.between(flows.first().date,flows.last().date)>36525)return unavailable("XIRR supports at most 100 years of cash-flow history.")
        if(flows.zipWithNext().count {(a,b)->a.amount.signum()!=b.amount.signum()}>1)return unavailable("Multiple sign changes: a unique XIRR is not established; multiple roots may exist.")
        val years=flows.map {ChronoUnit.DAYS.between(flows.first().date,it.date).toBigDecimal().divide(BigDecimal(365),mc)}
        val scale=flows.fold(BigDecimal.ZERO){a,f->a+f.amount.abs()}
        fun npv(logBase:BigDecimal)=flows.indices.fold(BigDecimal.ZERO){sum,i->sum+flows[i].amount.divide(scale,mc).multiply(DecimalMath.exp(-logBase.multiply(years[i],mc)),mc)}
        // Solve in ln(1+r); bounded range exp(-20)-1 through exp(20)-1, with no crossing of r=-1.
        var lo=BigDecimal(-20);var hi=BigDecimal(20);var left=npv(lo);val right=npv(hi)
        if(left.signum()==right.signum())return unavailable("No root bracketed in the supported rate range.")
        repeat(maxIterations){val mid=(lo+hi).divide(BigDecimal(2),mc);val value=npv(mid)
            if(value.abs()<BigDecimal("1E-26") || (hi-lo).abs()<BigDecimal("1E-25"))return RateResult(DecimalMath.exp(mid)-BigDecimal.ONE,"ACT/365 XIRR · unique one-sign-change pattern",flows)
            if(value.signum()==left.signum()){lo=mid;left=value}else hi=mid}
        return unavailable("XIRR did not converge within the iteration limit.")
    }
    fun withdrawal(initial:BigDecimal,monthly:BigDecimal,annual:BigDecimal,inflation:BigDecimal,months:Int):WithdrawalResult {
        nonnegative(initial);nonnegative(monthly);months(months)
        var value=initial;var withdrawn=BigDecimal.ZERO;val f=BigDecimal.ONE+DecimalMath.monthly(annual);val expense=BigDecimal.ONE+DecimalMath.monthly(inflation)
        var payment=monthly
        repeat(months){i->value=value.multiply(f,mc);if(payment>value){withdrawn+=value;return WithdrawalResult(BigDecimal.ZERO,withdrawn,i+1)};value-=payment;withdrawn+=payment;payment=payment.multiply(expense,mc)}
        return WithdrawalResult(value,withdrawn,null)
    }
    fun retirementCorpus(monthlyAtRetirement:BigDecimal,annual:BigDecimal,inflation:BigDecimal,months:Int):BigDecimal {
        nonnegative(monthlyAtRetirement);months(months)
        val f=BigDecimal.ONE+DecimalMath.monthly(annual);val e=BigDecimal.ONE+DecimalMath.monthly(inflation)
        var expense=monthlyAtRetirement;var discount=f;var total=BigDecimal.ZERO
        repeat(months){total+=expense.divide(discount,mc);expense=expense.multiply(e,mc);discount=discount.multiply(f,mc)}
        return total
    }
    fun rd(monthly:BigDecimal,annualNominal:BigDecimal,start:LocalDate,months:Int,convention:DepositConvention):BigDecimal {
        nonnegative(monthly);months(months);require(annualNominal>=BigDecimal.ZERO && annualNominal<=BigDecimal(10))
        val end=start.plusMonths(months.toLong())
        return (0 until months).fold(BigDecimal.ZERO){sum,i->sum+Contracts.accrued(monthly,annualNominal,start.plusMonths(i.toLong()),end,convention)}
    }
    fun prepay(principal:BigDecimal,annualNominal:BigDecimal,months:Int,atMonth:Int,extra:BigDecimal,fee:BigDecimal=BigDecimal.ZERO,scale:Int=2):PrepaymentResult {
        nonnegative(principal);nonnegative(extra);nonnegative(fee);months(months);require(atMonth in 1..months && annualNominal>=BigDecimal.ZERO && annualNominal<=BigDecimal(10))
        val original=Contracts.schedule(principal,annualNominal,months,scale);val emi=Contracts.emi(principal,annualNominal,months,scale)
        var balance=principal;val result=mutableListOf<Contracts.Payment>()
        for(n in 1..months){if(balance.signum()==0)break
            val opening=balance;val interest=balance.multiply(annualNominal,mc).divide(BigDecimal(12),scale,RoundingMode.HALF_EVEN)
            require(emi>=interest || n==months) {"Rounded payment does not amortise this loan."}
            var capital=if(n==months)balance else (emi-interest).max(BigDecimal.ZERO).min(balance)
            if(n==atMonth)capital=(capital+extra).min(balance)
            balance-=capital;result+=Contracts.Payment(n,opening,capital,interest,balance)
        }
        return PrepaymentResult(original,result,fee)
    }
}
