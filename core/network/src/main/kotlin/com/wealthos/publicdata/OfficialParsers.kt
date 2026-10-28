package com.wealthos.publicdata

import com.wealthos.finance.*
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object EcbParser {
    fun parse(text:String,at:Instant):List<PublicDatum> {
        require(text.length<=128*1024 && '\u0000' !in text && !text.contains("<!DOCTYPE",true) && !text.contains("<!ENTITY",true)){"Unsafe or oversized FX XML."}
        val parser=XmlPullParserFactory.newInstance().apply {isNamespaceAware=true}.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);parser.setInput(StringReader(text))
        val rates=linkedMapOf<String,BigDecimal>();var date:LocalDate?=null;var envelope=false;var elements=0
        while(parser.eventType!=XmlPullParser.END_DOCUMENT){if(parser.eventType==XmlPullParser.START_TAG){elements++;require(elements<=1000)
            if(parser.depth==1)require(parser.name=="Envelope")
            if(parser.name=="Envelope"){require(!envelope && parser.depth==1 && parser.namespace=="http://www.gesmes.org/xml/2002-08-01");envelope=true}
            if(parser.name=="Cube"){
                require(parser.namespace=="http://www.ecb.int/vocabulary/2002-08-01/eurofxref")
                require(envelope && parser.depth in 2..4)
                parser.getAttributeValue(null,"time")?.let {require(date==null && parser.depth==3);date=LocalDate.parse(it)}
                parser.getAttributeValue(null,"currency")?.let {code->require(date!=null && parser.depth==4 && code!="EUR" && Regex("[A-Z]{3}").matches(code) && code !in rates);rates[code]=DecimalInput.positive(requireNotNull(parser.getAttributeValue(null,"rate")))}
            }
        };parser.next()}
        require(envelope && date!=null && listOf("INR","USD","GBP","JPY").all {it in rates}){"Incomplete ECB currency/date table."}
        val inr=rates.getValue("INR")
        return listOf(CurrencyCode.EUR,CurrencyCode.USD,CurrencyCode.GBP,CurrencyCode.JPY).map {currency->val raw=if(currency==CurrencyCode.EUR)BigDecimal.ONE else rates.getValue(currency.name)
            PublicDatum(PublicProvider.ECB,PublicKind.FX,"FX:${currency.name}","${currency.name} to INR reference",inr.divide(raw,12,RoundingMode.HALF_EVEN),currency,"INR per native unit",date!!,PublicRequests.ECB,PublicValidation.HTTPS_VALIDATED,"EUR-base cross rate","Source: ECB. Derived INR/${currency.name} = INR/EUR ${inr.toPlainString()} divided by ${currency.name}/EUR ${raw.toPlainString()}, HALF_EVEN to 12 decimals. Reference only; not an execution rate.","ECB daily reference; source publication date")
        }.also {rows->rows.forEach {PublicRules.validate(it,at)}}
    }
}
object CpiParser {
    fun parse(text:String,url:String,month:YearMonth,at:Instant):List<PublicDatum> {
        require(text.length<=128*1024 && '\u0000' !in text)
        val root=JSONObject(text);require(root.getBoolean("statusCode")){"CPI provider reported an unsuccessful request."}
        val data=root.getJSONArray("data");val meta=root.getJSONObject("meta_data")
        if(data.length()==0){require(meta.getInt("totalRecords")==0);return emptyList()}
        require(data.length()==1 && meta.getInt("totalRecords")==1 && meta.getInt("totalPages")==1 && meta.getInt("page")==1){"CPI response is partial or ambiguously filtered."}
        val row=data.getJSONObject(0)
        require(row.getString("base_year")=="2024" && row.getString("series")=="Current" && row.getString("state")=="All India" && row.getString("sector")=="Combined" && row.getString("division")=="CPI (General)"){"Wrong CPI base or national/sector series."}
        require(row.getString("year")==month.year.toString() && row.getString("month")==month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM",Locale.ENGLISH))) {"CPI observation month does not match the request."}
        require(listOf("group","class","sub_class","item","code","imputation").all {row.isNull(it) || row.getString(it).isBlank()}){"CPI subcategory/imputed value cannot replace the published General index."}
        fun decimal(key:String):BigDecimal {val value=row.get(key);require(value is String){"CPI decimal fields changed type; refusing floating-point coercion."};return DecimalInput.parse(value)}
        val common="Source: Ministry of Statistics and Programme Implementation, All India Combined CPI, 2024 base. Published YoY is retained; no rebasing or mixing of older series."
        return listOf(PublicKind.CPI_INDEX to decimal("index"),PublicKind.CPI_YOY to decimal("inflation")).map {(kind,value)->PublicDatum(PublicProvider.MOSPI,kind,"CPI2024:ALL:COMBINED:${kind.name}",kind.label,value,null,if(kind==PublicKind.CPI_INDEX)"index (2024=100)" else "percent year-on-year",month.atEndOfMonth(),url,PublicValidation.HTTPS_VALIDATED,"2024=100; All India; Combined; General",common,"API does not specify provisional/final status; revisions remain possible")}.also {rows->rows.forEach {PublicRules.validate(it,at)}}
    }
}
