package com.wealthos.imports

import com.wealthos.finance.*
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.util.NumberToTextConverter
import org.apache.poi.util.IOUtils
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Only the two verified templates. Never evaluates formulas, loads links or reads personal account metadata. */
object HdfcStatements {
    const val MAX_BYTES=256*1024
    const val MAX_ROWS=2000
    private val bankHeaders=listOf("Date","Narration","Chq./Ref.No.","Value Dt","Withdrawal Amt.","Deposit Amt.","Closing Balance")
    private val cardHeaders=listOf("Transaction type","Primary / Addon Customer Name","Date & Time","Description","Base NeuCoins*","AMT","Debit / Credit")
    private val fullDate=DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
    private val cardDate=DateTimeFormatter.ofPattern("dd/MM/uuuu / HH:mm").withResolverStyle(ResolverStyle.STRICT)
    fun read(input:InputStream):ByteArray {
        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val count=input.read(buffer);if(count<0)break;require(out.size()+count<=MAX_BYTES){"Choose a statement of at most 256 KB. Split longer exports into shorter periods."};out.write(buffer,0,count)}
        require(out.size()>0){"The statement is empty."};return out.toByteArray()
    }
    fun parse(bytes:ByteArray):ParsedStatement {
        // POI never logs a financial row or an input-derived exception, including in its isolated process.
        System.setProperty("log4j2.simplelogLevel","OFF")
        System.setProperty("log4j2.statusLoggerLevel","OFF")
        require(bytes.size in 1..MAX_BYTES){"Statement exceeds the 256 KB limit."}
        val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        val table=if(bytes.take(8).toByteArray().contentEquals(byteArrayOf(0xd0.toByte(),0xcf.toByte(),0x11,0xe0.toByte(),0xa1.toByte(),0xb1.toByte(),0x1a,0xe1.toByte())))xls(bytes) else csv(bytes)
        fun signature(headers:List<String>):Pair<Int,List<Int>>? {
            val found=table.withIndex().filter {(_,row)->headers.all {h->row.count {it==h}==1}}
            require(found.size<=1){"Repeated or ambiguous statement headers."}
            return found.singleOrNull()?.let {(index,row)->index to headers.map {row.indexOf(it)}}
        }
        val bank=signature(bankHeaders);val card=signature(cardHeaders)
        require((bank==null)!=(card==null)){"This file is not a supported HDFC Bank or Tata Neu HDFC statement. Choose the original XLS or a matching UTF-8 CSV export."}
        return if(bank!=null)bank(table,bank.first,bank.second,hash) else card(table,card!!.first,card.second,hash)
    }
    /** Numeric XLS boundary only: exact re-encoding prevents accepting rounded canonical decimals. No accounting uses Double. */
    internal fun excelNumber(value:Double):String {
        require(value.isFinite()) {"Invalid spreadsheet number."}
        val text=NumberToTextConverter.toText(value)
        require(text.length<=40 && value.toBits()==text.toBigDecimal().toDouble().toBits()){ "Spreadsheet number cannot be represented safely." }
        return text.toBigDecimal().toPlainString()
    }
    private fun xls(bytes:ByteArray):List<List<String>> {
        // Called only in the app's isolated reader process. Avoid process-wide overrides in the vault process.
        IOUtils.setByteArrayMaxOverride(2*1024*1024)
        return POIFSFileSystem(bytes.inputStream()).use {fs->
            val allowed=setOf("Workbook","Book","\u0005SummaryInformation","\u0005DocumentSummaryInformation")
            require(fs.root.entryNames.all {it in allowed}){"Embedded content, macros or encrypted workbooks are unsupported."}
            HSSFWorkbook(fs).use {book->
                require(book.numberOfSheets==1 && !book.isSheetHidden(0) && !book.isSheetVeryHidden(0)){"Choose a single-sheet original statement."}
                val sheet=book.getSheetAt(0)
                require(sheet.lastRowNum<MAX_ROWS){"Statement has too many rows."}
                var characters=0
                (0..sheet.lastRowNum).map {r->
                    val row=sheet.getRow(r)
                    require(row==null || row.lastCellNum<=64){"Statement has too many columns."}
                    List(maxOf(0,row?.lastCellNum?.toInt() ?: 0)){c->
                        val cell=row?.getCell(c)
                        val text=when(cell?.cellType) {
                            null,CellType.BLANK->""
                            CellType.STRING->cell.stringCellValue.trim()
                            CellType.NUMERIC->excelNumber(cell.numericCellValue)
                            else->throw IllegalArgumentException("Formulas, errors and unsupported cell types are not accepted.")
                        }
                        characters+=text.length
                        require(text.length<=2048 && characters<=512*1024){"Statement text exceeds the supported limit."}
                        text
                    }
                }
            }
        }
    }
    private fun csv(bytes:ByteArray):List<List<String>> {
        val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val text=decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        require(!text.contains('\u0000') && !text.trimStart().startsWith("<") && !text.startsWith("PK")){"This Excel format is unsupported. Choose the original binary XLS or UTF-8 CSV."}
        val delimiter=if(text.lineSequence().any {line->line.contains("Date\tNarration") || line.contains("Transaction type\t")})'\t' else ','
        val result=mutableListOf<List<String>>();var row=mutableListOf<String>();val cell=StringBuilder();var quoted=false;var afterQuote=false;var i=0
        fun field(){require(cell.length<=2048);row+=cell.toString().trim();cell.setLength(0);afterQuote=false;require(row.size<=64)}
        fun record(){field();result+=row;row=mutableListOf();require(result.size<=MAX_ROWS)}
        while(i<text.length) {
            val ch=text[i++]
            if(quoted) {if(ch=='"'){if(i<text.length && text[i]=='"'){cell.append('"');i++}else{quoted=false;afterQuote=true}}else cell.append(ch)}
            else when {
                ch=='"'->{require(cell.isEmpty() && !afterQuote){"Malformed CSV quotes."};quoted=true}
                ch==delimiter->field()
                ch=='\r' || ch=='\n'->{if(ch=='\r' && i<text.length && text[i]=='\n')i++;record()}
                else->{require(!afterQuote || ch==' '){"Malformed CSV field."};if(!afterQuote)cell.append(ch)}
            }
            require(cell.length<=2048){"Statement cell is too long."}
        }
        require(!quoted){"Unclosed CSV quote."}
        if(cell.isNotEmpty() || row.isNotEmpty() || afterQuote)record()
        return result
    }
    private fun value(table:List<List<String>>,r:Int,c:Int)=table.getOrNull(r)?.getOrNull(c).orEmpty()
    private fun decimal(text:String):BigDecimal {
        require(text.length in 1..24 && Regex("-?(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,2}(?:,[0-9]{2})*,[0-9]{3})(?:\\.[0-9]{1,2})?").matches(text)){"Invalid amount or grouping in statement."}
        val amount=text.replace(",","").toBigDecimal().setScale(2,RoundingMode.UNNECESSARY)
        require(amount.abs()<BigDecimal("1000000000000")){"Statement amount exceeds the supported precision."}
        Amounts.parse(amount.toPlainString(),CurrencyCode.INR)
        return amount
    }
    private fun bank(table:List<List<String>>,header:Int,columns:List<Int>,hash:String):ParsedStatement {
        fun v(r:Int,c:Int)=value(table,r,columns[c])
        require(table.take(header).flatten().any {Regex("(?i).*currency\\s*:?\\s*INR\\b.*").matches(it)}){"Only an explicitly INR bank statement is supported."}
        val periods=table.take(header).flatten().mapNotNull {text->Regex("(?i)Statement From\\s*:\\s*(\\d{2}/\\d{2}/\\d{4})\\s+To\\s*:\\s*(\\d{2}/\\d{2}/\\d{4})").find(text)}
        require(periods.size==1){"Statement period is missing or ambiguous."}
        val from=LocalDate.parse(periods.single().groupValues[1],fullDate);val to=LocalDate.parse(periods.single().groupValues[2],fullDate)
        require(from<=to && to.year-from.year<=1){"Choose a bank export covering at most two adjacent calendar years."}
        fun date(text:String):LocalDate {
            require(Regex("\\d{2}/\\d{2}/\\d{2}").matches(text)){"Invalid bank transaction date."}
            val years=maxOf(1900,from.year-1)..minOf(9999,to.year+1)
            val matches=years.filter {it%100==text.takeLast(2).toInt()}.mapNotNull {year->runCatching {LocalDate.parse(text.take(6)+year,fullDate)}.getOrNull()}
            require(matches.size==1){"Bank date has an ambiguous year or is too far from the statement period."};return matches.single()
        }
        val footer=table.indices.filter {r->v(r,0)=="Opening Balance" && v(r,4)=="Debits" && v(r,5)=="Credits" && v(r,6)=="Closing Bal"}.singleOrNull()
        require(footer!=null && footer>header){"The bank reconciliation summary is missing."}
        val summary=(footer+1 until table.size).firstOrNull {r->table[r].any {it.isNotBlank()}} ?: error("Missing bank summary amounts.")
        val rows=mutableListOf<StatementInputRow>();val balances=mutableListOf<BigDecimal>();var ended=false
        for(r in header+1 until footer) {
            val raw=table[r]
            if(raw.all {it.isBlank()})continue
            if(raw.all {it.isBlank() || it.all {ch->ch=='*' || ch=='-'}}){if(rows.isNotEmpty())ended=true;continue}
            if(ended){require(!Regex("\\d{2}/.*").matches(v(r,0)) && (v(r,4).isBlank() && v(r,5).isBlank())){"Unexpected rows after the transaction boundary."};continue}
            val debit=v(r,4).takeIf {it.isNotBlank()}?.let(::decimal) ?: BigDecimal.ZERO
            val credit=v(r,5).takeIf {it.isNotBlank()}?.let(::decimal) ?: BigDecimal.ZERO
            require(debit>=BigDecimal.ZERO && credit>=BigDecimal.ZERO && (debit>BigDecimal.ZERO)!=(credit>BigDecimal.ZERO)){"Each bank transaction must have one positive debit or credit."}
            rows+=StatementInputRow(r+1,date(v(r,0)),date(v(r,3)),v(r,1),v(r,2),maxOf(debit,credit),if(debit>BigDecimal.ZERO)StatementDirection.DEBIT else StatementDirection.CREDIT)
            balances+=decimal(v(r,6))
        }
        require(rows.isNotEmpty() && rows.size<=1500 && rows.zipWithNext().all {(a,b)->a.date<=b.date}){"Bank rows are missing or out of date order."}
        var balance=decimal(v(summary,0));var debit=BigDecimal.ZERO;var credit=BigDecimal.ZERO
        rows.forEachIndexed {i,row->if(row.direction==StatementDirection.DEBIT){balance-=row.amount;debit+=row.amount}else{balance+=row.amount;credit+=row.amount};require(balance.compareTo(balances[i])==0){"Bank running balances do not reconcile; no transactions were imported."}}
        require(debit.compareTo(decimal(v(summary,4)))==0 && credit.compareTo(decimal(v(summary,5)))==0 && balance.compareTo(decimal(v(summary,6)))==0){"Bank summary totals do not reconcile."}
        return ParsedStatement(StatementKind.HDFC_BANK,hash,rows.minOf {it.date},rows.maxOf {it.date},rows,periodStart=from,periodEnd=to)
    }
    private fun card(table:List<List<String>>,header:Int,columns:List<Int>,hash:String):ParsedStatement {
        fun v(r:Int,c:Int)=value(table,r,columns[c])
        val rows=mutableListOf<StatementInputRow>();var ended=false
        for(r in header+1 until table.size) {
            if(table[r].all {it.isBlank()}){if(rows.isNotEmpty())ended=true;continue}
            if(ended){require(v(r,0)!="Domestic" && v(r,0)!="International"){"Unexpected transaction table after the card section."};continue}
            require(v(r,0)=="Domestic"){"Only the verified domestic INR card layout is supported."}
            val timestamp=LocalDateTime.parse(v(r,2),cardDate)
            val direction=when(v(r,6)){"","Dr"->StatementDirection.DEBIT;"Cr"->StatementDirection.CREDIT;else->throw IllegalArgumentException("Unknown card debit/credit marker.")}
            rows+=StatementInputRow(r+1,timestamp.toLocalDate(),null,v(r,3),"",decimal(v(r,5)),direction)
        }
        require(rows.isNotEmpty() && rows.size<=1500){"No supported card transactions found."}
        return ParsedStatement(StatementKind.HDFC_NEU_CARD,hash,rows.minOf {it.date},rows.maxOf {it.date},rows)
    }
}
