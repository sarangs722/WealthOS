package com.wealthos.finance

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class IncomeKind(val label:String) { SALARY("Salary"),RENT("Rent"),FREELANCE("Freelance / business"),PENSION("Pension"),INTEREST("Interest / distributions"),OTHER("Other income") }
data class IncomeSource(val id:UUID,val name:String,val kind:IncomeKind,val currency:CurrencyCode,val startedOn:LocalDate,
                        val endedOn:LocalDate?,val createdAt:Instant,val updatedAt:Instant) {
    init {require(name.isNotBlank() && name.length<=80);require(endedOn==null || endedOn>=startedOn)}
    fun active(month:YearMonth)=startedOn<=month.atEndOfMonth() && (endedOn==null || endedOn>=month.atDay(1))
}
data class IncomeRate(val id:UUID,val sourceId:UUID,val effectiveFrom:LocalDate,val monthlyNet:BigDecimal,val revision:Int,val recordedAt:Instant,val note:String)
data class IncomeRecord(val id:UUID,val sourceId:UUID,val month:YearMonth,val net:BigDecimal,val gross:BigDecimal?,val deductions:BigDecimal?,
                        val revision:Int,val recordedAt:Instant,val note:String)
enum class IncomeMode(val label:String) { MANUAL_TOTAL("Manual net-income total"),RECORDED_SOURCES("Use recorded monthly income sources") }
data class MonthlyCashFlow(val id:UUID,val month:YearMonth,val currency:CurrencyCode,val incomeMode:IncomeMode,val manualIncome:BigDecimal?,
    val essential:BigDecimal,val discretionary:BigDecimal,val premiums:BigDecimal,val loanPrincipal:BigDecimal,val loanInterest:BigDecimal,
    val otherOutflows:BigDecimal,val investments:BigDecimal,val incomeConfirmed:Boolean,val expensesConfirmed:Boolean,
    val revision:Int,val recordedAt:Instant,val note:String)
data class EmergencyTarget(val id:UUID,val date:LocalDate,val targetInr:BigDecimal,val revision:Int,val recordedAt:Instant,val note:String)
data class EmergencyAllocation(val id:UUID,val accountId:UUID?,val instrumentId:UUID?,val date:LocalDate,val nativeAmount:BigDecimal,
    val revision:Int,val recordedAt:Instant,val note:String) {
    init {require((accountId==null)!=(instrumentId==null)) {"Choose exactly one existing asset."}}
    val sourceKey:String get()=if(accountId!=null)"cash:$accountId" else "asset:$instrumentId"
}
enum class PolicyKind(val label:String) { TERM("Term insurance"),HEALTH("Health insurance") }
enum class CoverageScope(val label:String) { INDIVIDUAL("Individual"),FAMILY_FLOATER("Family floater (shared limit)") }
enum class PremiumFrequency(val label:String,val months:Int) { MONTHLY("Monthly",1),QUARTERLY("Quarterly",3),HALF_YEARLY("Half-yearly",6),YEARLY("Yearly",12),SINGLE("One-off",0) }
data class InsurancePolicy(val id:UUID,val name:String,val insurer:String,val kind:PolicyKind,val currency:CurrencyCode,val createdAt:Instant,val updatedAt:Instant) {
    init {require(name.isNotBlank() && name.length<=80 && insurer.length<=80)}
}
data class InsuranceTerms(val id:UUID,val policyId:UUID,val effectiveFrom:LocalDate,val validFrom:LocalDate,val validUntil:LocalDate,
    val cover:BigDecimal?,val premium:BigDecimal?,val frequency:PremiumFrequency,val anchorDue:LocalDate?,val scope:CoverageScope,
    val active:Boolean,val revision:Int,val recordedAt:Instant,val note:String,val lastPremiumDue:LocalDate?=null)
/** Total received by the insurer for one due installment, not an additional wallet transaction. */
data class PremiumSettlement(val id:UUID,val policyId:UUID,val dueOn:LocalDate,val paidOn:LocalDate,val amount:BigDecimal,
    val revision:Int,val recordedAt:Instant,val note:String)
