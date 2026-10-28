package com.wealthos.finance

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// Covers the bounded 24-digit inputs and 100-year / 1000%-nominal contract envelope without losing paise.
internal val MC = MathContext(512, RoundingMode.HALF_EVEN)
internal val ZERO: BigDecimal = BigDecimal.ZERO

object DecimalInput {
    fun parse(text: String, scale: Int = 12): BigDecimal {
        require(text.length <= 40 && Regex("-?(0|[1-9][0-9]{0,23})(\\.[0-9]+)?").matches(text)) { "Enter a plain decimal without commas or exponents." }
        return text.toBigDecimal().also { require(it.scale() <= scale) { "Use at most $scale decimal places." } }
    }
    fun positive(text: String, scale: Int = 12) = parse(text, scale).also { require(it > ZERO) { "Value must be positive." } }
}

enum class AssetCategory(val label: String) {
    MUTUAL_FUND("Mutual funds"), STOCK("Stocks"), ETF("ETFs"), GOLD("Gold"), COMMODITY("Commodities"),
    EPF("EPF"), PPF("PPF"), NPS("NPS"), PROPERTY("Real estate"), FD("Fixed deposits"), RD("Recurring deposits"), LOAN("Loans")
}
enum class TrackingMode { TRANSACTIONS, OBSERVED, DEPOSIT, LOAN }
enum class MarketRegion { INDIA, INTERNATIONAL }
data class Instrument(
    val id: UUID, val name: String, val category: AssetCategory, val currency: CurrencyCode,
    val tracking: TrackingMode, val openedOn: LocalDate, val createdAt: Instant,
    val liquid: Boolean = false, val region: MarketRegion = MarketRegion.INDIA,
    val unit: String = "units", val custodyAccountId: UUID? = null,
) {
    init {
        require(name.isNotBlank() && name.length <= 80)
        require(unit.isNotBlank() && unit.length <= 20)
        require(custodyAccountId==null || tracking==TrackingMode.TRANSACTIONS) {"Only unit-tracked instruments belong to a custody cash account."}
        require(when(category) {
            AssetCategory.FD, AssetCategory.RD -> tracking == TrackingMode.DEPOSIT
            AssetCategory.LOAN -> tracking == TrackingMode.LOAN
            AssetCategory.EPF, AssetCategory.PPF, AssetCategory.NPS, AssetCategory.PROPERTY -> tracking == TrackingMode.OBSERVED
            else -> tracking == TrackingMode.TRANSACTIONS
        }) { "Tracking mode does not match the asset category." }
    }
}

enum class TradeKind(val label: String) {
    OPENING("Opening position"), BUY("Purchase"), SELL("Sale / redemption"), DIVIDEND("Cash distribution"),
    REINVEST("Reinvested distribution"), SPLIT("Stock split"), BONUS("Bonus issue"), ADJUST("Manual unit adjustment")
}
data class InvestmentEntry(
    val id: UUID, val instrumentId: UUID, val kind: TradeKind, val date: LocalDate,
    val sequence: Int, val units: BigDecimal = ZERO, val gross: BigDecimal? = null,
    val fees: BigDecimal = ZERO, val ratioDenominator: BigDecimal = BigDecimal.ONE,
    val note: String = "", val recordedAt: Instant,
)
data class InvestmentLot(val sourceId: UUID, val acquiredOn: LocalDate, val units: BigDecimal, val remainingCost: BigDecimal?)
data class Holding(
    val units: BigDecimal, val lots: List<InvestmentLot>, val remainingCost: BigDecimal?,
    val realizedGain: BigDecimal?, val distributions: BigDecimal, val historyComplete: Boolean,
)

