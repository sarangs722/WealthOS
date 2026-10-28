package com.wealthos.finance

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class HouseholdTest {
    private val date=LocalDate.of(2026,10,15);private val month=YearMonth.from(date);private val now=Instant.EPOCH
    private val source=IncomeSource(UUID(0,1),"Salary",IncomeKind.SALARY,CurrencyCode.INR,date.withDayOfMonth(1),null,now,now)
    private val policy=InsurancePolicy(UUID(0,2),"Cover","Example insurer",PolicyKind.TERM,CurrencyCode.INR,now,now)
    private val bank=BankAccount(UUID(0,3),"Reserve cash","Bank",CurrencyCode.INR,AccountKind.SAVINGS,now)
    private fun number(expected:String,actual:BigDecimal?)=assertEquals(0,expected.toBigDecimal().compareTo(actual!!))
    private fun summary(mode:IncomeMode=IncomeMode.MANUAL_TOTAL)=MonthlyCashFlow(UUID(1,1),month,CurrencyCode.INR,mode,"100000".toBigDecimal().takeIf {mode==IncomeMode.MANUAL_TOTAL},
        "20000".toBigDecimal(),"5000".toBigDecimal(),"1000".toBigDecimal(),"8000".toBigDecimal(),"2000".toBigDecimal(),"4000".toBigDecimal(),"10000".toBigDecimal(),true,true,1,now,"")
    private fun record(value:String="100000",revision:Int=1)=IncomeRecord(UUID(2,revision.toLong()),source.id,month,value.toBigDecimal(),null,null,revision,now,"")
    private fun terms(anchor:LocalDate=date,frequency:PremiumFrequency=PremiumFrequency.MONTHLY)=InsuranceTerms(UUID(3,1),policy.id,date.withDayOfMonth(1),date.withDayOfMonth(1),date.plusYears(1),"10000000".toBigDecimal(),"1000".toBigDecimal(),frequency,anchor,CoverageScope.INDIVIDUAL,true,1,now,"")
    private fun wallet(h:HouseholdBook=HouseholdBook(),balance:String="20000")=BankBook(listOf(bank),listOf(BalanceObservation(UUID(4,1),bank.id,balance.toBigDecimal(),CurrencyCode.INR,date,now,1)),household=h)
    private fun target(value:String="20000")=EmergencyTarget(UUID(5,1),date,value.toBigDecimal(),1,now,"")
    private fun allocation(value:String="10000")=EmergencyAllocation(UUID(6,1),bank.id,null,date,value.toBigDecimal(),1,now,"")
    @Test fun expectedSalaryIsNeverActualIncome() {
        val h=HouseholdBook(sources=listOf(source),rates=listOf(IncomeRate(UUID(7,1),source.id,date,"100000".toBigDecimal(),1,now,"Expected")),cashFlows=listOf(summary(IncomeMode.RECORDED_SOURCES)))
        val result=CashFlows.at(h,month,CurrencyCode.INR);assertNull(result.income);assertNull(result.remainingCash);assertEquals(1,result.missingSources.size)
    }
    @Test fun sourceRevisionsReplaceInsteadOfAddingSalary() {
        val h=HouseholdBook(listOf(source),income=listOf(record("90000"),record("100000",2)),cashFlows=listOf(summary(IncomeMode.RECORDED_SOURCES)))
        val result=CashFlows.at(h,month,CurrencyCode.INR);number("100000",result.income);number("60000",result.surplusBeforeInvestments);number("50000",result.remainingCash)
    }
    @Test fun manualTotalDoesNotAddSourceIncomeTwice() {
        val result=CashFlows.at(HouseholdBook(listOf(source),income=listOf(record()),cashFlows=listOf(summary())),month,CurrencyCode.INR)
        number("100000",result.income);number("100000",result.recordedIncome);number("50000",result.remainingCash)
    }
    @Test fun unconfirmedOutflowsAndMissingSummaryStayUnavailable() {
        assertNull(CashFlows.at(HouseholdBook(),month,CurrencyCode.INR).income)
        val s=summary().copy(expensesConfirmed=false)
        assertNull(CashFlows.at(HouseholdBook(cashFlows=listOf(s)),month,CurrencyCode.INR).remainingCash)
    }
    @Test fun explicitZeroIncomeIsKnownAndCanProduceNegativeSurplus() {
        val result=CashFlows.at(HouseholdBook(listOf(source),income=listOf(record("0")),cashFlows=listOf(summary(IncomeMode.RECORDED_SOURCES))),month,CurrencyCode.INR)
        number("0",result.income);number("-40000",result.surplusBeforeInvestments);number("-50000",result.remainingCash)
    }
    @Test fun endedSourceCanHaveActualLateIncomeWithoutInventingExpectedIncome() {
        val old=source.copy(startedOn=date.minusYears(1),endedOn=date.minusMonths(1))
        val result=CashFlows.at(HouseholdBook(listOf(old),income=listOf(record("5000")),cashFlows=listOf(summary(IncomeMode.RECORDED_SOURCES))),month,CurrencyCode.INR)
        assertTrue(result.missingSources.isEmpty());number("5000",result.income)
    }
    @Test fun futureExpectedRateDoesNotRewriteCurrentRate() {
        val old=IncomeRate(UUID(7,1),source.id,date,"100".toBigDecimal(),1,now,"")
        val future=old.copy(id=UUID(7,2),effectiveFrom=date.plusMonths(1),monthlyNet="200".toBigDecimal())
        number("100",CashFlows.rate(HouseholdBook(rates=listOf(future,old)),source.id,date)!!.monthlyNet)
    }
    @Test fun incomeInsuranceAndEarmarksNeverIncreaseNetWorth() {
        val h=HouseholdBook(sources=listOf(source),income=listOf(record()),cashFlows=listOf(summary()),emergencyTargets=listOf(target()),allocations=listOf(allocation()),policies=listOf(policy),terms=listOf(terms()))
        number("20000",WealthValuation.at(wallet(h),date).netWorthInr);number("10000",EmergencyFunds.at(wallet(h),date).allocatedInr)
    }
    @Test fun emergencyProgressClampsWhenCashIsSpentAndRetainsRequestedAmount() {
        val h=HouseholdBook(emergencyTargets=listOf(target()),allocations=listOf(allocation()))
        val r=EmergencyFunds.at(wallet(h,"8000"),date)
        number("8000",r.allocatedInr);number("12000",r.shortfall);number("40",r.percentage);number("10000",r.lines.single().allocation.nativeAmount)
        assertTrue(r.lines.single().warning!!.contains("capped"))
        number("0",EmergencyFunds.at(wallet(h,"-100"),date).allocatedInr)
    }
    @Test fun emergencyFxMissingIsIncompleteButZeroReleaseIsKnown() {
        val usd=bank.copy(currency=CurrencyCode.USD)
        val snapshot=wallet().observations.single().copy(currency=CurrencyCode.USD)
        val h=HouseholdBook(allocations=listOf(allocation("100")))
        val book=BankBook(listOf(usd),listOf(snapshot),household=h)
        assertNull(EmergencyFunds.at(book,date).allocatedInr)
        val fx=FxObservation(UUID(8,1),CurrencyCode.USD,"80".toBigDecimal(),date,1,now,"Manual")
        number("8000",EmergencyFunds.at(book.copy(portfolio=PortfolioBook(fx=listOf(fx))),date).allocatedInr)
        number("0",EmergencyFunds.at(book.copy(household=h.copy(allocations=listOf(allocation("0")))) ,date).allocatedInr)
    }
    @Test fun emergencyMonthsUseEssentialAndCommittedOutflowsOnly() {
        val s=summary().copy(essential="5000".toBigDecimal(),premiums="1000".toBigDecimal(),loanPrincipal="1000".toBigDecimal(),loanInterest="500".toBigDecimal(),otherOutflows="500".toBigDecimal())
        val h=HouseholdBook(cashFlows=listOf(s),emergencyTargets=listOf(target("16000")),allocations=listOf(allocation("16000")))
        val r=EmergencyFunds.at(wallet(h),date);number("2",r.monthsCovered);number("100",r.percentage)
    }
    @Test fun monthEndPremiumDatesStayAnchoredIncludingLeapDay() {
        val start=LocalDate.of(2024,1,1)
        val t=terms(LocalDate.of(2024,1,31)).copy(effectiveFrom=start,validFrom=start,validUntil=start.plusYears(1))
        val dates=Insurance.dues(HouseholdBook(policies=listOf(policy),terms=listOf(t)),policy.id,start,LocalDate.of(2024,3,31)).map {it.dueOn}
        assertEquals(listOf(LocalDate.of(2024,1,31),LocalDate.of(2024,2,29),LocalDate.of(2024,3,31)),dates)
    }
    @Test fun partialAndAdvancePaymentsResolveOnlyTheirOwnInstallment() {
        val t=terms();val first=PremiumSettlement(UUID(9,1),policy.id,date,date.minusDays(1),"400".toBigDecimal(),1,now,"")
        val next=first.copy(id=UUID(9,2),dueOn=date.plusMonths(1),amount="1000".toBigDecimal())
        val h=HouseholdBook(policies=listOf(policy),terms=listOf(t),settlements=listOf(first,next))
        val dues=Insurance.dues(h,policy.id,date,date.plusMonths(1));assertEquals(1,dues.size);number("600",dues.single().outstanding)
    }
    @Test fun settlementCorrectionsReplaceCumulativeAmountAndOverpaymentIsNotCarried() {
        val first=PremiumSettlement(UUID(9,1),policy.id,date,date,"500".toBigDecimal(),1,now,"")
        val corrected=first.copy(id=UUID(9,2),amount="1200".toBigDecimal(),revision=2)
        val dues=Insurance.dues(HouseholdBook(policies=listOf(policy),terms=listOf(terms()),settlements=listOf(first,corrected)),policy.id,date,date.plusMonths(1))
        assertEquals(1,dues.size);assertEquals(date.plusMonths(1),dues.single().dueOn);number("1000",dues.single().outstanding)
    }
    @Test fun unknownPremiumDoesNotDeclareAnAmountOverdueOrPaid() {
        val t=terms().copy(premium=null,cover=null)
        val v=Insurance.view(HouseholdBook(policies=listOf(policy),terms=listOf(t)),policy,date.plusDays(1))
        assertNull(v.nextDue!!.outstanding);assertEquals(0,v.overdueCount);assertTrue(v.status.contains("unknown"));assertNull(v.monthlyBudget)
    }
    @Test fun oneOffAndLimitedPayDoNotProduceEndlessPremiums() {
        val single=terms(frequency=PremiumFrequency.SINGLE)
        assertEquals(1,Insurance.dues(HouseholdBook(terms=listOf(single)),policy.id,date,date.plusYears(1)).size)
        val limited=terms().copy(lastPremiumDue=date.plusMonths(1))
        val h=HouseholdBook(terms=listOf(limited));assertEquals(2,Insurance.dues(h,policy.id,date,date.plusYears(1)).size)
        number("0",Insurance.view(h,policy,date.plusMonths(2)).monthlyBudget)
    }
    @Test fun paymentDoesNotExtendRecordedCoverEndAndBudgetsAreNotExpenses() {
        val t=terms(frequency=PremiumFrequency.YEARLY).copy(premium="12000".toBigDecimal(),validUntil=date.plusDays(10))
        val paid=PremiumSettlement(UUID(9,1),policy.id,date,date,"12000".toBigDecimal(),1,now,"")
        val h=HouseholdBook(terms=listOf(t),settlements=listOf(paid));number("1000",Insurance.view(h,policy,date).monthlyBudget)
        val after=Insurance.view(h,policy,date.plusDays(11));assertTrue(after.status.contains("end date passed"));assertNull(after.monthlyBudget)
    }
    @Test fun renewalRetainsOldUnpaidDueAndInactivePolicyStopsActionableOldDues() {
        val old=terms();val renewal=old.copy(id=UUID(3,2),effectiveFrom=date.plusMonths(1).withDayOfMonth(1),anchorDue=date.plusMonths(1),premium="2000".toBigDecimal())
        val h=HouseholdBook(terms=listOf(old,renewal));val dues=Insurance.dues(h,policy.id,date.plusMonths(1),date.plusMonths(1))
        assertEquals(2,dues.size);number("1000",dues.first().outstanding);number("2000",dues.last().outstanding)
        val inactive=renewal.copy(active=false)
        val v=Insurance.view(h.copy(terms=listOf(old,inactive)),policy,date.plusMonths(1));assertNull(v.nextDue);assertEquals("Marked inactive",v.status)
    }
}