data class HouseholdBook(val sources:List<IncomeSource> = emptyList(),val rates:List<IncomeRate> = emptyList(),val income:List<IncomeRecord> = emptyList(),
    val cashFlows:List<MonthlyCashFlow> = emptyList(),val emergencyTargets:List<EmergencyTarget> = emptyList(),val allocations:List<EmergencyAllocation> = emptyList(),
    val policies:List<InsurancePolicy> = emptyList(),val terms:List<InsuranceTerms> = emptyList(),val settlements:List<PremiumSettlement> = emptyList())

data class CashFlowResult(val summary:MonthlyCashFlow?,val income:BigDecimal?,val recordedIncome:BigDecimal,val missingSources:List<IncomeSource>,
    val livingExpenses:BigDecimal?,val obligations:BigDecimal?,val surplusBeforeInvestments:BigDecimal?,val remainingCash:BigDecimal?)
object CashFlows {
    fun income(book:HouseholdBook,month:YearMonth,currency:CurrencyCode):List<IncomeRecord> {
        val ids=book.sources.filter {it.currency==currency}.map {it.id}.toSet()
        return book.income.filter {it.month==month && it.sourceId in ids}.groupBy {it.sourceId}.values.map {rows->rows.maxBy {it.revision}}
    }
    fun at(book:HouseholdBook,month:YearMonth,currency:CurrencyCode):CashFlowResult {
        val summary=book.cashFlows.filter {it.month==month && it.currency==currency}.maxByOrNull {it.revision}
        val actual=income(book,month,currency)
        val sum=actual.fold(BigDecimal.ZERO) {a,r->a+r.net}
        val missing=book.sources.filter {it.currency==currency && it.active(month) && actual.none {r->r.sourceId==it.id}}
        val knownIncome=when {
            summary==null || !summary.incomeConfirmed->null
            summary.incomeMode==IncomeMode.MANUAL_TOTAL->summary.manualIncome
            missing.isNotEmpty()->null
            else->sum
        }
        val living=summary?.takeIf {it.expensesConfirmed}?.let {it.essential+it.discretionary+it.otherOutflows}
        val obligations=summary?.takeIf {it.expensesConfirmed}?.let {it.premiums+it.loanPrincipal+it.loanInterest}
        val surplus=if(knownIncome!=null && living!=null && obligations!=null)knownIncome-living-obligations else null
        return CashFlowResult(summary,knownIncome,sum,missing,living,obligations,surplus,surplus?.minus(summary!!.investments))
    }
    fun rate(book:HouseholdBook,source:UUID,date:LocalDate)=book.rates.filter {it.sourceId==source && it.effectiveFrom<=date}
        .maxWithOrNull(compareBy<IncomeRate> {it.effectiveFrom}.thenBy {it.revision})
}

data class ReserveSource(val key:String,val name:String,val currency:CurrencyCode,val available:BigDecimal?,val eligible:Boolean,val date:LocalDate?,val status:String)
data class ReserveLine(val allocation:EmergencyAllocation,val source:ReserveSource,val eligibleNative:BigDecimal?,val inr:BigDecimal?,val warning:String?)
data class EmergencyResult(val target:EmergencyTarget?,val lines:List<ReserveLine>,val allocatedInr:BigDecimal?,val knownInr:BigDecimal,
    val shortfall:BigDecimal?,val percentage:BigDecimal?,val monthsCovered:BigDecimal?,val referenceMonth:YearMonth?)
