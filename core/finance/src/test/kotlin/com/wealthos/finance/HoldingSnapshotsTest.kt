package com.wealthos.finance

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class HoldingSnapshotsTest {
    private val day=LocalDate.of(2026,1,1)
    private val i=Instrument(UUID.randomUUID(),"Fictional fund",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.OBSERVED,day,Instant.EPOCH)
    private fun v(value:String="1234.56")=AssetValuation(UUID.randomUUID(),i.id,ValuationKind.OBSERVED_TOTAL,value.toBigDecimal(),day,1,Instant.EPOCH)
    @Test fun currentValueNeedsNoUnitsOrAcquisitionBasis() {
        val result=WealthValuation.asset(i,PortfolioBook(instruments=listOf(i),valuations=listOf(v())),day)
        assertEquals("1234.56",result.nativeValue!!.toPlainString());assertNull(result.holding);assertNull(result.unrealizedGain)
        assertNull(result.snapshot!!.reportedInvested);assertNull(result.snapshot.reportedUnits)
    }
    @Test fun reportedInvestedDoesNotCreateAcquisitionsOrReturns() {
        val snapshot=v().copy(reportedInvested="1000.00".toBigDecimal())
        HoldingSnapshots.validate(snapshot,i.currency)
        val book=BankBook(emptyList(),emptyList(),portfolio=PortfolioBook(instruments=listOf(i),valuations=listOf(snapshot)))
        assertEquals(0,"1234.56".toBigDecimal().compareTo(WealthValuation.at(book,day).netWorthInr!!))
        assertNull(Performance.portfolioXirr(book,day).rate)
    }
    @Test fun unitsTimesNavRoundsOnceHalfEvenAtCurrencyScale() {
        assertEquals("10.00",HoldingSnapshots.total("2".toBigDecimal(),"5.0025".toBigDecimal(),CurrencyCode.INR).toPlainString())
        assertEquals("10.02",HoldingSnapshots.total("2".toBigDecimal(),"5.0075".toBigDecimal(),CurrencyCode.INR).toPlainString())
        assertEquals("1524.16",HoldingSnapshots.total("12.345678".toBigDecimal(),"123.456789".toBigDecimal(),CurrencyCode.INR).toPlainString())
    }
    @Test fun reportedTotalTakesPrecedenceOverOptionalRoundedQuote() {
        val snapshot=v().copy(reportedUnits="10".toBigDecimal(),reportedPrice="123.46".toBigDecimal())
        HoldingSnapshots.validate(snapshot,i.currency)
        assertEquals("1234.56",WealthValuation.asset(i,PortfolioBook(valuations=listOf(snapshot)),day).nativeValue!!.toPlainString())
    }
    @Test fun mismatchedCalculatedValueIsRejected() {
        try{HoldingSnapshots.validate(v().copy(reportedUnits=BigDecimal.TEN,reportedPrice=BigDecimal.TEN,calculatedTotal=true),i.currency);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun negativeOrExcessPrecisionMetadataIsRejected() {
        for(snapshot in listOf(v().copy(reportedInvested="-1".toBigDecimal()),v().copy(reportedUnits="-1".toBigDecimal()),v().copy(reportedPrice="0.1234567890123".toBigDecimal()))) {
            try{HoldingSnapshots.validate(snapshot,i.currency);fail()}catch(_:IllegalArgumentException){}
        }
    }
    @Test fun conversionPreservesHistoryAndNeverUsesTotalAsUnitPrice() {
        val converted=i.copy(tracking=TrackingMode.TRANSACTIONS)
        val opening=InvestmentEntry(UUID.randomUUID(),i.id,TradeKind.OPENING,day.plusDays(2),1,"5".toBigDecimal(),recordedAt=Instant.EPOCH)
        val book=PortfolioBook(instruments=listOf(converted),valuations=listOf(v()),entries=listOf(opening))
        val before=WealthValuation.asset(converted,book,day.plusDays(1))
        assertEquals("1234.56",before.nativeValue!!.toPlainString());assertNull(before.holding)
        val after=WealthValuation.asset(converted,book,day.plusDays(2))
        assertNull(after.nativeValue);assertNull(after.holding!!.remainingCost)
    }
    @Test fun zeroSnapshotIsKnownAndForeignFxRemainsIndependent() {
        val foreign=i.copy(currency=CurrencyCode.USD)
        val book=BankBook(emptyList(),emptyList(),portfolio=PortfolioBook(instruments=listOf(foreign),valuations=listOf(v("0.00"))))
        assertEquals(0,WealthValuation.at(book,day).netWorthInr!!.signum())
        val positive=book.copy(portfolio=book.portfolio.copy(valuations=listOf(v("10.00"))))
        assertNull(WealthValuation.at(positive,day).netWorthInr)
        val fx=FxObservation(UUID.randomUUID(),CurrencyCode.USD,"80".toBigDecimal(),day,1,Instant.EPOCH,"Fictional FX")
        assertEquals(0,"800".toBigDecimal().compareTo(WealthValuation.at(positive.copy(portfolio=positive.portfolio.copy(fx=listOf(fx))),day).netWorthInr!!))
    }
}
