package com.wealthos.finance

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.UUID

class StatementsTest {
    private val month=YearMonth.of(2026,10);private val at=Instant.parse("2026-10-10T12:00:00Z")
    private fun batch(kind:StatementKind,name:String="Fictional bank",hash:String="a")=StatementImport(UUID.randomUUID(),kind,name,hash.repeat(64),month,month.atDay(1),month.atEndOfMonth(),1,at)
    private fun row(index:Int,amount:String,direction:StatementDirection)=StatementInputRow(index,month.atDay(2),null,"Fictional merchant","",amount.toBigDecimal(),direction)
    private fun book(batch:StatementImport,rows:List<Pair<StatementInputRow,SpendingCategory>>):StatementBook {
        val lines=rows.map {StatementLine(UUID.randomUUID(),batch.id,it.first)};val review=StatementReview(UUID.randomUUID(),batch.id,1,false,true,at)
        return StatementBook(listOf(batch),lines,listOf(review),lines.mapIndexed {i,line->StatementDecision(review.id,line.id,rows[i].second,false)})
    }
    private fun combine(a:StatementBook,b:StatementBook)=StatementBook(a.imports+b.imports,a.lines+b.lines,a.reviews+b.reviews,a.decisions+b.decisions)
    @Test fun cardPurchasesAndBankSettlementDoNotDoubleSpending() {
        val card=book(batch(StatementKind.HDFC_NEU_CARD),listOf(row(1,"1000.00",StatementDirection.DEBIT) to SpendingCategory.DISCRETIONARY,row(2,"1000.00",StatementDirection.CREDIT) to SpendingCategory.CARD_PAYMENT))
        val bank=book(batch(StatementKind.HDFC_BANK,hash="b"),listOf(row(1,"1000.00",StatementDirection.DEBIT) to SpendingCategory.CARD_PAYMENT))
        val combined=combine(card,bank);StatementRules.validate(combined);val total=combined.totals(month)
        assertEquals("1000.00",total.netSpending.toPlainString());assertEquals("-1000.00",total.bankCashChange.toPlainString());assertEquals("1000.00",total.bankCardRepayments.toPlainString())
    }
    @Test fun transfersInvestmentsAndLoanPrincipalAreNotLivingSpending() {
        val b=book(batch(StatementKind.HDFC_BANK),listOf(row(1,"500.00",StatementDirection.DEBIT) to SpendingCategory.TRANSFER,row(2,"500.00",StatementDirection.CREDIT) to SpendingCategory.TRANSFER,row(3,"200.00",StatementDirection.DEBIT) to SpendingCategory.INVESTMENT))
        StatementRules.validate(b);assertEquals(0,b.totals(month).netSpending.signum());assertEquals("-200.00",b.totals(month).bankCashChange.toPlainString())
    }
    @Test fun refundMonthCanHaveNegativeNetSpendingAndNoInventedIncome() {
        val b=book(batch(StatementKind.HDFC_NEU_CARD),listOf(row(1,"50.25",StatementDirection.CREDIT) to SpendingCategory.REFUND))
        StatementRules.validate(b);assertEquals("-50.25",b.totals(month).netSpending.toPlainString());assertEquals(0,b.totals(month).bankCashChange.signum())
    }
    @Test fun equalCandidateKeysPreserveMultipleLegitimatePayments() {
        val b=book(batch(StatementKind.HDFC_BANK),listOf(row(1,"10.00",StatementDirection.DEBIT) to SpendingCategory.ESSENTIAL,row(2,"10.00",StatementDirection.DEBIT) to SpendingCategory.ESSENTIAL))
        StatementRules.validate(b);assertEquals("20.00",b.totals(month).netSpending.toPlainString())
        assertEquals(setOf(9),b.overlaps(StatementKind.HDFC_BANK,"FICTIONAL BANK",listOf(row(9,"10.00",StatementDirection.DEBIT))))
        assertTrue(b.overlaps(StatementKind.HDFC_NEU_CARD,"Fictional bank",listOf(row(9,"10.00",StatementDirection.DEBIT))).isEmpty())
    }
    @Test fun revisionsAndVoidExcludeOnlyLatestState() {
        val b=book(batch(StatementKind.HDFC_BANK),listOf(row(1,"10.00",StatementDirection.DEBIT) to SpendingCategory.ESSENTIAL))
        val review=b.reviews.single().copy(id=UUID.randomUUID(),revision=2,voided=true)
        val changed=b.copy(reviews=b.reviews+review,decisions=b.decisions+b.decisions.map {it.copy(reviewId=review.id)})
        StatementRules.validate(changed);assertEquals(0,changed.totals(month).netSpending.signum());assertTrue(changed.activeLines().isEmpty());assertFalse(changed.totals(month).sourceCoverageConfirmed)
    }
    @Test fun unresolvedLoansAndRowsCannotClaimConfirmedCoverage() {
        for(category in listOf(SpendingCategory.UNREVIEWED,SpendingCategory.LOAN_PAYMENT)) {
            val b=book(batch(StatementKind.HDFC_BANK),listOf(row(1,"50.00",StatementDirection.DEBIT) to category))
            try{StatementRules.validate(b);fail()}catch(_:IllegalArgumentException){}
            val draft=b.copy(reviews=b.reviews.map {it.copy(coverageConfirmed=false)})
            StatementRules.validate(draft);assertEquals(1,draft.totals(month).unresolved);assertFalse(draft.totals(month).sourceCoverageConfirmed)
        }
    }
    @Test fun invalidDirectionsAndMissingDecisionsFailValidation() {
        for((kind,direction,category) in listOf(Triple(StatementKind.HDFC_NEU_CARD,StatementDirection.CREDIT,SpendingCategory.INCOME),Triple(StatementKind.HDFC_BANK,StatementDirection.DEBIT,SpendingCategory.REFUND),Triple(StatementKind.HDFC_NEU_CARD,StatementDirection.DEBIT,SpendingCategory.CARD_PAYMENT))) {
            try{StatementRules.decision(kind,row(1,"50.00",direction),category);fail()}catch(_:IllegalArgumentException){}
        }
        val b=book(batch(StatementKind.HDFC_BANK),listOf(row(1,"10.00",StatementDirection.DEBIT) to SpendingCategory.ESSENTIAL))
        try{StatementRules.validate(b.copy(decisions=emptyList()));fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun crossMonthSelectionUsesTransactionDate() {
        val b=book(batch(StatementKind.HDFC_BANK),listOf(row(1,"10.00",StatementDirection.DEBIT).copy(valueDate=month.plusMonths(1).atDay(1)) to SpendingCategory.ESSENTIAL))
        StatementRules.validate(b);assertEquals("10.00",b.totals(month).netSpending.toPlainString());assertEquals(0,b.totals(month.plusMonths(1)).netSpending.signum())
    }
    @Test fun currencyIsExplicitAndUnsupportedImportsCannotBeReinterpreted() {
        val input=ParsedStatement(StatementKind.HDFC_BANK,"a".repeat(64),month.atDay(1),month.atDay(3),listOf(row(1,"10.00",StatementDirection.DEBIT)))
        assertEquals(CurrencyCode.INR,input.currency)
        try{input.copy(currency=CurrencyCode.USD);fail()}catch(_:IllegalArgumentException){}
        try{batch(StatementKind.HDFC_BANK).copy(currency=CurrencyCode.USD);fail()}catch(_:IllegalArgumentException){}
    }
}
