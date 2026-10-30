package com.wealthos.imports

import com.wealthos.finance.*
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/** Independently fictional layouts. Never read attachments or copy user rows into a test. */
class HdfcStatementsTest {
    private fun bankCsv(change:String=""):ByteArray=("""
        Currency : INR
        Statement From  :  01/09/2026         To  :  31/10/2026
        Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
        ******,******,******,******,******,******,******
        30/09/26,Fictional shop,00001,01/10/26,125.25,,874.75
        01/10/26,Fictional salary,00002,01/10/26,,500.00,1374.75
        02/10/26,Fictional bill,00003,02/10/26,74.75,,1300.00
        ******,******,******,******,******,******,******
        STATEMENT SUMMARY  :-
        Opening Balance,,,,Debits,Credits,Closing Bal
        1000.00,,,,200.00,500.00,1300.00
    """.trimIndent()+change).toByteArray()
    private fun cardCsv():ByteArray="""
        Fictional card statement
        Transaction type,Primary / Addon Customer Name,Date & Time,Description,Base NeuCoins*,AMT,Debit / Credit
        Domestic,Fictional person,30/09/2026 / 12:10,Fictional groceries,5,123.45,
        Domestic,Fictional person,01/10/2026 / 13:20,Fictional shop,,100.00,
        Domestic,Fictional person,02/10/2026 / 14:30,Fictional card payment,,1000.00,Cr

        Reward summary,,,,,999.00
        EMI summary,,,,,5000.00
    """.trimIndent().toByteArray()
    private fun reject(bytes:ByteArray){try{HdfcStatements.parse(bytes);fail("Must reject invalid input")}catch(_:Exception){}}
    @Test fun bankReconcilesAndPreservesValueDateAndTextReference() {
        val parsed=HdfcStatements.parse(bankCsv());assertEquals(StatementKind.HDFC_BANK,parsed.kind);assertEquals(3,parsed.rows.size)
        assertEquals("00001",parsed.rows.first().reference);assertEquals(LocalDate.parse("2026-10-01"),parsed.rows.first().valueDate)
        assertEquals("125.25",parsed.rows.first().amount.toPlainString());assertEquals(2,parsed.months().size)
    }
    @Test fun cardSplitsCalendarMonthsAndExcludesRewardsAndEmiSummary() {
        val parsed=HdfcStatements.parse(cardCsv());assertEquals(3,parsed.rows.size);assertEquals(2,parsed.months().size)
        assertEquals(StatementDirection.DEBIT,parsed.rows[0].direction);assertEquals(StatementDirection.CREDIT,parsed.rows[2].direction)
    }
    @Test fun badBalanceAndFooterFailRatherThanProducePartialSuccess() {
        reject(String(bankCsv()).replace("874.75","874.74").toByteArray())
        reject(String(bankCsv()).replace("200.00,500.00,1300.00","199.00,500.00,1300.00").toByteArray())
    }
    @Test fun strictDatesAndTwoDigitCenturyUsePeriod() {
        reject(String(bankCsv()).replace("30/09/26","31/09/26").toByteArray())
        val parsed=HdfcStatements.parse(String(bankCsv()).replace("2026","2126").toByteArray())
        assertEquals(2126,parsed.rows.first().date.year)
        reject(String(bankCsv()).replace("30/09/26","30/09/20").toByteArray())
        reject(String(cardCsv()).replace("30/09/2026 / 12:10","30/09/2026 / 25:10").toByteArray())
    }
    @Test fun postingDateOutsideRequestedPeriodIsPreservedRatherThanDropped() {
        val parsed=HdfcStatements.parse(String(bankCsv()).replace("31/10/2026","01/10/2026").toByteArray())
        assertEquals(LocalDate.parse("2026-10-01"),parsed.periodEnd)
        assertEquals(LocalDate.parse("2026-10-02"),parsed.lastCovered);assertEquals(3,parsed.rows.size)
    }
    @Test fun signsDirectionsCurrenciesAndPrecisionAreStrict() {
        reject(String(cardCsv()).replace(",Cr",",Reversal").toByteArray())
        reject(String(cardCsv()).replace("Domestic","International").toByteArray())
        reject(String(cardCsv()).replace("123.45","123.456").toByteArray())
        reject(String(cardCsv()).replace("123.45","-123.45").toByteArray())
        reject(String(bankCsv()).replace("Currency : INR","Currency : USD").toByteArray())
        reject(String(bankCsv()).replace("125.25,,874.75","125.25,1.00,874.75").toByteArray())
    }
    @Test fun identicalLegitimatePaymentsStaySeparate() {
        val csv=String(cardCsv()).replace("Domestic,Fictional person,01/10/2026 / 13:20,Fictional shop,,100.00,","Domestic,Fictional person,01/10/2026 / 13:20,Fictional shop,,100.00,\nDomestic,Fictional person,01/10/2026 / 13:20,Fictional shop,,100.00,")
        val parsed=HdfcStatements.parse(csv.toByteArray());assertEquals(4,parsed.rows.size)
        assertEquals(parsed.rows[1].candidateKey(),parsed.rows[2].candidateKey());assertNotEquals(parsed.rows[1].index,parsed.rows[2].index)
    }
    @Test fun quotedGroupingAndNarrationAndTabDelimitedWork() {
        val csv=String(cardCsv()).replace("Fictional groceries","\"Fictional, groceries\"").replace("123.45","\"1,23,456.78\"")
        val parsed=HdfcStatements.parse(csv.toByteArray());assertEquals("123456.78",parsed.rows[0].amount.toPlainString());assertEquals("Fictional, groceries",parsed.rows[0].description)
        assertEquals(3,HdfcStatements.parse(String(cardCsv()).replace(',','\t').toByteArray()).rows.size)
        reject(csv.replace("1,23,456.78","12,3,456.78").toByteArray())
    }
    @Test fun malformedAmbiguousAndOversizedInputsAreRejected() {
        reject("<html>not a statement</html>".toByteArray());reject(ByteArray(HdfcStatements.MAX_BYTES+1));reject(byteArrayOf(0xff.toByte()))
        reject(String(cardCsv()).replace("Fictional shop","\"Unclosed").toByteArray())
        reject(cardCsv()+cardCsv());reject(String(cardCsv()).replace("AMT","Missing amount").toByteArray())
        reject(String(cardCsv()).replace("100.00","1e2").toByteArray())
    }
    @Test fun xlsNativeNumericMoneyAndSpacedMergedCardColumnsWork() {
        val bank=xlsFromCsv(String(bankCsv()),numeric=true);assertEquals("125.25",HdfcStatements.parse(bank).rows.first().amount.toPlainString())
        val card=xlsFromCsv(String(cardCsv()),numeric=false,spaced=true)
        assertEquals(3,HdfcStatements.parse(card).rows.size)
        // Reproducible fictional binaries for native reader checks; generated outputs never read source attachments.
        val directory=java.io.File("build/fictional-fixtures").apply {mkdirs()}
        java.io.File(directory,"fictional-bank.xls").writeBytes(bank)
        java.io.File(directory,"fictional-card.xls").writeBytes(card)
    }
    @Test fun nativeNumberConversionRejectsLossyAndInvalidValues() {
        assertEquals("7.56",HdfcStatements.excelNumber(7.56))
        for(v in listOf(Double.NaN,Double.POSITIVE_INFINITY,0.1+0.2,123456789012345.67))try{HdfcStatements.excelNumber(v);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun xlsFormulasAndEmbeddedStreamsAreRejected() {
        val b=HSSFWorkbook();b.createSheet().createRow(0).createCell(0).cellFormula="1+1"
        val bytes=ByteArrayOutputStream().also {b.write(it)}.toByteArray();b.close();reject(bytes)
        val base=xlsFromCsv(String(cardCsv()),false)
        val file=org.apache.poi.poifs.filesystem.POIFSFileSystem(base.inputStream());file.root.createDirectory("_VBA_PROJECT_CUR")
        val out=ByteArrayOutputStream();file.writeFilesystem(out);file.close();reject(out.toByteArray())
    }
    private fun xlsFromCsv(csv:String,numeric:Boolean,spaced:Boolean=false):ByteArray {
        HSSFWorkbook().use {book->val sheet=book.createSheet("Fictional")
            csv.lines().forEachIndexed {r,line->val row=sheet.createRow(r);line.split(',').forEachIndexed {c,text->if(text.isNotEmpty()){
                val col=if(spaced)listOf(0,4,9,12,18,20,23).getOrElse(c){c} else c;val cell=row.createCell(col)
                if(numeric && r>=4 && c in listOf(0,4,5,6) && Regex("[0-9]+\\.[0-9]{2}").matches(text))cell.setCellValue(text.toDouble())else cell.setCellValue(text)
            }}}
            return ByteArrayOutputStream().also {book.write(it)}.toByteArray()
        }
    }
}
