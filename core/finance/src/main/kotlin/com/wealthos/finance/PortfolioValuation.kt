package com.wealthos.finance

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID

object Contracts {
    fun validate(contract: FinancialContract) {
        require(contract.openingPrincipal >= ZERO && contract.annualRate >= ZERO && contract.annualRate <= BigDecimal("10"))
        require(contract.end > contract.start && contract.end <= contract.start.plusYears(100)) { "Contract end must be after start and within 100 years." }
        require(contract.scheduledInstallment >= ZERO && contract.termMonths in 0..1200)
    }
    /** No automatic installment/payment events. Gross ACT/365 estimates; bank-specific/tax rules are not inferred. */
    fun accrued(principal: BigDecimal, annualRate: BigDecimal, start: LocalDate, end: LocalDate, convention: DepositConvention): BigDecimal {
        require(principal >= ZERO && annualRate >= ZERO && end >= start)
        if(convention == DepositConvention.SIMPLE_ACT_365) return principal + principal.multiply(annualRate,MC)
            .multiply(ChronoUnit.DAYS.between(start,end).toBigDecimal(),MC).divide(BigDecimal(365),MC)
        val months = 12L / convention.periods
        var periods = ChronoUnit.MONTHS.between(YearMonth.from(start),YearMonth.from(end))/months
        if(start.plusMonths(periods*months) > end) periods--
        val full = principal.multiply((BigDecimal.ONE+annualRate.divide(convention.periods.toBigDecimal(),MC)).pow(periods.toInt(),MC),MC)
        val stubDays = ChronoUnit.DAYS.between(start.plusMonths(periods*months),end)
        return full + full.multiply(annualRate,MC).multiply(stubDays.toBigDecimal(),MC).divide(BigDecimal(365),MC)
    }
    fun depositValue(contract: FinancialContract, events: List<ContractEvent>, through: LocalDate): BigDecimal? {
        if(through < contract.start) return null
        val actual = events.filter {it.date<=through}.sortedWith(compareBy<ContractEvent> {it.date}.thenBy {it.sequence})
        if(actual.any {it.kind==ContractEventKind.MATURITY}) return ZERO
        val end=through.coerceAtMost(contract.end)
        val deposits=listOf(contract.start to contract.openingPrincipal)+actual.filter {it.kind==ContractEventKind.CONTRIBUTION}.map {it.date to it.principal}
        val value=deposits.fold(ZERO) {sum,(date,principal) -> sum+accrued(principal,contract.annualRate,date,end,contract.convention)}
        return value-actual.filter {it.kind==ContractEventKind.INTEREST_PAYOUT}.fold(ZERO) {sum,e -> sum+e.interest}
    }
    fun loanPrincipal(contract: FinancialContract, events: List<ContractEvent>, through: LocalDate): BigDecimal? {
        if(through<contract.start) return null
        val payments=events.filter {it.date<=through && it.kind==ContractEventKind.REPAYMENT}
        return contract.openingPrincipal-payments.fold(ZERO) {sum,e -> sum+e.principal}
    }
    data class Payment(val number:Int, val opening:BigDecimal, val principal:BigDecimal, val interest:BigDecimal, val closing:BigDecimal)
    fun emi(principal:BigDecimal, annualRate:BigDecimal, months:Int, scale:Int=2):BigDecimal {
        require(principal>=ZERO && annualRate>=ZERO && months in 1..1200)
        val rate=annualRate.divide(BigDecimal(12),MC)
        if(rate.signum()==0) return principal.divide(months.toBigDecimal(),scale,RoundingMode.HALF_EVEN)
        val power=(BigDecimal.ONE+rate).pow(months,MC)
        return principal.multiply(rate,MC).multiply(power,MC).divide(power-BigDecimal.ONE,scale,RoundingMode.HALF_EVEN)
    }
    fun schedule(principal:BigDecimal,annualRate:BigDecimal,months:Int,scale:Int=2):List<Payment> {
        val payment=emi(principal,annualRate,months,scale)
        var balance=principal
        return (1..months).map {number ->
            val opening=balance
            val interest=balance.multiply(annualRate,MC).divide(BigDecimal(12),scale,RoundingMode.HALF_EVEN)
            val capital=if(number==months) balance else (payment-interest).max(ZERO).min(balance)
            balance-=capital
            Payment(number,opening,capital,interest,balance)
        }
    }
    fun cash(event:ContractEvent)=when(event.kind) {
        ContractEventKind.FUNDING, ContractEventKind.CONTRIBUTION -> -(event.principal+event.fees)
        ContractEventKind.REPAYMENT -> -(event.principal+event.interest+event.fees)
        ContractEventKind.MATURITY -> event.principal+event.interest-event.fees
        ContractEventKind.INTEREST_PAYOUT -> event.interest-event.fees
    }
}

