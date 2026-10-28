package com.wealthos.database

import androidx.room.withTransaction
import com.wealthos.finance.*
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class RoomHouseholdRepository(private val db:BankDatabase,private val clock:Clock=Clock.systemDefaultZone()):HouseholdRepository {
    private val dao=db.householdDao()
    override suspend fun saveSource(source:IncomeSource,expectedMonthlyNet:BigDecimal?):Unit=db.withTransaction {
        day(source.startedOn,true);source.endedOn?.let {day(it,true)}
        val old=dao.sources().find {it.id==source.id.toString()}
        require(old==null || expectedMonthlyNet==null) {"Use a dated income-rate revision to change expected income."}
        if(old!=null)require(old.currency==source.currency.name && old.kind==source.kind.name && old.startedOn==source.startedOn.toString()) {"Income currency, kind and first-known date are immutable."}
        val actual=source.copy(name=source.name.trim(),createdAt=old?.let {java.time.Instant.parse(it.createdAt)} ?: clock.instant(),updatedAt=clock.instant())
        if(old==null)dao.insert(actual.entity()) else dao.update(actual.entity())
        audit(source.id,"SAVE_INCOME_SOURCE",old?.toString(),actual.toString())
        if(old==null && expectedMonthlyNet!=null) {
            money(expectedMonthlyNet,source.currency)
            val rate=IncomeRate(UUID.randomUUID(),source.id,source.startedOn,expectedMonthlyNet,1,clock.instant(),"Initial expected monthly net; not actual income")
            dao.insert(rate.entity());audit(rate.id,"INCOME_RATE",null,rate.toString())
        }
    }
    override suspend fun saveRate(rate:IncomeRate):Unit=db.withTransaction {
        val source=source(rate.sourceId);day(rate.effectiveFrom,true);require(rate.effectiveFrom>=source.startedOn);money(rate.monthlyNet,source.currency)
        val revision=dao.rates().filter {it.sourceId==rate.sourceId.toString() && it.effectiveFrom==rate.effectiveFrom.toString()}.maxOfOrNull {it.revision} ?: 0
        note(rate.note,revision>0)
        val actual=rate.copy(revision=revision+1,recordedAt=clock.instant())
        dao.insert(actual.entity());audit(actual.id,"INCOME_RATE",null,actual.toString())
    }
    override suspend fun saveIncome(record:IncomeRecord):Unit=db.withTransaction {
        val source=source(record.sourceId);month(record.month);require(record.month>=YearMonth.from(source.startedOn)) {"Income month precedes the first-known source date."}
        money(record.net,source.currency)
        require((record.gross==null)==(record.deductions==null)) {"Enter both gross pay and deductions, or leave both unknown."}
        if(record.gross!=null && record.deductions!=null) {
            require(source.kind==IncomeKind.SALARY) {"Payslip gross/deductions apply to salary sources."}
            money(record.gross!!,source.currency);money(record.deductions!!,source.currency)
            require((record.gross!!-record.deductions!!).compareTo(record.net)==0) {"Gross minus deductions must equal actual net income."}
        }
        val revision=dao.income().filter {it.sourceId==record.sourceId.toString() && it.month==record.month.toString()}.maxOfOrNull {it.revision} ?: 0
        note(record.note,revision>0)
        val actual=record.copy(revision=revision+1,recordedAt=clock.instant())
        dao.insert(actual.entity());audit(actual.id,"MONTHLY_INCOME",null,actual.toString())
    }
    override suspend fun saveCashFlow(summary:MonthlyCashFlow):Unit=db.withTransaction {
        month(summary.month)
        listOf(summary.essential,summary.discretionary,summary.premiums,summary.loanPrincipal,summary.loanInterest,summary.otherOutflows,summary.investments).forEach {money(it,summary.currency)}
        if(summary.incomeMode==IncomeMode.MANUAL_TOTAL)money(requireNotNull(summary.manualIncome) {"Enter actual net income, including an explicit zero if none."},summary.currency)
        else require(summary.manualIncome==null) {"Source-derived income must not also contain a manual total."}
        val revision=dao.cashFlows().filter {it.month==summary.month.toString() && it.currency==summary.currency.name}.maxOfOrNull {it.revision} ?: 0
        note(summary.note,revision>0)
        val actual=summary.copy(revision=revision+1,recordedAt=clock.instant())
        dao.insert(actual.entity());audit(actual.id,"MONTHLY_CASH_FLOW",null,actual.toString())
    }
    override suspend fun saveTarget(target:EmergencyTarget):Unit=db.withTransaction {
        day(target.date);money(target.targetInr,CurrencyCode.INR);require(target.targetInr>BigDecimal.ZERO) {"Emergency target must be positive."}
        val revision=dao.targets().filter {it.date==target.date.toString()}.maxOfOrNull {it.revision} ?: 0
        note(target.note,revision>0)
        val actual=target.copy(revision=revision+1,recordedAt=clock.instant())
        dao.insert(actual.entity());audit(actual.id,"EMERGENCY_TARGET",null,actual.toString())
    }
    override suspend fun saveAllocation(allocation:EmergencyAllocation):Unit=db.withTransaction {
        day(allocation.date)
        val book=db.readBook();val available=requireNotNull(EmergencyFunds.sources(book,allocation.date).find {it.key==allocation.sourceKey}) {"Select an existing asset."}
        money(allocation.nativeAmount,available.currency)
        if(allocation.nativeAmount.signum()>0) {
            require(available.eligible) {"Mark the asset liquid before earmarking it."}
            val value=requireNotNull(available.available) {"Enter a known asset valuation before allocating it."}
            require(allocation.nativeAmount<=value.max(BigDecimal.ZERO)) {"Earmark cannot exceed the known available native value."}
        }
        val revision=book.household.allocations.filter {it.sourceKey==allocation.sourceKey && it.date==allocation.date}.maxOfOrNull {it.revision} ?: 0
        note(allocation.note,revision>0)
        val actual=allocation.copy(revision=revision+1,recordedAt=clock.instant())
        dao.insert(actual.entity());audit(actual.id,"EMERGENCY_EARMARK",null,actual.toString())
    }
    override suspend fun savePolicy(policy:InsurancePolicy,terms:InsuranceTerms):Unit=db.withTransaction {
        require(terms.policyId==policy.id)
        val old=dao.policies().find {it.id==policy.id.toString()}
        if(old!=null)require(old.kind==policy.kind.name && old.currency==policy.currency.name) {"Insurance kind and native currency are immutable."}
        day(terms.effectiveFrom,true);day(terms.validFrom,true);day(terms.validUntil,true)
        require(terms.validUntil>=terms.validFrom && terms.validUntil<=terms.validFrom.plusYears(100)) {"Enter a recorded policy period within 100 years."}
        terms.cover?.let {money(it,policy.currency);require(it>BigDecimal.ZERO) {"Cover must be positive, or left unknown."}}
        terms.premium?.let {money(it,policy.currency)}
        require(policy.kind==PolicyKind.HEALTH || terms.scope==CoverageScope.INDIVIDUAL) {"Family-floater scope applies to health policies."}
        terms.anchorDue?.let {day(it,true);require(it>=terms.validFrom && it<=terms.validUntil) {"Tracked premium date must fall within the recorded policy period."}}
        terms.lastPremiumDue?.let {day(it,true);require(it>=terms.validFrom && it<=terms.validUntil && (terms.anchorDue==null || it>=terms.anchorDue)) {"Last premium date must be within cover dates and not before the tracking anchor."}}
        val revision=dao.terms().filter {it.policyId==policy.id.toString() && it.effectiveFrom==terms.effectiveFrom.toString()}.maxOfOrNull {it.revision} ?: 0
        note(terms.note,revision>0)
        val actualPolicy=policy.copy(name=policy.name.trim(),insurer=policy.insurer.trim(),createdAt=old?.let {java.time.Instant.parse(it.createdAt)} ?: clock.instant(),updatedAt=clock.instant())
        if(old==null)dao.insert(actualPolicy.entity()) else dao.update(actualPolicy.entity())
        val actualTerms=terms.copy(revision=revision+1,recordedAt=clock.instant())
        dao.insert(actualTerms.entity())
        audit(policy.id,"SAVE_INSURANCE_POLICY",old?.toString(),actualPolicy.toString());audit(actualTerms.id,"INSURANCE_TERMS",null,actualTerms.toString())
    }
    override suspend fun saveSettlement(settlement:PremiumSettlement):Unit=db.withTransaction {
        val policy=requireNotNull(dao.policies().find {it.id==settlement.policyId.toString()}) {"Policy is unavailable."}.domain()
        day(settlement.paidOn);day(settlement.dueOn,true);money(settlement.amount,policy.currency)
        require(dao.terms().any {it.policyId==policy.id.toString() && settlement.dueOn>=LocalDate.parse(it.validFrom) && settlement.dueOn<=LocalDate.parse(it.validUntil)}) {"Due date is outside every recorded policy period."}
        val revision=dao.settlements().filter {it.policyId==policy.id.toString() && it.dueOn==settlement.dueOn.toString()}.maxOfOrNull {it.revision} ?: 0
        note(settlement.note,revision>0)
        val actual=settlement.copy(revision=revision+1,recordedAt=clock.instant())
        dao.insert(actual.entity());audit(actual.id,"PREMIUM_SETTLEMENT",null,actual.toString())
    }
    private suspend fun source(id:UUID)=requireNotNull(dao.sources().find {it.id==id.toString()}) {"Income source is unavailable."}.domain()
    private fun money(value:BigDecimal,currency:CurrencyCode) {Amounts.parse(value.toPlainString(),currency);require(value>=BigDecimal.ZERO) {"Amounts cannot be negative."}}
    private fun month(value:YearMonth) {require(value.year>=1900 && value<=YearMonth.now(clock)) {"Actual monthly records cannot be future-dated or before 1900."}}
    private fun day(value:LocalDate,future:Boolean=false) {require(value.year>=1900 && value<=LocalDate.now(clock).let {if(future)it.plusYears(100) else it}) {"Use a valid effective date; actual observations cannot be future-dated."}}
    private fun note(value:String,required:Boolean=false) {require(value.length<=500 && (!required || value.isNotBlank())) {"Explain this correction (up to 500 characters)."}}
    private suspend fun audit(id:UUID,action:String,before:String?,after:String)=db.bankDao().insertAudit(AuditEntity(UUID.randomUUID().toString(),id.toString(),action,clock.instant().toString(),before,after))
}
