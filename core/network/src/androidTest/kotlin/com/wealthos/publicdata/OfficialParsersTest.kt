package com.wealthos.publicdata

import com.wealthos.finance.*
import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class OfficialParsersTest {
    private val at=Instant.parse("2026-10-27T12:00:00Z")
    private val xml="""<gesmes:Envelope xmlns:gesmes="http://www.gesmes.org/xml/2002-08-01" xmlns="http://www.ecb.int/vocabulary/2002-08-01/eurofxref"><Cube><Cube time="2026-10-26"><Cube currency="INR" rate="100"/><Cube currency="USD" rate="1.25"/><Cube currency="GBP" rate="0.8"/><Cube currency="JPY" rate="160"/></Cube></Cube></gesmes:Envelope>"""
    private val json="""{"data":[{"base_year":"2024","series":"Current","year":"2026","month":"September","state":"All India","sector":"Combined","division":"CPI (General)","group":null,"class":null,"sub_class":null,"item":null,"code":null,"index":"109.491234567890","inflation":"5.54"}],"meta_data":{"page":1,"totalRecords":1,"totalPages":1},"statusCode":true}"""
    private fun reject(block:()->Unit){try{block();fail("Invalid provider response accepted")}catch(_:Exception){}}
    @Test fun ecbCrossRatesPreserveRawInputsAndReferenceDate(){val d=EcbParser.parse(xml,at);assertEquals(4,d.size);assertEquals("80.000000000000",d.single {it.currency==CurrencyCode.USD}.value.toPlainString());assertEquals("125.000000000000",d.single {it.currency==CurrencyCode.GBP}.value.toPlainString());assertEquals("0.625000000000",d.single {it.currency==CurrencyCode.JPY}.value.toPlainString());assertTrue(d.first().details.contains("INR/EUR 100"))}
    @Test fun xmlEntitiesWrongNamespacesDuplicatesAndMissingRatesRejected(){listOf("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///secret'>]>$xml",xml.replace("rate=\"100\"","rate=\"0\""),xml.replace("currency=\"GBP\"","currency=\"USD\""),xml.replace("eurofxref\"","wrong\"")).forEach {reject {EcbParser.parse(it,at)}}}
    @Test fun cpiUsesPublishedQuotedDecimalsAndNewBase(){val d=CpiParser.parse(json,PublicRequests.cpi(YearMonth.of(2026,9)).url,YearMonth.of(2026,9),at);assertEquals(2,d.size);assertEquals("109.491234567890",d.first().value.toPlainString());assertEquals("2026-09-30",d.first().date.toString());assertTrue(d.first().publication.contains("does not specify"))}
    @Test fun cpiRejectsWrongBaseSectorMonthPartialAndNumberCoercion(){listOf(json.replace("\"2024\"","\"2012\""),json.replace("Combined","Rural"),json.replace("September","August"),json.replace("\"totalRecords\":1","\"totalRecords\":2"),json.replace("\"109.491234567890\"","109.491234567890")).forEach {reject {CpiParser.parse(it,PublicRequests.cpi(YearMonth.of(2026,9)).url,YearMonth.of(2026,9),at)}}}
    @Test fun emptyMonthIsUnavailableAndNoFabricatedCpi(){val rows=CpiParser.parse("""{"data":[],"meta_data":{"totalRecords":0},"statusCode":true}""",PublicRequests.cpi(YearMonth.of(2026,9)).url,YearMonth.of(2026,9),at);assertTrue(rows.isEmpty());reject {PublicFeeds(PublicTransport {"""{"data":[],"meta_data":{"totalRecords":0},"statusCode":true}"""}){at}.fetch(PublicProvider.MOSPI)}}
    @Test fun retrievalTimestampCapturedAfterSuccessfulParsing(){var call=0;val b=PublicFeeds(PublicTransport {xml}){at.plusSeconds((call++).toLong())}.fetch(PublicProvider.ECB);assertEquals(at.plusSeconds(1),b.retrievedAt)}
    @Test fun failedSecondCpiRequestDoesNotReturnPartialBatch(){var n=0;reject {PublicFeeds(PublicTransport {if(n++==0)json else throw PublicFetchException("offline")}){at}.fetch(PublicProvider.MOSPI)};assertEquals(2,n)}
}