object EmergencyFunds {
    fun sources(book:BankBook,date:LocalDate):List<ReserveSource> {
        val cash=BankValuation.at(book,date).inputs.map {ReserveSource("cash:${it.account.id}",it.account.name,it.account.currency,it.value,true,it.observation?.effectiveDate,if(it.provisional)"Provisional cash" else "Observed cash")}
        val assets=book.portfolio.instruments.filter {it.category!=AssetCategory.LOAN}.map {i->
            val value=WealthValuation.asset(i,book.portfolio,date)
            ReserveSource("asset:${i.id}",i.name,i.currency,value.nativeValue,i.liquid,value.date,value.status)
        }
        return cash+assets
    }
    fun at(book:BankBook,date:LocalDate):EmergencyResult {
        val h=book.household
        val target=h.emergencyTargets.filter {it.date<=date}.maxWithOrNull(compareBy<EmergencyTarget> {it.date}.thenBy {it.revision})
        val available=sources(book,date).associateBy {it.key}
        val allocations=h.allocations.filter {it.date<=date}.groupBy {it.sourceKey}.values.map {it.maxWith(compareBy<EmergencyAllocation> {it.date}.thenBy {it.revision})}
        val lines=allocations.map {a->
            val source=requireNotNull(available[a.sourceKey]) {"Earmarked asset is missing."}
            val native=if(a.nativeAmount.signum()==0 || !source.eligible)BigDecimal.ZERO else source.available?.max(BigDecimal.ZERO)?.min(a.nativeAmount)
            val fx=if(source.currency==CurrencyCode.INR)BigDecimal.ONE else WealthValuation.rate(book.portfolio,source.currency,date)?.inrPerUnit
            val inr=if(native?.signum()==0)BigDecimal.ZERO else if(native!=null && fx!=null)native.multiply(fx,MC) else null
            val warning=when {
                !source.eligible && a.nativeAmount.signum()>0->"Source is no longer marked liquid; excluded"
                source.available==null && a.nativeAmount.signum()>0->"Source valuation unavailable"
                source.available!=null && a.nativeAmount>source.available.max(BigDecimal.ZERO)->"Earmark exceeds available value; progress is capped"
                inr==null->"Missing FX rate"
                source.date!=null && ChronoUnit.DAYS.between(source.date,date)>30->"Source valuation older than 30 days"
                source.currency!=CurrencyCode.INR && WealthValuation.rate(book.portfolio,source.currency,date)?.let {ChronoUnit.DAYS.between(it.date,date)>7}==true->"Applied FX older than 7 days"
                else->null
            }
            ReserveLine(a,source,native,inr,warning)
        }
        val known=lines.mapNotNull {it.inr}.fold(BigDecimal.ZERO,BigDecimal::add)
        val total=known.takeIf {lines.all {it.inr!=null}}
        val shortfall=if(target!=null && total!=null)(target.targetInr-total).max(BigDecimal.ZERO) else null
        val percentage=if(target!=null && total!=null)total.multiply(BigDecimal(100)).divide(target.targetInr,MC) else null
        val summary=h.cashFlows.filter {it.currency==CurrencyCode.INR && it.month<=YearMonth.from(date)}
            .maxWithOrNull(compareBy<MonthlyCashFlow> {it.month}.thenBy {it.revision})
        val essential=summary?.takeIf {it.expensesConfirmed}?.let {it.essential+it.premiums+it.loanPrincipal+it.loanInterest+it.otherOutflows}
        val covered=if(total!=null && essential!=null && essential.signum()>0)total.divide(essential,MC) else null
        return EmergencyResult(target,lines,total,known,shortfall,percentage,covered,summary?.month)
    }
}