/** FIFO book cost, independent of tax jurisdiction. Unknown opening basis stays unknown. */
object Holdings {
    fun calculate(entries: List<InvestmentEntry>, through: LocalDate): Holding {
        require(entries.map {it.instrumentId}.distinct().size<=1) {"Calculate each instrument's FIFO ledger separately."}
        val lots = mutableListOf<InvestmentLot>()
        var realized: BigDecimal? = ZERO
        var distributions = ZERO
        var complete = true
        fun remove(units: BigDecimal): BigDecimal? {
            require(units > ZERO && units <= lots.fold(ZERO) { a,l -> a+l.units }) { "Sale/adjustment exceeds known units on its effective date." }
            var left = units
            var cost: BigDecimal? = ZERO
            while(left > ZERO) {
                val lot = lots.first()
                val taken = left.min(lot.units)
                val entireLot=taken.compareTo(lot.units)==0
                val allocated = lot.remainingCost?.let { if(entireLot) it else it.multiply(taken,MC).divide(lot.units,MC) }
                cost = if(cost == null || allocated == null) null else cost + allocated
                if(entireLot) lots.removeAt(0)
                else lots[0] = lot.copy(units=lot.units-taken, remainingCost=lot.remainingCost?.minus(allocated!!))
                left -= taken
            }
            return cost
        }
        val ordered = entries.filter { it.date <= through }.sortedWith(compareBy<InvestmentEntry> {it.date}.thenBy {it.sequence})
        require(ordered.map {it.sequence}.distinct().size == ordered.size) { "Ledger sequence must be unique." }
        ordered.forEach { e ->
            DecimalInput.parse(e.units.toPlainString())
            require(e.fees >= ZERO && (e.gross == null || e.gross >= ZERO) && e.note.length <= 500)
            when(e.kind) {
                TradeKind.OPENING, TradeKind.BUY, TradeKind.REINVEST -> {
                    require(e.units > ZERO) { "Units must be positive." }
                    if(e.kind != TradeKind.OPENING) require(e.gross != null && e.gross > ZERO) { "Enter the actual gross consideration." }
                    if(e.kind == TradeKind.OPENING) complete = false // prior cash-flow history is not known
                    if(e.kind == TradeKind.REINVEST) distributions += e.gross!!
                    lots += InvestmentLot(e.id,e.date,e.units,e.gross?.plus(e.fees))
                }
                TradeKind.SELL -> {
                    require(e.gross != null && e.gross >= e.fees) { "Net sale proceeds cannot be negative." }
                    val basis = remove(e.units)
                    realized = if(realized == null || basis == null) null else realized + e.gross-e.fees-basis
                }
                TradeKind.DIVIDEND -> {
                    require(e.units.signum()==0 && e.gross != null && e.gross >= e.fees) { "Enter distribution proceeds, with zero units." }
                    distributions += e.gross - e.fees
                }
                TradeKind.SPLIT, TradeKind.BONUS -> {
                    require(e.units > ZERO && e.ratioDenominator > ZERO && e.fees.signum()==0) { "Enter a positive ratio, without fees." }
                    require(lots.isNotEmpty()) { "A corporate action needs existing units." }
                    val changed = lots.map { l ->
                        val units = l.units.multiply(e.units).divide(e.ratioDenominator,12,RoundingMode.UNNECESSARY).stripTrailingZeros()
                        if(e.kind == TradeKind.SPLIT) l.copy(units=units) else InvestmentLot(e.id,e.date,units,ZERO)
                    }
                    if(e.kind == TradeKind.SPLIT) lots.clear()
                    lots.addAll(changed)
                }
                TradeKind.ADJUST -> {
                    require(e.note.isNotBlank() && e.units.signum()!=0) { "Explain the non-zero unit adjustment." }
                    complete = false
                    if(e.units > ZERO) lots += InvestmentLot(e.id,e.date,e.units,e.gross)
                    else remove(e.units.abs()) // no invented realization from a manual adjustment
                }
            }
        }
        val total = lots.fold(ZERO) {a,l -> a+l.units}
        val basis = if(lots.any {it.remainingCost==null}) null else lots.fold(ZERO) {a,l -> a+l.remainingCost!!}
        return Holding(total,lots.toList(),basis,realized,distributions,complete)
    }
    fun cash(entry: InvestmentEntry): BigDecimal = when(entry.kind) {
        TradeKind.BUY -> -(requireNotNull(entry.gross)+entry.fees)
        TradeKind.SELL, TradeKind.DIVIDEND -> requireNotNull(entry.gross)-entry.fees
        TradeKind.REINVEST -> -entry.fees
        else -> ZERO
    }
}

