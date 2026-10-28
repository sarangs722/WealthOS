package com.wealthos.finance

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PortfolioTest {
    private val day=LocalDate.of(2026,1,1)
    private val id=UUID(0,10)
    private val now=Instant.EPOCH
    private fun decimal(expected:String,actual:BigDecimal?)=assertEquals(0,BigDecimal(expected).compareTo(actual!!))
    private fun e(kind:TradeKind,seq:Int,units:String="0",gross:String?=null,fees:String="0",date:LocalDate=day,denom:String="1")=
        InvestmentEntry(UUID(1,seq.toLong()),id,kind,date,seq,units.toBigDecimal(),gross?.toBigDecimal(),fees.toBigDecimal(),denom.toBigDecimal(),"Fixture",now)
    private fun instrument(category:AssetCategory=AssetCategory.STOCK,currency:CurrencyCode=CurrencyCode.INR)=
        Instrument(id,"Fixture",category,currency,TrackingMode.TRANSACTIONS,day,now,true)
    @Test fun multipleSipsAndPartialSaleUseFifoIncludingFees() {
        val h=Holdings.calculate(listOf(e(TradeKind.BUY,1,"10","1000","10"),e(TradeKind.BUY,2,"5","750"),e(TradeKind.SELL,3,"12","1800","12")),day)
        decimal("3",h.units);decimal("450",h.remainingCost);decimal("478",h.realizedGain)
        assertEquals(1,h.lots.size)
    }
    @Test fun splitPreservesTotalBasisAndSaleUsesAdjustedUnits() {
        val h=Holdings.calculate(listOf(e(TradeKind.BUY,1,"10","1000"),e(TradeKind.SPLIT,2,"2"),e(TradeKind.SELL,3,"5","400")),day)
        decimal("15",h.units);decimal("750",h.remainingCost);decimal("150",h.realizedGain)
    }
    @Test fun carriedQuoteAdjustsForSplitsAndBonusWithoutCreatingWealth() {
        val i=instrument()
        val entries=listOf(e(TradeKind.BUY,1,"10","1000"),e(TradeKind.SPLIT,2,"2",date=day.plusDays(1)),e(TradeKind.BONUS,3,"1",date=day.plusDays(2)))
        val quote=AssetValuation(UUID(2,1),id,ValuationKind.UNIT_PRICE,"100".toBigDecimal(),day,1,now,includedInvestmentSequence=1)
        val p=PortfolioBook(listOf(i),entries,listOf(quote))
        decimal("1000",WealthValuation.at(BankBook(portfolio=p),day.plusDays(2)).netWorthInr)
        val fresh=quote.copy(id=UUID(2,2),value="30".toBigDecimal(),date=day.plusDays(2),includedInvestmentSequence=3)
        decimal("1200",WealthValuation.at(BankBook(portfolio=p.copy(valuations=listOf(quote,fresh))),day.plusDays(2)).netWorthInr)
    }
    @Test fun unitPriceInputsKeepTheirFullPrecisionAndLargeProductsRemainExact() {
        assertEquals("₹1,23,456.123456",Amounts.formatQuote("123456.123456".toBigDecimal(),CurrencyCode.INR))
        val i=instrument();val units="999999999999999999999999";val price="999999999999999999999999.123456789012"
        val p=PortfolioBook(listOf(i),listOf(e(TradeKind.OPENING,1,units)),listOf(AssetValuation(UUID(2,1),id,ValuationKind.UNIT_PRICE,price.toBigDecimal(),day,1,now)))
        assertEquals(0,units.toBigDecimal().multiply(price.toBigDecimal()).compareTo(WealthValuation.asset(i,p,day).nativeValue!!))
    }
    @Test fun bonusAddsZeroCostLotsWithoutChangingExistingLots() {
        val h=Holdings.calculate(listOf(e(TradeKind.BUY,1,"10","1000"),e(TradeKind.BONUS,2,"1",denom="1"),e(TradeKind.SELL,3,"10","1200")),day)
        decimal("10",h.units);decimal("0",h.remainingCost);decimal("200",h.realizedGain)
    }
    @Test fun unknownOpeningCostNeverBecomesZeroBasis() {
        val h=Holdings.calculate(listOf(e(TradeKind.OPENING,1,"10"),e(TradeKind.SELL,2,"2","300")),day)
        assertNull(h.remainingCost);assertNull(h.realizedGain);assertFalse(h.historyComplete)
    }
    @Test fun reinvestmentAddsUnitsBasisAndDistributionButOnlyFeesAffectCash() {
        val reinvest=e(TradeKind.REINVEST,2,"2","200","1")
        val h=Holdings.calculate(listOf(e(TradeKind.BUY,1,"10","1000"),reinvest),day)
        decimal("12",h.units);decimal("1201",h.remainingCost);decimal("200",h.distributions);decimal("-1",Holdings.cash(reinvest))
    }
    @Test fun cashDividendDoesNotInventUnits() {
        val h=Holdings.calculate(listOf(e(TradeKind.BUY,1,"10","1000"),e(TradeKind.DIVIDEND,2,gross="50",fees="5")),day)
        decimal("10",h.units);decimal("45",h.distributions)
    }
    @Test fun oversalesAndBackdatedInvalidHistoryAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {Holdings.calculate(listOf(e(TradeKind.BUY,1,"2","200",date=day.plusDays(1)),e(TradeKind.SELL,2,"1","100")),day.plusDays(1))}
        assertThrows(IllegalArgumentException::class.java) {Holdings.calculate(listOf(e(TradeKind.BUY,1,"2","200"),e(TradeKind.SELL,2,"3","300")),day)}
    }
    @Test fun manualAdjustmentsRequireExplanationAndNeverManufactureRealizedGains() {
        assertThrows(IllegalArgumentException::class.java) {Holdings.calculate(listOf(e(TradeKind.ADJUST,1,"1").copy(note="")),day)}
        val h=Holdings.calculate(listOf(e(TradeKind.BUY,1,"10","1000"),e(TradeKind.ADJUST,2,"-2")),day)
        decimal("8",h.units);decimal("800",h.remainingCost);decimal("0",h.realizedGain);assertFalse(h.historyComplete)
    }
    @Test fun fifoRemainderConservesAllCostWhenLastFractionIsSold() {
        val entries=listOf(e(TradeKind.BUY,1,"3","100"),e(TradeKind.SELL,2,"1","40"),e(TradeKind.SELL,3,"2","80"))
        val h=Holdings.calculate(entries,day);decimal("0",h.remainingCost);decimal("20",h.realizedGain)
    }
    @Test fun completeSaleUsesNumericEqualityAcrossDecimalScales() {
        val h=Holdings.calculate(listOf(e(TradeKind.OPENING,1,"10.00"),e(TradeKind.SELL,2,"10.0","1000"),e(TradeKind.BUY,3,"2.00","200")),day)
        assertEquals(1,h.lots.size);decimal("2",h.units);decimal("200",h.remainingCost)
    }
    @Test fun independentForeignPriceAndCurrencyMovementsAreValuedByDate() {
        val i=instrument(currency=CurrencyCode.USD)
        val p=PortfolioBook(listOf(i),listOf(e(TradeKind.OPENING,1,"10","1000")),
            listOf(AssetValuation(UUID(2,1),id,ValuationKind.UNIT_PRICE,"100".toBigDecimal(),day,1,now)),
            fx=listOf(FxObservation(UUID(3,1),CurrencyCode.USD,"80".toBigDecimal(),day,1,now,"Manual"),FxObservation(UUID(3,2),CurrencyCode.USD,"85".toBigDecimal(),day.plusDays(1),1,now,"Manual")))
        decimal("80000",WealthValuation.at(BankBook(portfolio=p),day).netWorthInr)
        decimal("85000",WealthValuation.at(BankBook(portfolio=p),day.plusDays(1)).netWorthInr)
        decimal("0",WealthValuation.asset(i,p,day).unrealizedGain)
        assertNull(WealthValuation.at(BankBook(portfolio=p.copy(fx=emptyList())),day).netWorthInr)
    }
    @Test fun linkedPurchaseIsAppliedOnceThenAbsorbedByNewSnapshot() {
        val bank=BankAccount(UUID(0,1),"Cash","Bank",CurrencyCode.INR,AccountKind.SAVINGS,now)
        val opening=BalanceObservation(UUID(2,1),bank.id,"1000.00".toBigDecimal(),CurrencyCode.INR,day,now,1)
        val movement=CashMovement(UUID(3,1),bank.id,CurrencyCode.INR,day,"-200".toBigDecimal(),opening.id,true,UUID(1,1),now,"Purchase")
        val p=PortfolioBook(listOf(instrument()),listOf(e(TradeKind.BUY,1,"2","200")),listOf(AssetValuation(UUID(4,1),id,ValuationKind.UNIT_PRICE,"100".toBigDecimal(),day,1,now)),cashMovements=listOf(movement))
        val book=BankBook(listOf(bank),listOf(opening),p)
        decimal("1000",WealthValuation.at(book,day).netWorthInr)
        decimal("800",BankValuation.at(book,day).inputs.single().value)
        val latest=opening.copy(id=UUID(2,2),amount="800.00".toBigDecimal(),revision=2)
        decimal("1000",WealthValuation.at(book.copy(observations=listOf(opening,latest)),day).netWorthInr)
        assertFalse(BankValuation.at(book.copy(observations=listOf(opening,latest)),day).inputs.single().provisional)
    }
    @Test fun snapshotAlreadyIncludesPurchaseAndBackdatedSnapshotStillExcludesLaterCashFlow() {
        val bank=BankAccount(UUID(0,1),"Cash","Bank",CurrencyCode.INR,AccountKind.SAVINGS,now)
        val a=BalanceObservation(UUID(2,1),bank.id,"800.00".toBigDecimal(),CurrencyCode.INR,day.plusDays(3),now,1)
        val m=CashMovement(UUID(3,1),bank.id,CurrencyCode.INR,day.plusDays(2),"-200".toBigDecimal(),a.id,false,UUID(1,1),now,"Already reflected")
        decimal("800",BankValuation.at(BankBook(listOf(bank),listOf(a),PortfolioBook(cashMovements=listOf(m))),day.plusDays(3)).netWorthInr)
        val old=a.copy(id=UUID(2,2),amount="1000.00".toBigDecimal(),effectiveDate=day)
        decimal("800",BankValuation.at(BankBook(listOf(bank),listOf(old),PortfolioBook(cashMovements=listOf(m))),day.plusDays(2)).netWorthInr)
    }
    @Test fun internalTransferLegsPreserveWealth() {
        val a=BankAccount(UUID(0,1),"A","",CurrencyCode.INR,AccountKind.SAVINGS,now)
        val b=a.copy(id=UUID(0,2),name="B")
        val snapshots=listOf(a,b).mapIndexed {n,bank -> BalanceObservation(UUID(2,n.toLong()),bank.id,"1000".toBigDecimal(),CurrencyCode.INR,day,now,1)}
        val cash=snapshots.mapIndexed {n,s -> CashMovement(UUID(3,n.toLong()),s.accountId,CurrencyCode.INR,day.plusDays(1),(if(n==0) "-500" else "500").toBigDecimal(),s.id,true,UUID(4,1),now,"Transfer")}
        decimal("2000",WealthValuation.at(BankBook(listOf(a,b),snapshots,PortfolioBook(cashMovements=cash)),day.plusDays(1)).netWorthInr)
    }
    @Test fun brokerageStatementTotalIsNotAnAdditionalAsset() {
        val a=BankAccount(UUID(0,1),"Broker cash","",CurrencyCode.INR,AccountKind.BROKER_CASH,now)
        val snapshot=BalanceObservation(UUID(2,1),a.id,"100".toBigDecimal(),CurrencyCode.INR,day,now,1)
        val i=instrument().copy(custodyAccountId=a.id)
        val ref=StatementReference(UUID(4,1),a.id,"1000".toBigDecimal(),day,1,now)
        val p=PortfolioBook(listOf(i),listOf(e(TradeKind.OPENING,1,"9","900")),listOf(AssetValuation(UUID(3,1),id,ValuationKind.UNIT_PRICE,"100".toBigDecimal(),day,1,now)),references=listOf(ref))
        val book=BankBook(listOf(a),listOf(snapshot),p)
        decimal("1000",WealthValuation.at(book,day).netWorthInr);decimal("0",WealthValuation.reconcile(book,ref))
    }
    @Test fun quarterlyDepositAndZeroInterestHaveKnownMaturities() {
        decimal("1082.43216",Contracts.accrued("1000".toBigDecimal(),"0.08".toBigDecimal(),day,day.plusYears(1),DepositConvention.QUARTERLY))
        decimal("1000",Contracts.accrued("1000".toBigDecimal(),BigDecimal.ZERO,day,day.plusYears(1),DepositConvention.QUARTERLY))
        decimal("1010",Contracts.accrued("1000".toBigDecimal(),"0.365".toBigDecimal(),day,day.plusDays(10),DepositConvention.SIMPLE_ACT_365))
    }
    @Test fun monthlyAnniversaryWorksAtMonthEnd() {
        decimal("1010",Contracts.accrued("1000".toBigDecimal(),"0.12".toBigDecimal(),LocalDate.of(2025,1,31),LocalDate.of(2025,2,28),DepositConvention.MONTHLY))
    }
    @Test fun rdOnlyAccruesRecordedInstallmentsAndStopsAtMaturity() {
        val c=FinancialContract(id,"1000".toBigDecimal(),"0.12".toBigDecimal(),day,day.plusMonths(6),DepositConvention.QUARTERLY,"1000".toBigDecimal())
        val event=ContractEvent(UUID(3,1),id,ContractEventKind.CONTRIBUTION,day.plusMonths(3),1,"1000".toBigDecimal(),recordedAt=now)
        decimal("2090.9",Contracts.depositValue(c,listOf(event),day.plusMonths(6)))
        decimal("2090.9",Contracts.depositValue(c,listOf(event),day.plusYears(1)))
        val closed=event.copy(id=UUID(3,2),kind=ContractEventKind.MATURITY,date=day.plusMonths(6),sequence=2,principal="2000".toBigDecimal())
        decimal("0",Contracts.depositValue(c,listOf(event,closed),closed.date))
    }
    @Test fun loanInterestDoesNotReducePrincipalAndFinalScheduleClosesExactly() {
        val c=FinancialContract(id,"1000".toBigDecimal(),"0.12".toBigDecimal(),day,day.plusYears(1),termMonths=12)
        val payment=ContractEvent(UUID(3,1),id,ContractEventKind.REPAYMENT,day.plusMonths(1),1,"70".toBigDecimal(),"10".toBigDecimal(),recordedAt=now)
        decimal("930",Contracts.loanPrincipal(c,listOf(payment),payment.date));decimal("-80",Contracts.cash(payment))
        decimal("88.85",Contracts.emi(c.openingPrincipal,c.annualRate,12))
        val schedule=Contracts.schedule(c.openingPrincipal,c.annualRate,12)
        decimal("0",schedule.last().closing);decimal("1000",schedule.fold(BigDecimal.ZERO) {sum,p->sum+p.principal})
        decimal("83.33",Contracts.emi(c.openingPrincipal,BigDecimal.ZERO,12))
    }
    @Test fun observedDepositValuesRemainDistinctFromEstimates() {
        val i=Instrument(id,"FD",AssetCategory.FD,CurrencyCode.INR,TrackingMode.DEPOSIT,day,now)
        val c=FinancialContract(id,"1000".toBigDecimal(),"0.08".toBigDecimal(),day,day.plusYears(1))
        val p=PortfolioBook(listOf(i),valuations=listOf(AssetValuation(UUID(2,1),id,ValuationKind.OBSERVED_TOTAL,"1030".toBigDecimal(),day.plusMonths(6),1,now)),contracts=listOf(c))
        val v=WealthValuation.asset(i,p,day.plusMonths(6));decimal("1030",v.nativeValue);decimal("1040.4",v.contractEstimate)
    }
    @Test fun staleManualPriceAndFxAreExplicitlyCounted() {
        val i=instrument(currency=CurrencyCode.USD)
        val p=PortfolioBook(listOf(i),listOf(e(TradeKind.OPENING,1,"1")),listOf(AssetValuation(UUID(2,1),id,ValuationKind.UNIT_PRICE,"100".toBigDecimal(),day,1,now)),fx=listOf(FxObservation(UUID(3,1),CurrencyCode.USD,"80".toBigDecimal(),day,1,now,"Manual")))
        val v=WealthValuation.at(BankBook(portfolio=p),day.plusDays(40))
        assertEquals(1,v.staleAccounts);assertEquals(1,v.staleFx);decimal("8000",v.netWorthInr)
    }
}
