package com.wealthos.finance

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class PerformanceTest {
    private val d=LocalDate.of(2025,1,1);private val now=Instant.parse("2025-01-01T00:00:00Z")
    private val i=Instrument(UUID(1,1),"Fund",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.TRANSACTIONS,d,now,true)
    private fun b(v:String)=v.toBigDecimal()
    private fun close(s:String,v:BigDecimal?)=assertTrue("$s versus $v",v!=null && (b(s)-v).abs()<b("0.000000001"))
    private fun trade(n:Int,kind:TradeKind,units:String,gross:String?,date:LocalDate=d,fees:String="0")=InvestmentEntry(UUID(2,n.toLong()),i.id,kind,date,n,b(units),gross?.let(::b),b(fees),recordedAt=now)
    private fun book(entries:List<InvestmentEntry>,quote:String="120",date:LocalDate=d.plusDays(365))=BankBook(portfolio=PortfolioBook(listOf(i),entries,listOf(AssetValuation(UUID(3,1),i.id,ValuationKind.UNIT_PRICE,b(quote),date,1,now))))
    @Test fun purchaseTerminalReturnsAndCagr(){val book=book(listOf(trade(1,TradeKind.BUY,"10","1000")));val p=Performance.instrument(book,i.id,d.plusDays(365));close("1000",p.invested);close("1200",p.value);close("0.2",p.xirr.rate);close("0.2",p.cagr.rate);close("0.2",p.simpleReturn)}
    @Test fun openingCostDoesNotInventCashFlowHistory(){val p=Performance.instrument(book(listOf(trade(1,TradeKind.OPENING,"10","1000"))),i.id,d.plusDays(365));close("0.2",p.simpleReturn);assertNull(p.xirr.rate);assertNull(p.cagr.rate);assertTrue(p.xirr.status.contains("incomplete"))}
    @Test fun unknownOpeningCostStaysUnknown(){val p=Performance.instrument(book(listOf(trade(1,TradeKind.OPENING,"10",null))),i.id,d.plusDays(365));assertNull(p.invested);assertNull(p.simpleReturn);assertNull(p.xirr.rate)}
    @Test fun partialSaleSeparatesRealizedUnrealizedAndTotalSimpleGain(){val p=Performance.instrument(book(listOf(trade(1,TradeKind.BUY,"10","1000"),trade(2,TradeKind.SELL,"5","600",d.plusDays(180)))),i.id,d.plusDays(365));close("100",p.realized);close("100",p.unrealized);close("0.2",p.simpleReturn);assertNotNull(p.xirr.rate);assertNull(p.cagr.rate)}
    @Test fun reinvestedDistributionDoesNotBecomeNewExternalCapital(){val p=Performance.instrument(book(listOf(trade(1,TradeKind.BUY,"10","100",fees="2"),trade(2,TradeKind.REINVEST,"1","10",d.plusDays(100),"1")),"10"),i.id,d.plusDays(365));close("103",p.invested);close("110",p.value);assertEquals(3,p.cashFlows.size);close("-1",p.cashFlows[1].amount)}
    @Test fun foreignFlowsUseIndependentDatedFx(){val foreign=i.copy(currency=CurrencyCode.USD);val base=book(listOf(trade(1,TradeKind.BUY,"1","100")),"110");val rates=listOf(FxObservation(UUID(4,1),CurrencyCode.USD,b("80"),d,1,now,"Start"),FxObservation(UUID(4,2),CurrencyCode.USD,b("90"),d.plusDays(365),1,now,"End"));val book=base.copy(portfolio=base.portfolio.copy(instruments=listOf(foreign),fx=rates));close("0.2375",Performance.portfolioXirr(book,d.plusDays(365)).rate);assertNull(Performance.portfolioXirr(book.copy(portfolio=book.portfolio.copy(fx=rates.drop(1))),d.plusDays(365)).rate)}
    @Test fun goalsAreEarmarksAndUseFutureTargetInflation(){val g=FinancialGoal(UUID(5,1),UUID(5,2),"Goal",CurrencyCode.INR,b("1200"),d.plusYears(1),true,b("0"),b("0"),b("0.1"),1,now,"");val r=Goals.at(g,d);close("1320",r.targetFuture);close("110",r.monthlyRequired);assertNull(Goals.at(g.copy(due=d),d).monthlyRequired)}
    @Test fun oldGoalPurchasingPowerReferenceDoesNotRebaseWhenReviewed(){val g=FinancialGoal(UUID(5,1),UUID(5,2),"Goal",CurrencyCode.INR,b("1200"),d.plusYears(2),true,b("0"),b("0"),b("0.1"),1,now,"");close("1452",Goals.at(g,d).targetFuture);close("1452",Goals.at(g,d.plusYears(1)).targetFuture);close("121",Goals.at(g,d.plusYears(1)).monthlyRequired)}
    @Test fun overdueGoalStillUsesItsRecordedInflationBasis(){val g=FinancialGoal(UUID(5,1),UUID(5,2),"Overdue",CurrencyCode.INR,b("1200"),d.plusYears(1),true,b("0"),b("0"),b("0.1"),1,now,"");val r=Goals.at(g,d.plusYears(2));close("1320",r.targetFuture);assertNull(r.monthlyRequired)}
}
