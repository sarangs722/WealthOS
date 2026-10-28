package com.wealthos.finance

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.fail
import org.junit.Test

class PublicCalendarTest {
    private fun manual(day:String)=PublicDatum(PublicProvider.MANUAL,PublicKind.BANK_RATE,"MANUAL:fixture","Fictional reference",BigDecimal("6.125"),null,"percent",LocalDate.parse(day),"https://example.com/fictional-only",PublicValidation.MANUAL,"Nominal annual rate","Fictional test reference","Test")
    @Test fun indianReferenceUsesItsCalendarWhenUtcIsStillYesterday(){
        PublicRules.validate(manual("2026-10-28"),Instant.parse("2026-10-27T18:30:00Z"))
        try{PublicRules.validate(manual("2026-10-28"),Instant.parse("2026-10-27T18:29:59Z"));fail("Tomorrow's Indian observation was accepted")}catch(_:IllegalArgumentException){}
    }
    @Test fun europeUsesItsOwnCalendarRatherThanIndias(){
        val fx=PublicDatum(PublicProvider.ECB,PublicKind.FX,"FX:USD","USD reference",BigDecimal("90.125"),CurrencyCode.USD,"INR per native unit",LocalDate.parse("2026-07-02"),"https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml",PublicValidation.HTTPS_VALIDATED,"EUR cross reference","Fictional fixture","Test")
        PublicRules.validate(fx,Instant.parse("2026-07-01T22:00:00Z"))
        try{PublicRules.validate(fx,Instant.parse("2026-07-01T21:59:59Z"));fail("Tomorrow's European observation was accepted")}catch(_:IllegalArgumentException){}
    }
}
