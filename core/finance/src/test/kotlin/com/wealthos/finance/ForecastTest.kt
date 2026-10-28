package com.wealthos.finance

import org.junit.Test
import org.junit.Assert.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ForecastTest {
    private val d=LocalDate.of(2025,1,1);private val now=Instant.parse("2025-01-01T00:00:00Z")
    private fun b(s:String)=s.toBigDecimal()
    private fun close(s:String,v:BigDecimal)=assertTrue("$s versus $v",(b(s)-v).abs()<b("0.000000001"))
    private val bank=BankAccount(UUID(0,1),"Cash","",CurrencyCode.INR,AccountKind.SAVINGS,now)
    private fun book(amount:String="1200")=BankBook(listOf(bank),listOf(BalanceObservation(UUID(0,2),bank.id,b(amount),CurrencyCode.INR,d,now,1)))
    private fun scenario(mode:ForecastMode=ForecastMode.CONTRIBUTION)=ForecastScenario(UUID(1,1),UUID(1,2),"Fixture",ScenarioKind.BASE,mode,d,12,b("0"),b("0"),b("0"),b("0"),b("0"),b("0"),b("0"),b("0"),b("0"),b("0"),b("0"),false,false,1,now,"")
    @Test fun externallyFundedContributionsCreateOnlyPlannedNewWealth(){val s=scenario().copy(monthlyContribution=b("100"));val result=Forecasts.calculate(book(),s);assertTrue(result.complete);close("2400",result.points.last().nominal);close("1200",WealthValuation.at(book(),d).netWorthInr!!)}
    @Test fun cashFlowContributionsMoveCashWithoutCreatingWealth(){val s=scenario(ForecastMode.CASH_FLOW).copy(monthlyContribution=b("100"));val r=Forecasts.calculate(book(),s);assertTrue(r.complete);close("1200",r.points.last().nominal);close("0",r.points.last().cash)}
    @Test fun cashFlowSalaryCostsAndContributionsCountOnce(){val s=scenario(ForecastMode.CASH_FLOW).copy(salary=b("1000"),essential=b("300"),premiums=b("100"),monthlyContribution=b("200"));val r=Forecasts.calculate(book("0"),s);close("7200",r.points.last().nominal);close("4800",r.points.last().cash)}
    @Test fun unfundedContributionsStopWithoutInventingBorrowing(){val r=Forecasts.calculate(book("0"),scenario(ForecastMode.CASH_FLOW).copy(monthlyContribution=b("100")));assertFalse(r.complete);assertEquals(1,r.gap!!.month);close("100",r.gap!!.amount);assertEquals(2,r.points.size)}
    @Test fun returnsAndInflationCompoundConsistently(){val s=scenario().copy(cashReturn=b("0.1"),inflation=b("0.1"));val r=Forecasts.calculate(book("1000"),s);close("1100",r.points.last().nominal);close("1000",r.points.last().real)}
    @Test fun salaryAndSipStepUpBeginInMonthThirteen(){val s=scenario(ForecastMode.CASH_FLOW).copy(months=13,salary=b("1000"),salaryGrowth=b("0.1"),monthlyContribution=b("100"),stepUp=b("0.2"));val r=Forecasts.calculate(book("0"),s);close("1100",r.points.last().salary);close("120",r.points.last().contributions);close("13100",r.points.last().nominal)}
    @Test fun loanPrincipalPaymentDoesNotChangeNetWorthAtZeroInterest(){val loan=Instrument(UUID(2,1),"Loan",AssetCategory.LOAN,CurrencyCode.INR,TrackingMode.LOAN,d,now);val c=FinancialContract(loan.id,b("1000"),b("0"),d,d.plusMonths(10),termMonths=10);val s=scenario(ForecastMode.CASH_FLOW);val ledger=book().copy(portfolio=PortfolioBook(listOf(loan),contracts=listOf(c)),planning=PlanningBook(assets=listOf(AssetAssumption(s.id,loan.id,b("0"),b("100")))));val r=Forecasts.calculate(ledger,s);assertTrue(r.complete);close("200",r.points.first().nominal);close("200",r.points.last().nominal);close("0",r.points.last().liabilities)}
    @Test fun depositMaturityIsAnInternalTransferAndReinvestmentIsExplicit(){val fd=Instrument(UUID(2,2),"FD",AssetCategory.FD,CurrencyCode.INR,TrackingMode.DEPOSIT,d,now);val c=FinancialContract(fd.id,b("1000"),b("0"),d,d.plusMonths(1));val ledger=book("0").copy(portfolio=PortfolioBook(listOf(fd),contracts=listOf(c)));val r=Forecasts.calculate(ledger,scenario());close("1000",r.points.last().nominal);close("1000",r.points.last().cash);val reinvest=Forecasts.calculate(ledger,scenario().copy(reinvestDeposits=true,investmentReturn=b("0.12")));assertTrue(reinvest.points.last().nominal>b("1000"));close("0",reinvest.points.last().cash)}
    @Test fun foreignAssetAndFxHaveIndependentGrowth(){val foreign=bank.copy(currency=CurrencyCode.USD);val ledger=book("100").let {it.copy(accounts=listOf(foreign),observations=it.observations.map {o->o.copy(currency=CurrencyCode.USD)},portfolio=PortfolioBook(fx=listOf(FxObservation(UUID(4,1),CurrencyCode.USD,b("80"),d,1,now,"Manual"))))};val s=scenario();val r=Forecasts.calculate(ledger.copy(planning=PlanningBook(fx=listOf(FxAssumption(s.id,CurrencyCode.USD,b("0.1"))))),s);close("8800",r.points.last().nominal)}
    @Test fun plannedWithdrawalsAndGoalsReduceExistingWealth(){val s=scenario().copy(includeGoals=true);val g=FinancialGoal(UUID(5,1),UUID(5,2),"Goal",CurrencyCode.INR,b("200"),d.plusMonths(2),false,b("100"),b("0"),b("0"),1,now,"");val ledger=book().copy(planning=PlanningBook(withdrawals=listOf(PlannedWithdrawal(UUID(6,1),s.id,1,null,b("100"),"Trip")),goals=listOf(g)));val r=Forecasts.calculate(ledger,s);close("900",r.points.last().nominal);close("1200",WealthValuation.at(ledger,d).netWorthInr!!)}
    @Test fun missingStartingValuationIsUnavailable(){val unknown=book().copy(observations=emptyList());assertNotNull(Forecasts.calculate(unknown,scenario()).unavailable)}
    @Test fun savedScenarioInputsAreIndependent(){val base=scenario();val other=base.copy(id=UUID(9,1),key=UUID(9,2),cashReturn=b("0.1"));close("1200",Forecasts.calculate(book(),base).points.last().nominal);close("1320",Forecasts.calculate(book(),other).points.last().nominal)}
    @Test fun completedForeignGoalDoesNotRequireFxOrWithdrawCash(){val goal=FinancialGoal(UUID(5,1),UUID(5,2),"Completed",CurrencyCode.USD,b("200"),d.minusDays(1),false,b("200"),b("0"),b("0"),1,now,"",active=false);val r=Forecasts.calculate(book().copy(planning=PlanningBook(goals=listOf(goal))),scenario().copy(includeGoals=true));assertTrue(r.complete);close("1200",r.points.last().nominal)}
}