data class AssetValue(val instrument:Instrument,val nativeValue:BigDecimal?,val date:LocalDate?,val status:String,
                      val holding:Holding?=null,val contractEstimate:BigDecimal?=null,val unitPrice:BigDecimal?=null,val snapshot:AssetValuation?=null) {
    val unrealizedGain:BigDecimal? get()=if(nativeValue!=null && holding?.remainingCost!=null) nativeValue-holding.remainingCost else null
}
data class WealthSnapshot(
    val asOf:LocalDate,val inputs:List<BalanceInput>,val assets:List<AssetValue>,
    val assetsInr:BigDecimal?,val liabilitiesInr:BigDecimal?,val netWorthInr:BigDecimal?,val liquidNetWorthInr:BigDecimal?,
    val knownInrSubtotal:BigDecimal,val missingAccounts:Int,val foreignAccounts:Int,val staleAccounts:Int,
    val allocation:Map<String,BigDecimal>,val currencyExposure:Map<CurrencyCode,BigDecimal>,val staleFx:Int,
) { val complete get()=netWorthInr!=null }

object WealthValuation {
    fun asset(instrument:Instrument,book:PortfolioBook,date:LocalDate):AssetValue {
        if(date<instrument.openedOn) return AssetValue(instrument,null,null,"Incomplete: before opening position")
        val entries=book.entries.filter {it.instrumentId==instrument.id}
        val events=book.contractEvents.filter {it.instrumentId==instrument.id && it.date<=date}
        val cutover=entries.filter {it.kind==TradeKind.OPENING}.minOfOrNull {it.date}
        val historicalSnapshot=instrument.tracking==TrackingMode.TRANSACTIONS && cutover!=null && date<cutover
        val kind=if(instrument.tracking==TrackingMode.TRANSACTIONS && !historicalSnapshot) ValuationKind.UNIT_PRICE else ValuationKind.OBSERVED_TOTAL
        val latest=book.valuations.filter {it.instrumentId==instrument.id && it.date<=date && it.kind==kind}
            .maxWithOrNull(compareBy<AssetValuation> {it.date}.thenBy {it.revision})
        val contract=book.contracts.find {it.instrumentId==instrument.id}
        if(historicalSnapshot) return AssetValue(instrument,latest?.value,latest?.date,if(latest==null)"Missing historical value" else "Historical value snapshot · carried forward",snapshot=latest)
        return when(instrument.tracking) {
            TrackingMode.TRANSACTIONS -> {
                val holding=Holdings.calculate(entries,date)
                // Published AMFI NAVs close their financial date, including actions entered later.
                val closingNav=latest?.source?.datum?.let {it.provider==PublicProvider.AMFI_FILE && it.kind==PublicKind.NAV}==true
                val actions=entries.filter {it.date<=date && it.kind in listOf(TradeKind.SPLIT,TradeKind.BONUS) && latest!=null &&
                    (it.date>latest.date || (!closingNav && it.date==latest.date && it.sequence>latest.includedInvestmentSequence))}
                    .sortedWith(compareBy<InvestmentEntry> {it.date}.thenBy {it.sequence})
                val quote=latest?.let {observation -> actions.fold(observation.value) {price,event ->
                    val numerator=if(event.kind==TradeKind.SPLIT)event.units else event.ratioDenominator+event.units
                    price.multiply(event.ratioDenominator,MC).divide(numerator,MC)
                }}
                if(holding.units.signum()==0) AssetValue(instrument,ZERO,entries.filter {it.date<=date}.maxOfOrNull {it.date} ?: instrument.openedOn,"Calculated from recorded units",holding)
                else AssetValue(instrument,quote?.multiply(holding.units,MC),latest?.date,
                    if(latest==null) "Missing unit price" else if(actions.isNotEmpty())"Corporate-action-adjusted carried quote · estimate" else latest.source?.let {PublicRules.describe(it,date)} ?: "Manual unit price · ${if(latest.date==date) "observed" else "carried forward"}",holding,unitPrice=quote)
            }
            TrackingMode.OBSERVED -> AssetValue(instrument,latest?.value,latest?.date,if(latest==null) "Missing valuation" else if(latest.calculatedTotal) "Units x price snapshot - carried forward" else "Observed total - carried forward",snapshot=latest)
            TrackingMode.LOAN -> AssetValue(instrument,contract?.let {Contracts.loanPrincipal(it,events,date)},
                events.maxOfOrNull {it.date} ?: contract?.start,"Opening principal less actual repayments")
            TrackingMode.DEPOSIT -> {
                val estimate=contract?.let {Contracts.depositValue(it,events,date)}
                if(events.any {it.kind==ContractEventKind.MATURITY}) AssetValue(instrument,ZERO,events.maxOf {it.date},"Closed · actual payout recorded",contractEstimate=estimate)
                else if(latest!=null) {
                    val laterEvents=events.any {it.kind!=ContractEventKind.FUNDING && (it.date>latest.date || it.sequence>latest.includedContractSequence)}
                    AssetValue(instrument,latest.value.takeUnless {laterEvents},latest.date,
                        if(laterEvents) "Update observed value: later cash events exist" else "Observed total · carried forward",contractEstimate=estimate)
                } else AssetValue(instrument,estimate,contract?.end?.coerceAtMost(date),
                    if(contract!=null && date>=contract.end) "Matured · estimate held, record actual payout" else "Contract estimate · gross, before tax",contractEstimate=estimate)
            }
        }
    }
    fun rate(book:PortfolioBook,currency:CurrencyCode,date:LocalDate):FxObservation? = book.fx.filter {it.currency==currency && it.date<=date}
        .maxWithOrNull(compareBy<FxObservation> {it.date}.thenBy {it.revision})
    fun at(book:BankBook,date:LocalDate):WealthSnapshot {
        val banks=BankValuation.at(book,date)
        val values=book.portfolio.instruments.map {asset(it,book.portfolio,date)}
        var totalAssets=ZERO; var debt=ZERO; var liquid=ZERO; var missing=0; var foreign=0; var stale=0; var oldFx=0
        val allocation=linkedMapOf<String,BigDecimal>(); val exposure=linkedMapOf<CurrencyCode,BigDecimal>()
        fun include(value:BigDecimal?,currency:CurrencyCode,label:String,isDebt:Boolean,isLiquid:Boolean,observed:LocalDate?) {
            if(value==null) {missing++;return}
            val signed=if(isDebt) -value else value
            exposure[currency]=(exposure[currency] ?: ZERO)+signed
            if(observed!=null && ChronoUnit.DAYS.between(observed,date)>30) stale++
            val fx=if(currency==CurrencyCode.INR) BigDecimal.ONE else rate(book.portfolio,currency,date)?.let {
                if(ChronoUnit.DAYS.between(it.date,date)>7) oldFx++
                it.inrPerUnit
            }
            if(fx==null) {if(value.signum()!=0) foreign++;return}
            val inr=signed.multiply(fx,MC)
            if(inr.signum()<0) debt-=inr else {
                totalAssets+=inr
                if(isLiquid) liquid+=inr
                allocation[label]=(allocation[label] ?: ZERO)+inr
            }
        }
        banks.inputs.forEach { include(it.value,it.account.currency,"Cash",false,true,it.observation?.effectiveDate) }
        values.forEach {include(it.nativeValue,it.instrument.currency,it.instrument.category.label,it.instrument.category==AssetCategory.LOAN,it.instrument.liquid,it.date)}
        val valid=missing==0 && foreign==0 && (book.accounts.isNotEmpty() || values.isNotEmpty())
        return WealthSnapshot(date,banks.inputs,values,totalAssets.takeIf {valid},debt.takeIf {valid},(totalAssets-debt).takeIf {valid},
            (liquid-debt).takeIf {valid},totalAssets-debt,missing,foreign,stale,allocation,exposure,oldFx)
    }
    fun history(book:BankBook,through:LocalDate):List<WealthSnapshot> {
        val p=book.portfolio
        val dates=book.observations.map {it.effectiveDate}+p.instruments.map {it.openedOn}+p.entries.map {it.date}+
            p.valuations.map {it.date}+p.contractEvents.map {it.date}+p.cashMovements.map {it.date}+p.fx.map {it.date}
        return (dates+listOfNotNull(through.takeIf {dates.any {d->d<=through}})).filter {it<=through}.distinct().sorted().map {at(book,it)}
    }
    /** Statement total is only a reference, never an asset. All components use the reference's financial date. */
    fun reconcile(book:BankBook,reference:StatementReference):BigDecimal? {
        val account=book.accounts.find {it.id==reference.accountId} ?: return null
        val cash=BankValuation.at(book,reference.date).inputs.find {it.account.id==account.id}?.value ?: return null
        val holdings=book.portfolio.instruments.filter {it.custodyAccountId==account.id}
        if(holdings.any {it.currency!=account.currency}) return null
        val values=holdings.map {asset(it,book.portfolio,reference.date).nativeValue}
        if(values.any {it==null}) return null
        return cash+values.filterNotNull().fold(ZERO,BigDecimal::add)-reference.total
    }
}
