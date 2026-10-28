package com.wealthos.finance

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class CurrencyCode(val scale: Int, val symbol: String) {
    INR(2, "₹"), USD(2, "$"), EUR(2, "€"), GBP(2, "£"), JPY(0, "¥")
}

/** Accounting values are decimal strings. No grouping, exponents, implicit rounding or floating point. */
object Amounts {
    fun parse(text: String, currency: CurrencyCode): BigDecimal {
        require(text.length <= 40 && Regex("-?(0|[1-9][0-9]{0,23})(\\.[0-9]+)?").matches(text)) {
            "Enter a plain number, without commas (up to 24 integer digits)."
        }
        val amount = text.toBigDecimal()
        require(amount.scale() <= currency.scale) { "${currency.name} supports ${currency.scale} decimal places." }
        return amount.setScale(currency.scale, RoundingMode.UNNECESSARY)
    }

    fun format(value: BigDecimal, currency: CurrencyCode): String = formatScale(value,currency,currency.scale)
    fun formatQuote(value:BigDecimal,currency:CurrencyCode):String = formatScale(value,currency,maxOf(currency.scale,minOf(12,value.scale())))
    private fun formatScale(value:BigDecimal,currency:CurrencyCode,scale:Int):String {
        val parts = value.abs().setScale(scale, RoundingMode.HALF_EVEN).toPlainString().split('.')
        val digits = parts[0]
        val grouped = if (currency == CurrencyCode.INR && digits.length > 3) {
            val prefix = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
            "$prefix,${digits.takeLast(3)}"
        } else digits.reversed().chunked(3).joinToString(",").reversed()
        return (if (value.signum() < 0) "−" else "") + currency.symbol + grouped +
            (if (parts.size == 2) ".${parts[1]}" else "")
    }
}

enum class AccountKind(val label: String) { SAVINGS("Savings"), CURRENT("Current"), BROKER_CASH("Brokerage cash"), CASH("Cash") }

data class BankAccount(
    val id: UUID,
    val name: String,
    val institution: String,
    val currency: CurrencyCode,
    val kind: AccountKind,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
) {
    init {
        require(name.isNotBlank() && name.length <= 80) { "Account name must contain 1–80 characters." }
        require(institution.length <= 80) { "Bank name must be at most 80 characters." }
    }
}

data class BalanceObservation(
    val id: UUID,
    val accountId: UUID,
    val amount: BigDecimal,
    val currency: CurrencyCode,
    val effectiveDate: LocalDate,
    val recordedAt: Instant,
    val revision: Int,
    val note: String = "",
) {
    init {
        Amounts.parse(amount.toPlainString(), currency)
        require(revision > 0)
        require(note.length <= 500)
    }
}

data class BankBook(val accounts: List<BankAccount> = emptyList(), val observations: List<BalanceObservation> = emptyList(), val portfolio: PortfolioBook = PortfolioBook(),val household:HouseholdBook=HouseholdBook(),val planning:PlanningBook=PlanningBook(),val publicData:PublicBook=PublicBook())

data class BalanceInput(val account: BankAccount, val observation: BalanceObservation?, val provisionalDelta: BigDecimal = BigDecimal.ZERO) {
    val value: BigDecimal? get() = observation?.amount?.plus(provisionalDelta)
    val provisional get() = provisionalDelta.signum()!=0
}

/** Bank-only net worth. Null totals mean unavailable, never a manufactured zero. */
data class NetWorth(
    val asOf: LocalDate,
    val inputs: List<BalanceInput>,
    val assetsInr: BigDecimal?,
    val liabilitiesInr: BigDecimal?,
    val netWorthInr: BigDecimal?,
    val knownInrSubtotal: BigDecimal,
    val missingAccounts: Int,
    val foreignAccounts: Int,
    val staleAccounts: Int,
) {
    val complete get() = missingAccounts == 0 && foreignAccounts == 0 && inputs.isNotEmpty()
}

object BankValuation {
    fun at(book: BankBook, date: LocalDate): NetWorth {
        val latest = book.observations.filter { it.effectiveDate <= date }
            .groupBy { it.accountId }.mapValues { (_, entries) ->
                entries.maxWith(compareBy<BalanceObservation> { it.effectiveDate }.thenBy { it.revision })
            }
        val inputs = book.accounts.map { account ->
            val observation = latest[account.id]
            val pending = book.portfolio.cashMovements.filter { movement ->
                movement.accountId == account.id && movement.date <= date && observation != null &&
                    (movement.date > observation.effectiveDate || (movement.anchorObservationId == observation.id && movement.applyToAnchor))
            }.fold(BigDecimal.ZERO) { sum,movement -> sum+movement.amount }
            BalanceInput(account, observation, pending)
        }
        inputs.forEach { input -> require(input.observation == null || input.observation.currency == input.account.currency) }
        val missing = inputs.count { it.observation == null }
        val foreign = inputs.count { it.account.currency != CurrencyCode.INR }
        val values = inputs.filter { it.account.currency == CurrencyCode.INR }.mapNotNull { it.value }
        val assets = values.filter { it.signum() >= 0 }.fold(BigDecimal.ZERO, BigDecimal::add)
        val debts = values.filter { it.signum() < 0 }.fold(BigDecimal.ZERO) { sum, v -> sum + v.abs() }
        val complete = inputs.isNotEmpty() && missing == 0 && foreign == 0
        return NetWorth(date, inputs, assets.takeIf { complete }, debts.takeIf { complete },
            (assets - debts).takeIf { complete }, assets - debts, missing, foreign,
            inputs.count { it.observation?.let { o -> ChronoUnit.DAYS.between(o.effectiveDate, date) > 30 } == true })
    }

    fun history(book: BankBook, through: LocalDate): List<NetWorth> = book.observations
        .map { it.effectiveDate }.filter { it <= through }.distinct().sorted().map { at(book, it) }
}

interface BankRepository {
    fun observe(): Flow<BankBook>
    suspend fun addAccount(name: String, institution: String, currency: CurrencyCode, kind: AccountKind,
                           amount: BigDecimal, date: LocalDate): UUID
    suspend fun editAccount(id: UUID, name: String, institution: String, kind: AccountKind)
    suspend fun recordBalance(accountId: UUID, amount: BigDecimal, date: LocalDate, note: String)
}
