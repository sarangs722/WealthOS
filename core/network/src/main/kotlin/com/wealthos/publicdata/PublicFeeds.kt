package com.wealthos.publicdata

import com.wealthos.finance.*
import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import javax.net.ssl.HttpsURLConnection

data class PublicRequest(val provider:PublicProvider,val url:String,val accept:String,val maxBytes:Int)
data class PublicBatch(val provider:PublicProvider,val rows:List<PublicDatum>,val retrievedAt:Instant,val message:String)
fun interface PublicTransport {fun get(request:PublicRequest):String}
class PublicFetchException(message:String):IllegalArgumentException(message)
object PublicRequests {
    const val ECB="https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"
    const val BIS="https://stats.bis.org/api/v2/data/dataflow/BIS/WS_CBPOL/1.0/D.IN"
    const val CPI="https://api.mospi.gov.in/api/cpi/getCPIData"
    fun ecb()=PublicRequest(PublicProvider.ECB,ECB,"text/xml, application/xml",128*1024)
    fun bis(today:LocalDate)=PublicRequest(PublicProvider.BIS,"$BIS?startPeriod=${today.minusDays(60)}&endPeriod=$today&format=csv","text/csv",512*1024)
    fun cpi(month:YearMonth)=PublicRequest(PublicProvider.MOSPI,"$CPI?base_year=2024&series=Current&year=${month.year}&month_code=${month.monthValue}&state_code=1&sector_code=3&division_code=0&limit=10&page=1&Format=JSON","application/json",128*1024)
    fun validate(r:PublicRequest){
        require(r.maxBytes in 1..512*1024)
        val valid=when(r.provider){
            PublicProvider.ECB->r==ecb()
            PublicProvider.BIS->Regex(Regex.escape(BIS)+"\\?startPeriod=[0-9]{4}-[0-9]{2}-[0-9]{2}&endPeriod=[0-9]{4}-[0-9]{2}-[0-9]{2}&format=csv").matches(r.url) && r.accept=="text/csv"
            PublicProvider.MOSPI->Regex(Regex.escape(CPI)+"\\?base_year=2024&series=Current&year=[0-9]{4}&month_code=(?:[1-9]|1[0-2])&state_code=1&sector_code=3&division_code=0&limit=10&page=1&Format=JSON").matches(r.url) && r.accept=="application/json"
            else->false
        }
        require(valid){"Only fixed public statistical requests are permitted."}
    }
}
/** No credentials, finance fields, cookies or user-selected URLs are supplied to this transport. */
class HttpsPublicTransport(private val allowed:()->Boolean):PublicTransport {
    override fun get(request:PublicRequest):String {
        PublicRequests.validate(request);if(!allowed())throw PublicFetchException("Public network refresh is disabled.")
        val c=URL(request.url).openConnection() as HttpsURLConnection
        try {
            c.connectTimeout=15000;c.readTimeout=15000;c.instanceFollowRedirects=false;c.useCaches=false;c.requestMethod="GET"
            c.setRequestProperty("Accept",request.accept);c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("User-Agent","WealthOS/0.5 public-reference-client")
            val status=c.responseCode
            if(status!=200)throw PublicFetchException(if(status==429)"Provider rate limit reached; retain cached references and try later." else "Provider returned HTTP $status; cached references were retained.")
            val type=c.contentType?.substringBefore(';')?.lowercase() ?: ""
            val accepted=when(request.provider){PublicProvider.ECB->type in listOf("text/xml","application/xml");PublicProvider.MOSPI->type=="application/json";PublicProvider.BIS->type in listOf("text/csv","application/vnd.sdmx.data+csv");else->false}
            if(!accepted)throw PublicFetchException("Provider response type changed; no observations were replaced.")
            if(c.contentEncoding?.let {it!="identity"}==true)throw PublicFetchException("Unsupported response encoding.")
            val buffer=ByteArrayOutputStream();val bytes=ByteArray(4096)
            c.inputStream.use {input->while(true){if(!allowed())throw PublicFetchException("Refresh disabled before completion.");val read=input.read(bytes);if(read<0)break;if(buffer.size()+read>request.maxBytes)throw PublicFetchException("Provider response exceeds its size limit.");buffer.write(bytes,0,read)}}
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(buffer.toByteArray())).toString()
        } finally {c.disconnect()}
    }
}
class PublicFeeds(private val transport:PublicTransport,private val now:()->Instant={Instant.now()}) {
    fun fetch(provider:PublicProvider):PublicBatch {
        val at=now();val today=at.atZone(ZoneOffset.UTC).toLocalDate()
        val rows=when(provider){
            PublicProvider.ECB->EcbParser.parse(transport.get(PublicRequests.ecb()),at)
            PublicProvider.BIS->{val request=PublicRequests.bis(today);BisParser.parse(transport.get(request),request.url,at)}
            PublicProvider.MOSPI->{val result=mutableListOf<PublicDatum>();for(offset in 1L..2L){val month=YearMonth.from(today).minusMonths(offset);val request=PublicRequests.cpi(month);result+=CpiParser.parse(transport.get(request),request.url,month,at)};result}
            else->throw PublicFetchException("This source requires a personal file import or manual reference.")
        }
        if(rows.isEmpty())throw PublicFetchException("No published observations found; last-known references retained.")
        val retrieved=now()
        rows.forEach {PublicRules.validate(it,retrieved)}
        return PublicBatch(provider,rows,retrieved,"${rows.size} validated public observations cached for review. Financial records and assumptions were not changed.")
    }
}
