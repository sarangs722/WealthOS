package com.wealthos.finance

import org.junit.Test
import org.junit.Assert.*
import java.math.BigDecimal
import java.time.LocalDate

class FinancialMathTest {
    private fun b(s:String)=s.toBigDecimal()
    private fun close(expected:String,actual:BigDecimal,tolerance:String="0.000000000000000001")=assertTrue("$expected versus $actual",(b(expected)-actual).abs()<=b(tolerance))
    @Test fun decimalRootExpLogAndMonthlyConversion(){close("1.4142135623730950488",DecimalMath.sqrt(b("2")));close("2",DecimalMath.exp(DecimalMath.ln(b("2"))));close("0.1",(BigDecimal.ONE+DecimalMath.monthly(b("0.1"))).pow(12,DecimalMath.context)-BigDecimal.ONE)}
    @Test fun zeroAndNegativeGrowth(){close("1000",FinancialMath.growth(b("1000"),b("0"),120));close("810",FinancialMath.growth(b("1000"),b("-0.1"),24))}
    @Test fun inflationAndRealReturnAreMultiplicativelyConsistent(){close("0.047619047619047619",FinancialMath.realRate(b("0.1"),b("0.05")));close("121",FinancialMath.growth(b("100"),b("0.1"),24))}
    @Test fun sipZeroAndStepUpTiming(){val plain=FinancialMath.sip(b("100"),b("0"),24);close("2400",plain.value);val stepped=FinancialMath.sip(b("100"),b("0"),24,b("0.1"));close("2520",stepped.value);close("2520",stepped.contributions)}
    @Test fun sipBeginningGetsOneAdditionalMonthOfGrowth(){val end=FinancialMath.sip(b("100"),b("0.12"),12);val begin=FinancialMath.sip(b("100"),b("0.12"),12,beginning=true);close(end.value.multiply(BigDecimal.ONE+DecimalMath.monthly(b("0.12")),DecimalMath.context).toPlainString(),begin.value)}
    @Test fun goalAnnuityZeroAndAlreadyFunded(){close("100",FinancialMath.requiredMonthly(b("1200"),b("0"),b("0"),12));close("0",FinancialMath.requiredMonthly(b("100"),b("200"),b("0"),12))}
    @Test fun cagrDatesAndTotalLoss(){val start=LocalDate.of(2025,1,1);close("0.1",FinancialMath.cagr(b("100"),b("121"),start,start.plusDays(730)).rate!!);close("-1",FinancialMath.cagr(b("100"),b("0"),start,start.plusDays(365)).rate!!);assertNull(FinancialMath.cagr(b("0"),b("10"),start,start.plusDays(1)).rate)}
    @Test fun xirrMicrosoftReferenceFixture(){val rows=listOf("2008-01-01" to "-10000","2008-03-01" to "2750","2008-10-30" to "4250","2009-02-15" to "3250","2009-04-01" to "2750").map {(d,v)->DatedCashFlow(LocalDate.parse(d),b(v))};close("0.373362535",FinancialMath.xirr(rows).rate!!,"0.00000001")}
    @Test fun xirrZeroNegativeAndSameDateAggregation(){val d=LocalDate.of(2025,1,1);val flows=listOf(DatedCashFlow(d,b("-100")),DatedCashFlow(d,b("-100")),DatedCashFlow(d.plusDays(365),b("180")));close("-0.1",FinancialMath.xirr(flows).rate!!);close("0",FinancialMath.xirr(listOf(DatedCashFlow(d,b("-100")),DatedCashFlow(d.plusDays(365),b("100")))).rate!!)}
    @Test fun xirrMissingMultipleRootsAndNonconvergenceAreUnavailable(){val d=LocalDate.of(2025,1,1);assertNull(FinancialMath.xirr(listOf(DatedCashFlow(d,b("-100")))).rate);val ambiguous=listOf(DatedCashFlow(d,b("-100")),DatedCashFlow(d.plusDays(365),b("230")),DatedCashFlow(d.plusDays(730),b("-132")));assertTrue(FinancialMath.xirr(ambiguous).status.contains("Multiple sign"));assertNull(FinancialMath.xirr(ambiguous.take(2),0).rate)}
    @Test fun withdrawalDepletionAndZeroInterest(){val r=FinancialMath.withdrawal(b("1000"),b("300"),b("0"),b("0"),12);assertEquals(4,r.exhaustedMonth);close("1000",r.withdrawn);close("0",r.remaining)}
    @Test fun retirementFiniteHorizonAndInflation(){close("1200",FinancialMath.retirementCorpus(b("100"),b("0"),b("0"),12));val required=FinancialMath.retirementCorpus(b("100"),b("0.08"),b("0.04"),240);assertTrue(required>b("12000"));assertTrue(required<b("24000"))}
    @Test fun fdAndRdZeroAndSimpleConvention(){val d=LocalDate.of(2025,1,1);close("1200",FinancialMath.rd(b("100"),b("0"),d,12,DepositConvention.QUARTERLY));close("1100",Contracts.accrued(b("1000"),b("0.1"),d,d.plusDays(365),DepositConvention.SIMPLE_ACT_365))}
    @Test fun loanPrepaymentKeepsPrincipalAndInterestSeparate(){val r=FinancialMath.prepay(b("1200"),b("0"),12,1,b("600"));assertEquals(6,r.revised.size);close("1200",r.revised.fold(BigDecimal.ZERO){a,p->a+p.principal});close("0",r.interestSaved);val withRate=FinancialMath.prepay(b("100000"),b("0.12"),120,12,b("20000"));assertTrue(withRate.interestSaved.signum()>0);assertTrue(withRate.revised.size<120);close("0",withRate.revised.last().closing)}
    @Test fun largeValuesRetainPaise(){close("999999999999999999999999.99",FinancialMath.growth(b("999999999999999999999999.99"),b("0"),360))}
    @Test fun maximumGrowthEnvelopeStillRetainsPaise(){val principal=b("999999999999999999999999.99");val expected=principal*BigDecimal(11).pow(100);close(expected.toPlainString(),FinancialMath.growth(principal,b("10"),1200),"0.005")}
    @Test fun realNominalInverseAndFxReferenceMath(){val real=FinancialMath.realRate(b("0.1"),b("0.05"));close("0.1",FinancialMath.nominalRate(real,b("0.05")));close("9000",FinancialMath.convert(b("100"),b("90"),b("1")));close("80",FinancialMath.convert(b("100"),b("80"),b("100")))}
    @Test(expected=IllegalArgumentException::class) fun minusOneAnnualRateRejected(){DecimalMath.monthly(b("-1"))}
}