data class PremiumDue(val terms:InsuranceTerms,val dueOn:LocalDate,val paid:BigDecimal,val outstanding:BigDecimal?)
data class PolicyView(val policy:InsurancePolicy,val terms:InsuranceTerms?,val status:String,val nextDue:PremiumDue?,val overdueCount:Int,val monthlyBudget:BigDecimal?)
object Insurance {
    fun latestTerms(book:HouseholdBook,policyId:UUID)=book.terms.filter {it.policyId==policyId}.groupBy {it.effectiveFrom}.values
        .map {it.maxBy {v->v.revision}}.sortedBy {it.effectiveFrom}
    fun settlements(book:HouseholdBook,policyId:UUID,through:LocalDate)=book.settlements.filter {it.policyId==policyId && it.paidOn<=through}
        .groupBy {it.dueOn}.values.map {it.maxBy {v->v.revision}}
    /** The explicit anchor is the first tracked due. Earlier unpaid premiums are never fabricated. */
    fun dues(book:HouseholdBook,policyId:UUID,asOf:LocalDate,horizon:LocalDate):List<PremiumDue> {
        val versions=latestTerms(book,policyId)
        val paid=settlements(book,policyId,asOf).associateBy {it.dueOn}
        return versions.flatMapIndexed {index,t ->
            val anchor=t.anchorDue
            if(!t.active || anchor==null || t.premium?.signum()==0)emptyList() else {
                val next=versions.getOrNull(index+1)?.effectiveFrom
                val max=t.validUntil.coerceAtMost(t.lastPremiumDue ?: t.validUntil).coerceAtMost(horizon).let {if(next!=null)it.coerceAtMost(next.minusDays(1)) else it}
                val min=if(index==0) t.validFrom else t.validFrom.coerceAtLeast(t.effectiveFrom)
                val result=mutableListOf<PremiumDue>();var n=0
                while(n<=1200) {
                    val due=if(t.frequency==PremiumFrequency.SINGLE)anchor else anchor.plusMonths(n.toLong()*t.frequency.months)
                    if(due>max)break
                    if(due>=min) {
                        val amount=paid[due]?.amount ?: BigDecimal.ZERO
                        val outstanding=t.premium?.minus(amount)?.max(BigDecimal.ZERO)
                        if(outstanding==null || outstanding.signum()>0)result+=PremiumDue(t,due,amount,outstanding)
                    }
                    if(t.frequency==PremiumFrequency.SINGLE)break
                    n++
                }
                result
            }
        }.sortedBy {it.dueOn}
    }
    fun view(book:HouseholdBook,policy:InsurancePolicy,date:LocalDate):PolicyView {
        val term=latestTerms(book,policy.id).lastOrNull {it.effectiveFrom<=date}
        val due=dues(book,policy.id,date,date.plusYears(1)).filter {term?.active!=false || it.terms.effectiveFrom>term.effectiveFrom}
        val status=when {
            term==null->"Planned / no terms effective yet"
            !term.active->"Marked inactive"
            date<term.validFrom->"Recorded cover period has not started"
            date>term.validUntil->"Recorded end date passed; confirm renewal"
            due.any {it.dueOn<date && it.outstanding!=null}->"Recorded premium overdue; confirm with insurer"
            due.any {it.dueOn<date && it.outstanding==null}->"Past due date; premium amount unknown"
            due.firstOrNull()?.dueOn?.let {it<=date.plusDays(30)}==true->"Premium due within 30 days"
            term.anchorDue==null && term.premium?.signum()!=0->"Next premium date unknown"
            else->"Within recorded period"
        }
        val budget=term?.takeIf {it.active && date>=it.validFrom && date<=it.validUntil}?.let {
            if(it.frequency==PremiumFrequency.SINGLE || it.lastPremiumDue?.let {last->date>last}==true)BigDecimal.ZERO else it.premium?.divide(it.frequency.months.toBigDecimal(),MC)
        }
        return PolicyView(policy,term,status,due.firstOrNull(),due.count {it.dueOn<date && it.outstanding!=null},budget)
    }
}

interface HouseholdRepository {
    suspend fun saveSource(source:IncomeSource,expectedMonthlyNet:BigDecimal?)
    suspend fun saveRate(rate:IncomeRate)
    suspend fun saveIncome(record:IncomeRecord)
    suspend fun saveCashFlow(summary:MonthlyCashFlow)
    suspend fun saveTarget(target:EmergencyTarget)
    suspend fun saveAllocation(allocation:EmergencyAllocation)
    suspend fun savePolicy(policy:InsurancePolicy,terms:InsuranceTerms)
    suspend fun saveSettlement(settlement:PremiumSettlement)
}
