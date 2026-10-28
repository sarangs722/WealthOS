package com.wealthos.finance

import java.math.BigDecimal
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class PublicProvider(val label:String,val home:String,val terms:String,val network:Boolean){
    ECB("ECB reference FX","https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html","https://www.ecb.europa.eu/services/using-our-site/disclaimer/html/index.en.html",true),
    MOSPI("MoSPI CPI","https://esankhyiki.mospi.gov.in/","https://datainnovation.mospi.gov.in/mospi-mcp",true),
    BIS("RBI policy rate via BIS","https://data.bis.org/topics/CBPOL","https://data.bis.org/help/legal",true),
    AMFI_FILE("AMFI NAV file (user supplied)","https://www.amfiindia.com/","https://www.amfiindia.com/terms-of-use",false),
    MANUAL("Manual economic reference","https://www.rbi.org.in/","https://www.rbi.org.in/",false)
}
enum class PublicKind(val label:String,val recentDays:Long){NAV("Mutual fund NAV",4),FX("FX reference",4),CPI_INDEX("CPI index",45),CPI_YOY("CPI year-on-year inflation",45),POLICY_RATE("India policy repo rate",14),BANK_RATE("Bank rate reference",30)}
enum class PublicValidation(val label:String){HTTPS_VALIDATED("Validated official HTTPS response"),USER_FILE("Format validated; user-supplied file, publisher not authenticated"),MANUAL("User-entered reference; not independently verified")}
data class PublicDatum(val provider:PublicProvider,val kind:PublicKind,val series:String,val label:String,val value:BigDecimal,val currency:CurrencyCode?,
    val units:String,val date:LocalDate,val sourceUrl:String,val validation:PublicValidation,val basis:String,val details:String,val publication:String,val identity:String="")
data class PublicObservation(val id:UUID,val datum:PublicDatum,val retrievedAt:Instant,val revision:Int,val fingerprint:String)
data class NavBinding(val id:UUID,val instrumentId:UUID,val schemeCode:String,val isin:String,val active:Boolean,val revision:Int,val recordedAt:Instant)
data class PublicRun(val id:UUID,val provider:PublicProvider,val attemptedAt:Instant,val finishedAt:Instant,val success:Boolean,val count:Int,val message:String)
data class PublicApplication(val id:UUID,val observationId:UUID,val instrumentId:UUID?,val valuationId:UUID?,val fxId:UUID?,val appliedAt:Instant)
data class PublicBook(val networkEnabled:Boolean=false,val observations:List<PublicObservation> = emptyList(),val bindings:List<NavBinding> = emptyList(),
    val runs:List<PublicRun> = emptyList(),val applications:List<PublicApplication> = emptyList()) {
    fun currentBindings()=bindings.groupBy {it.instrumentId}.values.map {it.maxBy {b->b.revision}}.filter {it.active}
    fun latest()=observations.groupBy {it.datum.provider to it.datum.series}.values.map {it.maxWith(compareBy<PublicObservation>{o->o.datum.date}.thenBy {o->o.revision})}
}
object PublicRules {
    fun freshness(observation:PublicObservation,today:LocalDate):String {
        val days=ChronoUnit.DAYS.between(observation.datum.date,today)
        return when {days<0->"Future date / invalid";days>observation.datum.kind.recentDays->"Stale last-known reference · $days days old";else->"Recent last-known reference · $days days old"}
    }
    fun describe(observation:PublicObservation,date:LocalDate)="${observation.datum.provider.label} · ${observation.datum.validation.label} · ${freshness(observation,date)}"
    fun validate(d:PublicDatum,retrievedAt:Instant){
        // A financial observation date belongs to its source calendar, not the UTC retrieval day.
        // Indian references can already be dated tomorrow in UTC shortly after local midnight.
        val sourceZone=if(d.provider==PublicProvider.ECB)ZoneId.of("Europe/Berlin")else ZoneId.of("Asia/Kolkata")
        require(d.date.year>=1900 && d.date<=retrievedAt.atZone(sourceZone).toLocalDate()) {"Public observation is future-dated or invalid."}
        require(d.series.isNotBlank() && d.series.length<=100 && d.label.isNotBlank() && d.label.length<=240 && d.units.length in 1..80 && d.basis.length<=100 && d.details.length<=1600 && d.publication.length in 1..160 && d.identity.length<=80)
        DecimalInput.parse(d.value.toPlainString())
        if(d.kind==PublicKind.CPI_YOY)require(d.value>BigDecimal(-100) && d.value<=BigDecimal(1000)) else require(d.value>=BigDecimal.ZERO)
        if(d.kind in listOf(PublicKind.FX,PublicKind.NAV,PublicKind.CPI_INDEX))require(d.value>BigDecimal.ZERO) {"A missing or nonpositive reference cannot become a valuation."}
        if(d.kind in listOf(PublicKind.POLICY_RATE,PublicKind.BANK_RATE))require(d.value<=BigDecimal(100))
        val uri=URI(d.sourceUrl);require(d.sourceUrl.length<=600 && uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.userInfo==null && uri.port in listOf(-1,443) && uri.fragment==null) {"Use a valid HTTPS source reference."}
        when(d.provider){
            PublicProvider.ECB->require(d.kind==PublicKind.FX && d.currency!=null && d.currency!=CurrencyCode.INR && d.units=="INR per native unit" && d.validation==PublicValidation.HTTPS_VALIDATED && d.sourceUrl=="https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml")
            PublicProvider.MOSPI->require(d.kind in listOf(PublicKind.CPI_INDEX,PublicKind.CPI_YOY) && d.currency==null && d.basis=="2024=100; All India; Combined; General" && d.validation==PublicValidation.HTTPS_VALIDATED && uri.host=="api.mospi.gov.in" && uri.path=="/api/cpi/getCPIData")
            PublicProvider.BIS->require(d.kind==PublicKind.POLICY_RATE && d.currency==null && d.units=="percent per year" && d.validation==PublicValidation.HTTPS_VALIDATED && uri.host=="stats.bis.org" && uri.path=="/api/v2/data/dataflow/BIS/WS_CBPOL/1.0/D.IN")
            PublicProvider.AMFI_FILE->require(d.kind==PublicKind.NAV && d.currency==CurrencyCode.INR && d.units=="INR per unit" && d.validation==PublicValidation.USER_FILE && d.sourceUrl==PublicProvider.AMFI_FILE.home && Regex("NAV:[0-9]{5,9}").matches(d.series))
            PublicProvider.MANUAL->require(d.kind in listOf(PublicKind.CPI_INDEX,PublicKind.CPI_YOY,PublicKind.POLICY_RATE,PublicKind.BANK_RATE) && d.validation==PublicValidation.MANUAL && d.currency==null)
        }
    }
}
interface PublicDataRepository {
    suspend fun setNetwork(enabled:Boolean)
    suspend fun saveBinding(binding:NavBinding)
    suspend fun cache(provider:PublicProvider,rows:List<PublicDatum>,attemptedAt:Instant,retrievedAt:Instant,message:String)
    suspend fun recordFailure(provider:PublicProvider,attemptedAt:Instant,message:String)
    suspend fun applyNav(observationId:UUID,instrumentId:UUID,replaceSameDay:Boolean)
    suspend fun applyFx(observationId:UUID,replaceSameDay:Boolean)
}
