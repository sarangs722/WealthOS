package com.wealthos.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wealthos.finance.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Values from a broker screen are observations, not invented acquisitions. */
@Composable internal fun QuickInvestmentForm(state:BankUiState,model:BankViewModel,category:AssetCategory,detailed:()->Unit,done:(UUID)->Unit) {
    val id by rememberSaveable {mutableStateOf(UUID.randomUUID().toString())}
    var name by rememberSaveable {mutableStateOf("")}
    var currencyName by rememberSaveable {mutableStateOf(CurrencyCode.INR.name)}
    var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var value by rememberSaveable {mutableStateOf("")};var invested by rememberSaveable {mutableStateOf("")}
    var units by rememberSaveable {mutableStateOf("")};var price by rememberSaveable {mutableStateOf("")}
    var byUnits by rememberSaveable {mutableStateOf(false)}
    var liquid by rememberSaveable {mutableStateOf(category in listOf(AssetCategory.MUTUAL_FUND,AssetCategory.STOCK,AssetCategory.ETF))}
    var region by rememberSaveable {mutableStateOf(MarketRegion.INDIA.name)}
    val currency=CurrencyCode.valueOf(currencyName)
    val singular=when(category){AssetCategory.MUTUAL_FUND->"mutual fund";AssetCategory.STOCK->"stock";AssetCategory.ETF->"ETF";AssetCategory.GOLD->"gold holding";else->"commodity"}
    Form("Add $singular","Copy the figures shown in your investment app.",state,"Save holding",{
        model.portfolio({done(UUID.fromString(id))}) {repo->
            val instrument=Instrument(UUID.fromString(id),name.trim(),category,currency,TrackingMode.OBSERVED,LocalDate.parse(date),Instant.now(),liquid,MarketRegion.valueOf(region))
            val snapshot=holdingSnapshot(instrument,date,value,invested,units,price,byUnits,"")
            repo.addInstrument(NewInstrument(instrument,initialValue=snapshot.value,snapshot=snapshot))
        }
    }) {
        Field(if(category==AssetCategory.MUTUAL_FUND)"Fund name" else "Holding name",name,{name=it})
        ValueMode(byUnits,!state.saving && !state.demo){byUnits=it}
        if(byUnits) {
            Field("Units held",units,{units=it},true)
            Field(if(category==AssetCategory.MUTUAL_FUND)"NAV in $currency" else "Unit price in $currency",price,{price=it},true)
        } else Field("Current value in $currency",value,{value=it},true)
        Field("Invested amount (optional)",invested,{invested=it},true,"Copy the invested amount shown by your provider, if available. It does not establish FIFO cost or purchase dates.")
        DateChoice("As of",date,{date=it})
        FormDetails("More details (optional)") {
            Choice("Currency",currencyName,CurrencyCode.entries.map {it.name to it.name}) {currencyName=it}
            if(!byUnits) {
                Field("Units held (optional)",units,{units=it},true)
                Field("NAV / unit price (optional)",price,{price=it},true,"Use the price for the same financial date as this snapshot. A reported total remains the value; units are not inferred.")
            }
            Choice("Market",region,MarketRegion.entries.map {it.name to if(it==MarketRegion.INDIA)"India" else "International"}) {region=it}
            Toggle("Include in liquid assets",liquid){liquid=it}
            TextButton(onClick=detailed,enabled=!state.saving && !state.demo){Text("Track purchases & sales instead")}
            Text("Detailed tracking needs units and actual transaction amounts. You can start it later from this holding.",style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun ValueMode(byUnits:Boolean,enabled:Boolean,change:(Boolean)->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(!byUnits,{change(false)},enabled=enabled,label={Text("Current value")})
        FilterChip(byUnits,{change(true)},enabled=enabled,label={Text("Units & price")})
    }
}
internal fun holdingSnapshot(i:Instrument,date:String,value:String,invested:String,units:String,price:String,byUnits:Boolean,note:String):AssetValuation {
    val u=units.takeIf {it.isNotBlank()}?.let {DecimalInput.parse(it)}
    val p=price.takeIf {it.isNotBlank()}?.let {DecimalInput.parse(it)}
    val total=if(byUnits)HoldingSnapshots.total(requireNotNull(u){"Enter your units."},requireNotNull(p){"Enter the NAV or unit price."},i.currency) else Amounts.parse(value,i.currency)
    return AssetValuation(UUID.randomUUID(),i.id,ValuationKind.OBSERVED_TOTAL,total,LocalDate.parse(date),0,Instant.now(),note,
        reportedInvested=invested.takeIf {it.isNotBlank()}?.let {Amounts.parse(it,i.currency)},reportedUnits=u,reportedPrice=p,calculatedTotal=byUnits)
}

@Composable internal fun SnapshotUpdateForm(instrument:Instrument,state:BankUiState,model:BankViewModel,done:()->Unit) {
    var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var value by rememberSaveable {mutableStateOf("")};var invested by rememberSaveable {mutableStateOf("")}
    var units by rememberSaveable {mutableStateOf("")};var price by rememberSaveable {mutableStateOf("")}
    var byUnits by rememberSaveable {mutableStateOf(false)};var note by rememberSaveable {mutableStateOf("")}
    val correction=state.book.portfolio.valuations.any {it.instrumentId==instrument.id && it.date.toString()==date}
    Form("Update holding",instrument.name,state,"Save value",{
        model.portfolio(done){it.addValuation(holdingSnapshot(instrument,date,value,invested,units,price,byUnits,note))}
    }) {
        ValueMode(byUnits,!state.saving && !state.demo){byUnits=it}
        if(byUnits){Field("Units held",units,{units=it},true);Field("NAV / unit price in ${instrument.currency}",price,{price=it},true)}
        else Field("Current value in ${instrument.currency}",value,{value=it},true)
        DateChoice("As of",date,{date=it})
        if(correction)Field("Correction reason",note,{note=it},hint="A value already exists on this date. Explain the correction; both versions are retained.")
        FormDetails("More details (optional)") {
            Field("Invested amount (optional)",invested,{invested=it},true)
            if(!byUnits){Field("Units held (optional)",units,{units=it},true);Field("NAV / unit price (optional)",price,{price=it},true)}
            if(!correction)Field("Note (optional)",note,{note=it})
            Text("Only enter figures for this date. Blank optional values stay unknown; earlier snapshots are kept.",style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable internal fun UnitTrackingForm(instrument:Instrument,state:BankUiState,model:BankViewModel,done:()->Unit) {
    var units by rememberSaveable {mutableStateOf("")};var cost by rememberSaveable {mutableStateOf("")};var price by rememberSaveable {mutableStateOf("")}
    var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    Form("Start tracking transactions",instrument.name,state,"Start unit tracking",{
        model.portfolio(done){it.startUnitTracking(instrument.id,LocalDate.parse(date),DecimalInput.positive(units),cost.takeIf {it.isNotBlank()}?.let {Amounts.parse(it,instrument.currency)},price.takeIf {it.isNotBlank()}?.let {DecimalInput.parse(it)})}
    }) {
        Text("Your earlier value history stays. Add only purchases and sales after this starting position.",style=MaterialTheme.typography.bodyMedium)
        Field("Units held",units,{units=it},true)
        Field("NAV / unit price (optional)",price,{price=it},true,"A price is needed to value your units after the switch. The previous total is not reused as a unit price.")
        DateChoice("Start on",date,{date=it})
        FormDetails("Known cost (optional)") {
            Field("Cost of these units (optional)",cost,{cost=it},true,"Actual remaining acquisition cost, including fees. A provider's invested amount may differ after withdrawals. Leave blank when unknown.")
        }
        Text("This changes the tracking method for this holding. It adds no asset or cash movement. Gains need known cost; annualised returns need complete dated cash flows.",style=MaterialTheme.typography.bodySmall)
    }
}
