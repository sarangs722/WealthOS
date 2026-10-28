package com.wealthos.publicdata

import com.wealthos.finance.*
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class TextParsersTest {
    private val at=Instant.parse("2026-10-27T12:00:00Z")
    private val header="Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Plan;Option;Net Asset Value;Date"
    private fun nav(rows:String)=AmfiNavParser.parse(StringReader("$header\n$rows"),"123456","INF000000001",at)
    private fun reject(block:()->Unit){try{block();fail("Invalid input accepted")}catch(_:IllegalArgumentException){}}
    @Test fun csvPreservesQuotedNumbersCommasNewlinesAndEscapedQuotes(){assertEquals(listOf(listOf("a","b,c","line\n\"two\"")),CsvRows.parse("a,\"b,c\",\"line\n\"\"two\"\"\"\r\n"))}
    @Test fun malformedOrOversizedCsvRejected(){listOf("\"unfinished","a\"b", "\"a\"b", "a\u0000b","x".repeat(512*1024+1)).forEach {reject {CsvRows.parse(it)}}}
    @Test fun selectedNavKeepsExactPrecisionAndPlanOnly(){val d=nav("999999;;;Unselected;Regular;Growth;1;27-Oct-2026\n123456;INF000000001;;Fictional fund;Direct;Growth;123.123456789012;26-Oct-2026\n")
        assertEquals("123.123456789012",d.value.toPlainString());assertTrue(d.label.contains("Direct · Growth"));assertEquals(PublicValidation.USER_FILE,d.validation);assertEquals("INF000000001",d.identity)}
    @Test fun legacySixColumnHeaderSupported(){val d=AmfiNavParser.parse(StringReader("Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Net Asset Value;Date\n123456;INF000000001;;Fictional Direct Growth;55.25;26-Oct-2026"),"123456","",at);assertEquals("55.25",d.value.toPlainString())}
    @Test fun absentDuplicateWrongIdentityAndMissingNavFailClosed(){
        listOf("999999;;;Other;Direct;Growth;1;27-Oct-2026","123456;INF000000002;;Fund;Direct;Growth;1;27-Oct-2026","123456;INF000000001;;Fund;Direct;Growth;N.A.;27-Oct-2026","123456;INF000000001;;Fund;Direct;Growth;1;27-Oct-2026\n123456;INF000000001;;Fund;Direct;Growth;2;27-Oct-2026").forEach {reject {nav(it)}}
    }
    @Test fun futureAndInvalidCalendarDatesRejected(){listOf("28-Oct-2026","31-Sep-2026").forEach {date->try{nav("123456;INF000000001;;Fund;Direct;Growth;1;$date");fail()}catch(_:Exception){}}}
    @Test fun policyRequiresExactNationalSeriesAndUnits(){val h="FREQ,REF_AREA,UNIT_MEASURE,UNIT_MULT,SOURCE_REF,TIME_PERIOD,OBS_VALUE,OBS_STATUS,OBS_CONF\n";val row="D,IN,368,0,Reserve Bank of India,2026-10-26,5.25,A,F"
        val d=BisParser.parse(h+row,PublicRequests.bis(LocalDate.parse("2026-10-27")).url,at).single();assertEquals("5.25",d.value.toPlainString())
        listOf(row.replace(",IN,",",US,"),row.replace(",368,",",999,"),row.replace(",A,F",",E,F"),row+"\n"+row).forEach {reject {BisParser.parse(h+it,d.sourceUrl,at)}}
    }
    @Test fun fixedRequestsCannotUploadPrivateFieldsOrSelectHosts(){listOf(PublicRequests.ecb(),PublicRequests.cpi(YearMonth.of(2026,9)),PublicRequests.bis(LocalDate.parse("2026-10-27"))).forEach {r->PublicRequests.validate(r);reject {PublicRequests.validate(r.copy(url=r.url+"&account=secret"))};reject {PublicRequests.validate(r.copy(url=r.url.replace("https:","http:")))}}
        reject {PublicRequests.validate(PublicRequest(PublicProvider.AMFI_FILE,"https://www.amfiindia.com/","text/plain",100))}
    }
    @Test fun explicitMissingPolicyDaysRemainGapsAndNeverBecomeZero(){
        val h="FREQ,REF_AREA,UNIT_MEASURE,UNIT_MULT,SOURCE_REF,TIME_PERIOD,OBS_VALUE,OBS_STATUS,OBS_CONF\n"
        val missing="D,IN,368,0,Reserve Bank of India,2026-10-24,NaN,M,F"
        val ordinary="D,IN,368,0,Reserve Bank of India,2026-10-23,5.25,A,F"
        val url=PublicRequests.bis(LocalDate.parse("2026-10-27")).url
        val rows=BisParser.parse(h+ordinary+"\n"+missing,url,at);assertEquals(1,rows.size);assertEquals("2026-10-23",rows.single().date.toString());assertEquals("5.25",rows.single().value.toPlainString())
        assertTrue(BisParser.parse(h+missing,url,at).isEmpty())
        reject {BisParser.parse(h+missing.replace("NaN","5.25"),url,at)}
        reject {BisParser.parse(h+ordinary.replace("5.25","NaN"),url,at)}
    }
    @Test fun consentStopsTransportBeforeAnyConnection(){reject {HttpsPublicTransport {false}.get(PublicRequests.ecb())}}
}
