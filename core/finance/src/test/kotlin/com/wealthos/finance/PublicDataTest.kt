package com.wealthos.finance

import java.time.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PublicDataTest {
    private val at=Instant.parse("2026-10-27T12:00:00Z")
    private fun d()=PublicDatum(PublicProvider.AMFI_FILE,PublicKind.NAV,"NAV:123456","Fictional Direct Growth","80.00".toBigDecimal(),CurrencyCode.INR,"INR per unit",LocalDate.parse("2026-10-26"),PublicProvider.AMFI_FILE.home,PublicValidation.USER_FILE,"exact code","fictional fixture","file date")
    @Test fun staleAndFutureReferencesAreExplicit(){val o=PublicObservation(UUID.randomUUID(),d(),at,1,"");assertTrue(PublicRules.freshness(o,LocalDate.parse("2026-10-27")).startsWith("Recent"));assertTrue(PublicRules.freshness(o,LocalDate.parse("2026-10-31")).startsWith("Stale"));assertTrue(PublicRules.freshness(o,LocalDate.parse("2026-10-25")).contains("invalid"))}
    @Test fun cacheCannotCreateWealth(){val b=BankBook(publicData=PublicBook(observations=listOf(PublicObservation(UUID.randomUUID(),d(),at,1,""))));assertEquals(0,WealthValuation.at(b,LocalDate.parse("2026-10-27")).knownInrSubtotal.signum())}
    @Test fun priorDateUnitQuoteDoesNotInventHistoricalHoldings(){val day=LocalDate.parse("2026-10-27");val i=Instrument(UUID.randomUUID(),"Fund",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.TRANSACTIONS,day,at)
        val p=PortfolioBook(listOf(i),listOf(InvestmentEntry(UUID.randomUUID(),i.id,TradeKind.OPENING,day,1,"5".toBigDecimal(),null,recordedAt=at)),listOf(AssetValuation(UUID.randomUUID(),i.id,ValuationKind.UNIT_PRICE,"80".toBigDecimal(),day.minusDays(1),1,at)))
        assertEquals(0,"400".toBigDecimal().compareTo(WealthValuation.asset(i,p,day).nativeValue!!));assertNull(WealthValuation.asset(i,p,day.minusDays(1)).nativeValue)
    }
    @Test fun invalidOrUnverifiedOfficialValuesFailClosed(){listOf(d().copy(value="0".toBigDecimal()),d().copy(date=LocalDate.parse("2026-10-28")),d().copy(sourceUrl="http://example.com"),d().copy(validation=PublicValidation.HTTPS_VALIDATED)).forEach {try{PublicRules.validate(it,at);fail()}catch(_:IllegalArgumentException){}}}
    @Test fun closingNavIncludesSameDaySplitEnteredAfterQuote(){
        val day=LocalDate.parse("2026-10-27");val i=Instrument(UUID.randomUUID(),"Fund",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.TRANSACTIONS,day.minusDays(1),at)
        val entries=listOf(InvestmentEntry(UUID.randomUUID(),i.id,TradeKind.OPENING,day.minusDays(1),1,"10".toBigDecimal(),"100".toBigDecimal(),recordedAt=at),InvestmentEntry(UUID.randomUUID(),i.id,TradeKind.SPLIT,day,2,"2".toBigDecimal(),recordedAt=at))
        val source=PublicObservation(UUID.randomUUID(),d().copy(date=day,value="5".toBigDecimal()),at,1,"")
        val quote=AssetValuation(UUID.randomUUID(),i.id,ValuationKind.UNIT_PRICE,"5".toBigDecimal(),day,1,at,includedInvestmentSequence=1,source=source)
        val value=WealthValuation.asset(i,PortfolioBook(listOf(i),entries,listOf(quote)),day)
        assertEquals(0,"20".toBigDecimal().compareTo(value.holding!!.units));assertEquals(0,"100".toBigDecimal().compareTo(value.nativeValue!!));assertEquals(0,"5".toBigDecimal().compareTo(value.unitPrice!!))
    }
    @Test fun closingNavStillAdjustsForActionsOnLaterFinancialDates(){
        val day=LocalDate.parse("2026-10-27");val i=Instrument(UUID.randomUUID(),"Fund",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.TRANSACTIONS,day.minusDays(1),at)
        val entries=listOf(InvestmentEntry(UUID.randomUUID(),i.id,TradeKind.OPENING,day.minusDays(1),1,"10".toBigDecimal(),"100".toBigDecimal(),recordedAt=at),InvestmentEntry(UUID.randomUUID(),i.id,TradeKind.BONUS,day,2,"1".toBigDecimal(),recordedAt=at))
        val source=PublicObservation(UUID.randomUUID(),d().copy(value="10".toBigDecimal()),at,1,"")
        val quote=AssetValuation(UUID.randomUUID(),i.id,ValuationKind.UNIT_PRICE,"10".toBigDecimal(),day.minusDays(1),1,at,includedInvestmentSequence=1,source=source)
        val value=WealthValuation.asset(i,PortfolioBook(listOf(i),entries,listOf(quote)),day)
        assertEquals(0,"100".toBigDecimal().compareTo(value.nativeValue!!));assertTrue(value.status.contains("estimate"))
    }
}
