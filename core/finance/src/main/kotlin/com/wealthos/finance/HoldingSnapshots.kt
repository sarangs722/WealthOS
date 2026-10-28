package com.wealthos.finance

import java.math.BigDecimal
import java.math.RoundingMode

/** A broker-screen observation is not a reconstructed transaction ledger or tax cost basis. */
object HoldingSnapshots {
    fun total(units:BigDecimal,price:BigDecimal,currency:CurrencyCode):BigDecimal {
        DecimalInput.parse(units.toPlainString());DecimalInput.parse(price.toPlainString())
        require(units>=ZERO && price>=ZERO){"Units and price cannot be negative."}
        return (units*price).setScale(currency.scale,RoundingMode.HALF_EVEN).also {Amounts.parse(it.toPlainString(),currency)}
    }
    fun validate(value:AssetValuation,currency:CurrencyCode){
        val metadata=value.reportedInvested!=null || value.reportedUnits!=null || value.reportedPrice!=null || value.calculatedTotal
        require(!metadata || value.kind==ValuationKind.OBSERVED_TOTAL){"Holding summaries belong to total-value observations."}
        value.reportedInvested?.let{require(it>=ZERO);Amounts.parse(it.toPlainString(),currency)}
        listOfNotNull(value.reportedUnits,value.reportedPrice).forEach{require(it>=ZERO);DecimalInput.parse(it.toPlainString())}
        if(value.calculatedTotal){
            val units=requireNotNull(value.reportedUnits){"Enter your units."};val price=requireNotNull(value.reportedPrice){"Enter the NAV or unit price."}
            require(total(units,price,currency).compareTo(value.value)==0){"Current value does not match the entered units and price."}
        }
    }
    fun availableForSnapshot(category:AssetCategory)=category !in listOf(AssetCategory.FD,AssetCategory.RD,AssetCategory.LOAN,AssetCategory.PROPERTY,AssetCategory.EPF,AssetCategory.PPF,AssetCategory.NPS)
}
