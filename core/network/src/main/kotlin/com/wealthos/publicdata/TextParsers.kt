package com.wealthos.publicdata

import com.wealthos.finance.*
import java.io.Reader
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Strict bounded CSV with quoted commas/newlines and doubled quotes; no numeric coercion. */
object CsvRows {
    fun parse(text:String):List<List<String>> {
        require(text.length<=512*1024 && '\u0000' !in text){"Invalid CSV size/encoding."}
        val rows=mutableListOf<List<String>>();val row=mutableListOf<String>();val field=StringBuilder();var quoted=false;var closed=false;var i=0
        fun finishField(){row+=field.toString();field.setLength(0);closed=false;require(row.size<=60)}
        fun finishRow(){finishField();if(row.any {it.isNotBlank()})rows+=row.toList();row.clear();require(rows.size<=2000)}
        while(i<text.length){val ch=text[i]
            when {
                quoted && ch=='"'->{if(i+1<text.length && text[i+1]=='"'){field.append('"');i++}else{quoted=false;closed=true}}
                quoted->field.append(ch)
                ch=='"'->{require(field.isEmpty() && !closed){"Malformed CSV quoting."};quoted=true}
                ch==','->finishField()
                ch=='\n'->finishRow()
                ch=='\r'->{if(i+1<text.length && text[i+1]=='\n')i++;finishRow()}
                else->{require(!closed){"Unexpected characters after quoted CSV field."};field.append(ch)}
            };require(field.length<=8000);i++
        }
        require(!quoted){"Truncated CSV field."};if(field.isNotEmpty() || row.isNotEmpty() || closed)finishRow()
        return rows
    }
}
object BisParser {
    fun parse(text:String,url:String,at:Instant):List<PublicDatum> {
        val rows=CsvRows.parse(text.removePrefix("\uFEFF"));require(rows.size>=2){"No policy observations returned."}
        val header=rows.first();require(header.distinct().size==header.size)
        val required=listOf("FREQ","REF_AREA","UNIT_MEASURE","UNIT_MULT","SOURCE_REF","TIME_PERIOD","OBS_VALUE","OBS_STATUS","OBS_CONF")
        require(required.all {it in header}){"Policy source schema changed."}
        val seenDates=mutableSetOf<LocalDate>()
        val result=rows.drop(1).mapNotNull {row->require(row.size==header.size){"Partial policy row."}
            fun value(key:String)=row[header.indexOf(key)].trim()
            require(value("FREQ")=="D" && value("REF_AREA")=="IN" && value("UNIT_MEASURE")=="368" && value("UNIT_MULT")=="0" && value("SOURCE_REF")=="Reserve Bank of India") {"Wrong policy series, units or national source."}
            require(value("OBS_CONF")=="F"){"Policy observation is not public."}
            val date=LocalDate.parse(value("TIME_PERIOD"));require(date>=LocalDate.of(2001,4,3) && date<=at.atZone(ZoneOffset.UTC).toLocalDate() && seenDates.add(date)){"Invalid or conflicting policy date."}
            // BIS explicitly emits M/NaN for missing calendar days. They are gaps, never zero rates.
            if(value("OBS_STATUS")=="M"){
                require(value("OBS_VALUE") in listOf("","NaN")){"Missing policy flag conflicts with a numerical value."}
                return@mapNotNull null
            }
            require(value("OBS_STATUS")=="A"){"Policy observation is not an ordinary observation."}
            PublicDatum(PublicProvider.BIS,PublicKind.POLICY_RATE,"BIS:D.IN","India policy repo rate (RBI via BIS)",DecimalInput.parse(value("OBS_VALUE")),null,"percent per year",date,url,PublicValidation.HTTPS_VALIDATED,"Official repo overnight rate since 2001","Source: Reserve Bank of India; published by BIS. Daily series disseminated around mid-week; not a live policy announcement feed.","BIS observation status A; public F")
        }
        require(result.map {it.date}.distinct().size==result.size){"Conflicting policy dates."};result.forEach {PublicRules.validate(it,at)}
        return result
    }
}
object AmfiNavParser {
    private val dateFormat=DateTimeFormatter.ofPattern("dd-MMM-uuuu",Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    fun parse(reader:Reader,schemeCode:String,expectedIsin:String,at:Instant):PublicDatum {
        require(Regex("[0-9]{5,9}").matches(schemeCode));require(expectedIsin.isEmpty() || Regex("[A-Z]{2}[A-Z0-9]{9}[0-9]").matches(expectedIsin))
        var total=0;var lines=0;var columns=0;var match:PublicDatum?=null
        fun nextLine():String? {val line=StringBuilder();while(true){val c=reader.read();if(c<0)return if(line.isEmpty())null else line.toString();total++;require(total<=16*1024*1024){"NAV file exceeds the personal import limit."};if(c==10)return line.toString().removeSuffix("\r");line.append(c.toChar());require(line.length<=4096){"NAV line exceeds its format limit."}}}
        while(true){val line=nextLine()?.trim()?.removePrefix("\uFEFF") ?: break;lines++;require(lines<=120000);if(line.isBlank())continue
            if(line.startsWith("Scheme Code;")){val names=line.split(';').map {it.trim()};require(names.size in listOf(6,8) && names[3]=="Scheme Name" && names[names.size-2]=="Net Asset Value" && names.last()=="Date" && (names.size==6 || (names[4]=="Plan" && names[5]=="Option"))){"Unsupported AMFI NAV header."};columns=names.size;continue}
            if(!line.startsWith("$schemeCode;"))continue
            require(columns>0 && match==null){"Missing NAV header or duplicate scheme code."}
            val p=line.split(';').map {it.trim()};require(p.size==columns && p[0]==schemeCode && p[3].isNotBlank()){"Malformed selected NAV row."}
            val isins=listOf(p[1],p[2]).filter {it.isNotBlank() && it!="-" && it!="N.A."}
            require(expectedIsin.isEmpty() || expectedIsin in isins){"The selected scheme's ISIN does not match the linked holding."}
            val value=DecimalInput.positive(p[p.size-2]);val date=LocalDate.parse(p.last(),dateFormat)
            val name=if(columns==8)"${p[3]} · ${p[4]} · ${p[5]}" else p[3]
            match=PublicDatum(PublicProvider.AMFI_FILE,PublicKind.NAV,"NAV:$schemeCode",name,value,CurrencyCode.INR,"INR per unit",date,PublicProvider.AMFI_FILE.home,PublicValidation.USER_FILE,"AMFI scheme code $schemeCode","Personal NAV import; only this selected scheme is retained. Compare the exact plan/option and ISIN before applying.","Closing NAV for the stated financial date; source file supplied by user",isins.joinToString("|"))
        }
        require(columns>0){"AMFI semicolon NAV header not found."}
        val result=requireNotNull(match){"Selected scheme code not present in this file; cached NAV was retained."};PublicRules.validate(result,at);return result
    }
}
