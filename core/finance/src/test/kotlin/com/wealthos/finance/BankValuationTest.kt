package com.wealthos.finance

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class BankValuationTest {
    private val day = LocalDate.of(2026, 1, 31)
    private fun account(currency: CurrencyCode = CurrencyCode.INR) = BankAccount(UUID.randomUUID(), "Savings", "Bank", currency, AccountKind.SAVINGS, Instant.EPOCH)
    private fun obs(a: BankAccount, value: String, date: LocalDate = day, revision: Int = 1) =
        BalanceObservation(UUID.randomUUID(), a.id, BigDecimal(value), a.currency, date, Instant.EPOCH, revision)
    private fun decimal(expected: String, actual: BigDecimal?) = assertEquals(0, BigDecimal(expected).compareTo(actual!!))

    @Test fun decimalIsExactAtLargeValues() {
        val a = account(); val b = account()
        decimal("999999999999999999999999.99", BankValuation.at(BankBook(listOf(a,b), listOf(obs(a,"999999999999999999999999.89"),obs(b,"0.10"))),day).netWorthInr)
    }
    @Test fun inputRejectsAmbiguousAndExcessPrecision() {
        listOf("NaN", "1e3", "1,00,000", "01", "1.001", " 1", "+1", ".5", "9999999999999999999999999").forEach {
            assertThrows(IllegalArgumentException::class.java) { Amounts.parse(it, CurrencyCode.INR) }
        }
        decimal("0", Amounts.parse("-0.00", CurrencyCode.INR))
    }
    @Test fun indianFormattingPreservesPaise() {
        assertEquals("₹1,23,45,678.90", Amounts.format(BigDecimal("12345678.90"),CurrencyCode.INR))
        assertEquals("−₹1,000.50", Amounts.format(BigDecimal("-1000.50"),CurrencyCode.INR))
        assertEquals("$100,000.00", Amounts.format(BigDecimal("100000"),CurrencyCode.USD))
    }
    @Test fun futureAndBackdatedObservationsDoNotReplaceLatestEffectiveDate() {
        val a = account()
        val book = BankBook(listOf(a), listOf(obs(a,"200"),obs(a,"100",day.minusDays(1)),obs(a,"500",day.plusDays(1))))
        decimal("200",BankValuation.at(book,day).netWorthInr)
        decimal("100",BankValuation.at(book,day.minusDays(1)).netWorthInr)
    }
    @Test fun sameDayCorrectionsHaveDeterministicRevisionOrdering() {
        val a=account()
        decimal("12",BankValuation.at(BankBook(listOf(a),listOf(obs(a,"12",revision=2),obs(a,"10"))),day).netWorthInr)
    }
    @Test fun internalTransferSnapshotsPreserveWealth() {
        val a=account(); val b=account()
        val opening=listOf(obs(a,"1000",day.minusDays(1)),obs(b,"500",day.minusDays(1)))
        val book=BankBook(listOf(a,b),opening+listOf(obs(a,"700"),obs(b,"800")))
        decimal("1500",BankValuation.at(book,day).netWorthInr)
        decimal("1500",BankValuation.at(book,day.minusDays(1)).netWorthInr)
        assertEquals(4,book.observations.size)
    }
    @Test fun overdraftsAreLiabilities() {
        val a=account(); val b=account()
        val v=BankValuation.at(BankBook(listOf(a,b),listOf(obs(a,"100"),obs(b,"-30"))),day)
        decimal("100",v.assetsInr); decimal("30",v.liabilitiesInr); decimal("70",v.netWorthInr)
    }
    @Test fun missingFxMakesConsolidatedTotalUnavailable() {
        val a=account(); val b=account(CurrencyCode.USD)
        val v=BankValuation.at(BankBook(listOf(a,b),listOf(obs(a,"100"),obs(b,"50"))),day)
        assertNull(v.netWorthInr); decimal("100",v.knownInrSubtotal); assertEquals(1,v.foreignAccounts)
    }
    @Test fun missingHistoryIsUnavailableAndCarryForwardBecomesStale() {
        val a=account(); val b=account()
        val book=BankBook(listOf(a,b),listOf(obs(a,"10",day.minusDays(40)),obs(b,"20")))
        assertNull(BankValuation.at(book,day.minusDays(1)).netWorthInr)
        val v=BankValuation.at(book,day); assertEquals(1,v.staleAccounts); decimal("30",v.netWorthInr)
        assertFalse(BankValuation.history(book,day).first().complete)
    }
    @Test fun emptyBookDoesNotPretendToHaveCompleteWealth() {
        assertNull(BankValuation.at(BankBook(),day).netWorthInr)
    }
}
