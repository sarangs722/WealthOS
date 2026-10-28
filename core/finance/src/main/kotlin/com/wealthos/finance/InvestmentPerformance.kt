package com.wealthos.finance

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class InvestmentPerformance(val invested:BigDecimal?,val value:BigDecimal?,val realized:BigDecimal?,val unrealized:BigDecimal?,
    val simpleReturn:BigDecimal?,val xirr:RateResult,val cagr:RateResult,val cashFlows:List<DatedCashFlow>,val currency:CurrencyCode)
object Performance {
    fun instrument(book:BankBook,id:UUID,date:LocalDate):InvestmentPerformance {
        val instrument=requireNotNull(book.portfolio.instruments.find {it.id==id})
        require(instrument.tracking==TrackingMode.TRANSACTIONS)
        val entries=book.portfolio.entries.filter {it.instrumentId==id && it.date<=date}
        val value=WealthValuation.asset(instrument,book.portfolio,date)
        val holding=Holdings.calculate(entries,date)
        val unknown=entries.any {it.kind==TradeKind.ADJUST || (it.kind==TradeKind.OPENING && it.gross==null)}
        val invested=if(unknown)null else entries.fold(BigDecimal.ZERO){a,e->a+when(e.kind){TradeKind.OPENING,TradeKind.BUY->e.gross!!+e.fees;TradeKind.REINVEST->e.fees;else->BigDecimal.ZERO}}
        val flows=entries.mapNotNull {e->Holdings.cash(e).takeIf {it.signum()!=0}?.let {DatedCashFlow(e.date,it)}}
        val withTerminal=flows+listOfNotNull(value.nativeValue?.takeIf {it.signum()!=0}?.let {DatedCashFlow(date,it)})
        val xirr=when { !holding.historyComplete->RateResult(null,"Opening position or adjustment: earlier cash-flow history is incomplete.",withTerminal)
            value.nativeValue==null->RateResult(null,"Current valuation is missing.",withTerminal)
            else->FinancialMath.xirr(withTerminal) }
        val externalProceeds=entries.filter {it.kind==TradeKind.SELL || it.kind==TradeKind.DIVIDEND}.fold(BigDecimal.ZERO){a,e->a+Holdings.cash(e)}
        val simple=if(invested!=null && invested.signum()>0 && value.nativeValue!=null)(value.nativeValue!!+externalProceeds-invested).divide(invested,DecimalMath.context) else null
        val actual=withTerminal.groupBy {it.date}.map {(d,rows)->DatedCashFlow(d,rows.fold(BigDecimal.ZERO){a,f->a+f.amount})}.filter {it.amount.signum()!=0}.sortedBy {it.date}
        val cagr=if(holding.historyComplete && actual.size==2 && actual[0].amount.signum()<0 && actual[1].amount.signum()>0)
            FinancialMath.cagr(-actual[0].amount,actual[1].amount,actual[0].date,actual[1].date) else RateResult(null,"CAGR is only shown for a known single investment and terminal receipt/value without intervening flows.")
        return InvestmentPerformance(invested,value.nativeValue,holding.realizedGain,value.unrealizedGain,simple,xirr,cagr,withTerminal,instrument.currency)
    }
    /** Unit-tracked investments only; each actual flow uses its own dated FX, never today's rate. */
    fun portfolioXirr(book:BankBook,date:LocalDate):RateResult {
        val instruments=book.portfolio.instruments.filter {it.tracking==TrackingMode.TRANSACTIONS && it.openedOn<=date}
        if(instruments.isEmpty())return RateResult(null,"No transaction-based investments.")
        val combined=mutableListOf<DatedCashFlow>();var staleFx=false
        for(i in instruments){val p=instrument(book,i.id,date)
            if(!Holdings.calculate(book.portfolio.entries.filter {it.instrumentId==i.id},date).historyComplete)return RateResult(null,"${i.name}: opening/adjustment history is incomplete.")
            if(p.value==null)return RateResult(null,"${i.name}: current valuation unavailable.")
            for(f in p.cashFlows){val reference=WealthValuation.rate(book.portfolio,i.currency,f.date)
                if(i.currency!=CurrencyCode.INR && reference!=null && java.time.temporal.ChronoUnit.DAYS.between(reference.date,f.date)>7)staleFx=true
                val fx=if(i.currency==CurrencyCode.INR)BigDecimal.ONE else reference?.inrPerUnit
                if(fx==null)return RateResult(null,"${i.name}: missing dated ${i.currency} FX for ${f.date}.")
                combined+=DatedCashFlow(f.date,f.amount.multiply(fx,DecimalMath.context))}
        }
        val result=FinancialMath.xirr(combined)
        return if(staleFx)result.copy(status=result.status+" · Carried FX older than seven days used; inspect reference dates.") else result
    }
}
