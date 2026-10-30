package com.wealthos.finance

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class StatementKind(val label:String) { HDFC_BANK("HDFC Bank"), HDFC_NEU_CARD("Tata Neu HDFC card") }
enum class StatementDirection { DEBIT, CREDIT }
enum class SpendingCategory(val label:String) {
    UNREVIEWED("Needs review"), ESSENTIAL("Essential spending"), DISCRETIONARY("Other spending"),
    INCOME("Income"), REFUND("Refund"), TRANSFER("Own transfer"), CARD_PAYMENT("Card repayment"),
    INVESTMENT("Investment"), LOAN_PAYMENT("Loan payment · split unknown"), EXCLUDED("Exclude"), DUPLICATE("Already imported")
}

data class StatementInputRow(val index:Int,val date:LocalDate,val valueDate:LocalDate?,val description:String,
    val reference:String,val amount:BigDecimal,val direction:StatementDirection) {
    init {
        require(index in 1..10000 && date.year in 1900..9999 && (valueDate==null || valueDate.year in 1900..9999))
        require(description.isNotBlank() && description.length<=500 && reference.length<=120)
        require(amount>BigDecimal.ZERO);Amounts.parse(amount.toPlainString(),CurrencyCode.INR)
    }
    /** Candidate identity only. Equal keys never authorize deleting repeated legitimate payments. */
    fun candidateKey()=listOf(date.toString(),description.trim(),reference.trim(),amount.stripTrailingZeros().toPlainString(),direction.name)
}
data class ParsedStatement(val kind:StatementKind,val fileHash:String,val firstCovered:LocalDate,val lastCovered:LocalDate,
    val rows:List<StatementInputRow>,val parserVersion:Int=1,val periodStart:LocalDate?=null,val periodEnd:LocalDate?=null,val currency:CurrencyCode=CurrencyCode.INR) {
    init {
        require(Regex("[0-9a-f]{64}").matches(fileHash) && parserVersion==1 && rows.isNotEmpty() && rows.size<=1500)
        require(currency==CurrencyCode.INR){"Only INR statement imports are supported."}
        require(firstCovered<=lastCovered && firstCovered.year>=1900 && lastCovered.year<=9999)
        require(rows.map {it.index}.distinct().size==rows.size && rows.all {it.date in firstCovered..lastCovered})
        require((periodStart==null)==(periodEnd==null) && (periodStart==null || (periodStart.year in 1900..9999 && periodEnd!!.year in 1900..9999 && periodStart<=periodEnd)))
    }
    fun months()=rows.map {YearMonth.from(it.date)}.distinct().sortedDescending()
}
data class StatementImport(val id:UUID,val kind:StatementKind,val sourceName:String,val fileHash:String,val month:YearMonth,
    val firstCovered:LocalDate,val lastCovered:LocalDate,val parserVersion:Int,val createdAt:Instant,val periodStart:LocalDate?=null,val periodEnd:LocalDate?=null,val currency:CurrencyCode=CurrencyCode.INR) {
    init {
        require(sourceName==sourceName.trim() && sourceName.length in 1..60)
        require(currency==CurrencyCode.INR){"Only INR statement imports are supported."}
        require(Regex("[0-9a-f]{64}").matches(fileHash) && parserVersion==1 && month.year in 1900..9999)
        require(firstCovered<=lastCovered && firstCovered.year>=1900 && lastCovered.year<=9999)
        require((periodStart==null)==(periodEnd==null) && (periodStart==null || (periodStart.year in 1900..9999 && periodEnd!!.year in 1900..9999 && periodStart<=periodEnd)))
    }
}
data class StatementLine(val id:UUID,val importId:UUID,val row:StatementInputRow)
data class StatementReview(val id:UUID,val importId:UUID,val revision:Int,val voided:Boolean,val coverageConfirmed:Boolean,val recordedAt:Instant)
data class StatementDecision(val reviewId:UUID,val lineId:UUID,val category:SpendingCategory,val overlapKept:Boolean)
data class StatementBook(val imports:List<StatementImport> = emptyList(),val lines:List<StatementLine> = emptyList(),
    val reviews:List<StatementReview> = emptyList(),val decisions:List<StatementDecision> = emptyList()) {
    fun latest(id:UUID)=reviews.filter {it.importId==id}.maxByOrNull {it.revision}
    fun activeLines()=lines.filter {latest(it.importId)?.voided==false}
    fun overlaps(kind:StatementKind,sourceName:String,rows:List<StatementInputRow>,excluding:UUID?=null):Set<Int> {
        val ids=imports.filter {it.id!=excluding && it.kind==kind && it.sourceName.equals(sourceName.trim(),ignoreCase=true)}.map {it.id}.toSet()
        val keys=activeLines().filter {it.importId in ids}.filter {line->
            decisions.singleOrNull {it.lineId==line.id && it.reviewId==latest(line.importId)?.id}?.category !in listOf(SpendingCategory.DUPLICATE,SpendingCategory.EXCLUDED)
        }.map {it.row.candidateKey()}.toSet()
        return rows.filter {it.candidateKey() in keys}.map {it.index}.toSet()
    }
    fun totals(month:YearMonth):StatementTotals {
        var spending=BigDecimal.ZERO;var refunds=BigDecimal.ZERO;var bankNet=BigDecimal.ZERO;var repayments=BigDecimal.ZERO;var unresolved=0;var reviewed=0
        val selected=imports.filter {it.month==month && latest(it.id)?.voided==false}
        for(batch in selected) {
            val review=latest(batch.id)!!
            for(line in lines.filter {it.importId==batch.id}) {
                val category=decisions.single {it.reviewId==review.id && it.lineId==line.id}.category
                if(category !in listOf(SpendingCategory.UNREVIEWED,SpendingCategory.LOAN_PAYMENT))reviewed++
                if(category in listOf(SpendingCategory.DUPLICATE,SpendingCategory.EXCLUDED))continue
                val r=line.row
                if(batch.kind==StatementKind.HDFC_BANK)bankNet+=if(r.direction==StatementDirection.CREDIT)r.amount else -r.amount
                when(category) {
                    SpendingCategory.ESSENTIAL,SpendingCategory.DISCRETIONARY->spending+=r.amount
                    SpendingCategory.REFUND->refunds+=r.amount
                    SpendingCategory.CARD_PAYMENT->if(batch.kind==StatementKind.HDFC_BANK)repayments+=r.amount
                    SpendingCategory.UNREVIEWED,SpendingCategory.LOAN_PAYMENT->unresolved++
                    else->Unit
                }
            }
        }
        return StatementTotals(spending,refunds,bankNet,repayments,unresolved,reviewed,selected.size,
            selected.isNotEmpty() && unresolved==0 && selected.all {latest(it.id)!!.coverageConfirmed})
    }
}
data class StatementTotals(val grossSpending:BigDecimal,val refunds:BigDecimal,val bankCashChange:BigDecimal,
    val bankCardRepayments:BigDecimal,val unresolved:Int,val reviewedRows:Int,val sources:Int,val sourceCoverageConfirmed:Boolean) {
    val netSpending get()=grossSpending-refunds
}
object StatementRules {
    fun decision(kind:StatementKind,row:StatementInputRow,category:SpendingCategory) {
        when(category) {
            SpendingCategory.ESSENTIAL,SpendingCategory.DISCRETIONARY,SpendingCategory.INVESTMENT,SpendingCategory.LOAN_PAYMENT->require(row.direction==StatementDirection.DEBIT){"Choose a debit for this category."}
            SpendingCategory.REFUND->require(row.direction==StatementDirection.CREDIT){"A refund must be a credit."}
            SpendingCategory.INCOME->require(kind==StatementKind.HDFC_BANK && row.direction==StatementDirection.CREDIT){"Card credits are refunds, repayments or adjustments, not bank income."}
            SpendingCategory.CARD_PAYMENT->require(row.direction==if(kind==StatementKind.HDFC_BANK)StatementDirection.DEBIT else StatementDirection.CREDIT){"Check the card repayment direction."}
            else->Unit
        }
    }
    fun validate(book:StatementBook) {
        require(book.imports.map {it.id}.distinct().size==book.imports.size)
        require(book.imports.map {it.fileHash to it.month}.distinct().size==book.imports.size)
        require(book.lines.map {it.id}.distinct().size==book.lines.size)
        require(book.reviews.map {it.id}.distinct().size==book.reviews.size)
        val linesByImport=book.lines.groupBy {it.importId};val reviewsByImport=book.reviews.groupBy {it.importId}
        val decisionsByReview=book.decisions.groupBy {it.reviewId}
        for(batch in book.imports) {
            val lines=linesByImport[batch.id].orEmpty();val reviews=reviewsByImport[batch.id].orEmpty().sortedBy {it.revision}
            val byId=lines.associateBy {it.id}
            require(lines.isNotEmpty() && lines.size<=1500 && lines.map {it.row.index}.distinct().size==lines.size)
            require(lines.all {YearMonth.from(it.row.date)==batch.month && it.row.date in batch.firstCovered..batch.lastCovered})
            require(reviews.isNotEmpty() && reviews.map {it.revision}==(1..reviews.size).toList())
            for(review in reviews) {
                val decisions=decisionsByReview[review.id].orEmpty()
                require(decisions.size==lines.size && decisions.map {it.lineId}.toSet()==lines.map {it.id}.toSet())
                decisions.forEach {d->decision(batch.kind,byId.getValue(d.lineId).row,d.category)}
                require(!review.coverageConfirmed || decisions.none {it.category in listOf(SpendingCategory.UNREVIEWED,SpendingCategory.LOAN_PAYMENT)})
            }
        }
        val imports=book.imports.map {it.id}.toSet();val reviews=book.reviews.map {it.id}.toSet();val lines=book.lines.map {it.id}.toSet()
        require(book.lines.all {it.importId in imports} && book.reviews.all {it.importId in imports})
        require(book.decisions.all {it.reviewId in reviews && it.lineId in lines})
    }
}