enum class ValuationKind { UNIT_PRICE, OBSERVED_TOTAL }
data class AssetValuation(val id: UUID, val instrumentId: UUID, val kind: ValuationKind, val value: BigDecimal,
                          val date: LocalDate, val revision: Int, val recordedAt: Instant, val note: String = "",
                          val includedContractSequence:Int=0,val includedInvestmentSequence:Int=0,val source:PublicObservation?=null)
enum class DepositConvention(val label: String, val periods: Int) {
    SIMPLE_ACT_365("Simple ACT/365",0), MONTHLY("Monthly + ACT/365 stub",12),
    QUARTERLY("Quarterly + ACT/365 stub",4), ANNUAL("Annual + ACT/365 stub",1)
}
data class FinancialContract(
    val instrumentId: UUID, val openingPrincipal: BigDecimal, val annualRate: BigDecimal,
    val start: LocalDate, val end: LocalDate, val convention: DepositConvention = DepositConvention.QUARTERLY,
    val scheduledInstallment: BigDecimal = ZERO, val termMonths: Int = 0,
)
enum class ContractEventKind(val label: String) { FUNDING("Opening funding"), CONTRIBUTION("RD installment"), REPAYMENT("Loan repayment"), MATURITY("Close / maturity payout"), INTEREST_PAYOUT("Interest payout") }
data class ContractEvent(val id: UUID, val instrumentId: UUID, val kind: ContractEventKind, val date: LocalDate,
                         val sequence: Int, val principal: BigDecimal, val interest: BigDecimal = ZERO,
                         val fees: BigDecimal = ZERO, val recordedAt: Instant, val note: String = "")

/** Explicit cash anchor: apply=true is provisional until a later dated bank observation absorbs it. */
data class CashMovement(val id: UUID, val accountId: UUID, val currency: CurrencyCode, val date: LocalDate,
                        val amount: BigDecimal, val anchorObservationId: UUID, val applyToAnchor: Boolean,
                        val sourceId: UUID, val recordedAt: Instant, val note: String)
data class FxObservation(val id: UUID, val currency: CurrencyCode, val inrPerUnit: BigDecimal, val date: LocalDate,
                         val revision: Int, val recordedAt: Instant, val note: String,val source:PublicObservation?=null)
data class StatementReference(val id: UUID, val accountId: UUID, val total: BigDecimal, val date: LocalDate,
                              val revision: Int, val recordedAt: Instant)
data class PortfolioBook(
    val instruments: List<Instrument> = emptyList(), val entries: List<InvestmentEntry> = emptyList(),
    val valuations: List<AssetValuation> = emptyList(), val contracts: List<FinancialContract> = emptyList(),
    val contractEvents: List<ContractEvent> = emptyList(), val cashMovements: List<CashMovement> = emptyList(),
    val fx: List<FxObservation> = emptyList(), val references: List<StatementReference> = emptyList(),
)

data class CashLink(val accountId: UUID, val applyToSnapshot: Boolean)
data class NewInstrument(val instrument: Instrument, val openingUnits: BigDecimal = ZERO, val openingCost: BigDecimal? = null,
                         val initialValue: BigDecimal? = null, val contract: FinancialContract? = null,
                         val funding: CashLink? = null)
interface PortfolioRepository {
    suspend fun addInstrument(input: NewInstrument)
    suspend fun editInstrument(id: UUID, name: String, unit: String, region: MarketRegion, liquid: Boolean, custodyAccountId: UUID?)
    suspend fun addEntry(entry: InvestmentEntry, cash: CashLink? = null)
    suspend fun addValuation(value: AssetValuation)
    suspend fun addContractEvent(event: ContractEvent, cash: CashLink? = null)
    suspend fun addFx(currency: CurrencyCode, rate: BigDecimal, date: LocalDate, note: String)
    suspend fun transfer(from: CashLink, to: CashLink, amount: BigDecimal, date: LocalDate, note: String)
    suspend fun addReference(accountId: UUID, total: BigDecimal, date: LocalDate)
}
