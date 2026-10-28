package com.wealthos.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wealthos.finance.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun number(value:BigDecimal,hidden:Boolean)=if(hidden)"••••••" else value.stripTrailingZeros().toPlainString()
@Composable fun InstrumentDetails(instrument:Instrument,state:BankUiState,hidden:Boolean,onAction:(String)->Unit) {
    val p=state.book.portfolio;val today=LocalDate.now();val value=WealthValuation.asset(instrument,p,today)
    val contract=p.contracts.find {it.instrumentId==instrument.id}
    val entries=p.entries.filter {it.instrumentId==instrument.id}.sortedWith(compareByDescending<InvestmentEntry> {it.date}.thenByDescending {it.sequence})
    val events=p.contractEvents.filter {it.instrumentId==instrument.id}.sortedWith(compareByDescending<ContractEvent> {it.date}.thenByDescending {it.sequence})
    val valuations=p.valuations.filter {it.instrumentId==instrument.id}.sortedWith(compareByDescending<AssetValuation> {it.date}.thenByDescending {it.revision})
    var trades by rememberSaveable(instrument.id){mutableStateOf(false)}
    var payments by rememberSaveable(instrument.id){mutableStateOf(false)}
    var prices by rememberSaveable(instrument.id){mutableStateOf(false)}
    var lots by rememberSaveable(instrument.id){mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text(instrument.name,style=MaterialTheme.typography.headlineMedium)
            Text("${instrument.category.label} · ${instrument.currency} · ${instrument.region.name.lowercase().replaceFirstChar {it.uppercase()}}",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item {SectionCard(if(instrument.category==AssetCategory.LOAN)"Outstanding liability" else "Current recorded value") {
            AmountDisplay(money(value.nativeValue,instrument.currency,hidden),style=MaterialTheme.typography.headlineLarge)
            Text(value.status)
            if(instrument.currency!=CurrencyCode.INR && WealthValuation.rate(p,instrument.currency,today)==null)StatusPill("Missing FX · INR total incomplete",true)
            value.date?.let {Text("Input date ${dateLabel(it)}",style=MaterialTheme.typography.bodyMedium)}
            DetailsDisclosure("Valuation inputs"){
            value.unitPrice?.let {Text("Valuation quote ${if(hidden)"••••••" else Amounts.formatQuote(it,instrument.currency)} per ${instrument.unit}")}
            Text("Known from ${dateLabel(instrument.openedOn)} · ${if(instrument.liquid)"Marked liquid" else "Excluded from liquid assets"}",style=MaterialTheme.typography.bodyMedium)
            if(instrument.currency!=CurrencyCode.INR) {
                val fx=WealthValuation.rate(p,instrument.currency,today)
                Text(if(fx==null)"No eligible FX rate; INR consolidation is incomplete." else "${fx.source?.datum?.provider?.label ?: "Manual FX"}: ${if(hidden)"••••••" else fx.inrPerUnit.toPlainString()} INR / ${instrument.currency} · ${dateLabel(fx.date)}")
                if(fx!=null) {Text(if(hidden)"FX source note hidden" else fx.note);Text("Retrieved / entered ${recordedLabel(fx.recordedAt)}",style=MaterialTheme.typography.bodyMedium)}
            }
            }
        }}
        if(!state.demo) item {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(instrument.tracking==TrackingMode.TRANSACTIONS) Button(onClick={onAction("investment-entry")},modifier=Modifier.weight(1f)) {Text("Add transaction")}
                    if(instrument.tracking!=TrackingMode.LOAN) OutlinedButton(onClick={onAction("asset-valuation")},modifier=Modifier.weight(1f)) {Text(if(instrument.tracking==TrackingMode.TRANSACTIONS)"Update price" else "Update value")}
                }
                if(contract!=null && events.none {it.kind==ContractEventKind.MATURITY})Button(onClick={onAction("contract-event")},modifier=Modifier.fillMaxWidth()) {Text(if(instrument.tracking==TrackingMode.LOAN)"Record repayment" else "Record deposit event")}
                TextButton(onClick={onAction("edit-asset")}) {Text("Edit asset details")}
            }
        }
        if(instrument.tracking==TrackingMode.OBSERVED && HoldingSnapshots.availableForSnapshot(instrument.category)) item {
            SectionCard("Holding summary") {
                val summary=value.snapshot
                Text("Reported invested amount: ${money(summary?.reportedInvested,instrument.currency,hidden)}")
                summary?.reportedInvested?.let {Text("Difference vs reported investment: ${money(value.nativeValue?.minus(it),instrument.currency,hidden)}")}
                summary?.reportedUnits?.let {Text("Reported units: ${number(it,hidden)}")}
                summary?.reportedPrice?.let {Text("Reported NAV / price: ${if(hidden)"••••••" else Amounts.formatQuote(it,instrument.currency)}")}
                DetailsDisclosure("About this summary") {Text("These are the figures you copied on the input date, not a purchase history. The difference excludes any unrecorded withdrawals or distributions. FIFO gains and annualised returns are unavailable.")}
                if(!state.demo) TextButton(onClick={onAction("unit-tracking")}) {Text("Track purchases & sales")}
            }
        }
        value.holding?.let {holding ->
            item {SectionCard("Holdings & book cost") {
                Text("${number(holding.units,hidden)} ${instrument.unit}",style=MaterialTheme.typography.titleLarge)
                Text("Remaining FIFO cost: ${money(holding.remainingCost,instrument.currency,hidden)}")
                Text("Unrealized gain: ${money(value.unrealizedGain,instrument.currency,hidden)}")
                Text("Realized gain: ${money(holding.realizedGain,instrument.currency,hidden)}")
                if(!holding.historyComplete)StatusPill("History incomplete · returns unavailable",true)
                DetailsDisclosure("Cost & return details"){
                    Text("Distributions recorded: ${money(holding.distributions,instrument.currency,hidden)}")
                    Text(if(holding.historyComplete)"Based on recorded entries; earlier history is only complete if all events were entered." else "Pre-opening cash-flow history or manual adjustments are incomplete. No XIRR/CAGR is inferred.")
                    Text("FIFO book cost only. Tax rules, exemptions and country-specific cost adjustments are not calculated.")
                }
            }}
            if(holding.lots.isNotEmpty())item {DisclosureHeader("Remaining lots",lots,{lots=!lots},"${holding.lots.size} lots")}
            if(lots)items(holding.lots){lot->Text("${dateLabel(lot.acquiredOn)} · ${number(lot.units,hidden)} ${instrument.unit} · ${money(lot.remainingCost,instrument.currency,hidden)}")}
        }
        if(contract!=null) {
            item {DetailsDisclosure("Contract inputs") {
                Text("Opening principal ${money(contract.openingPrincipal,instrument.currency,hidden)}")
                Text("Contract rate ${if(hidden)"••••••" else contract.annualRate.movePointRight(2).stripTrailingZeros().toPlainString()}% nominal per year")
                Text("${dateLabel(contract.start)} – ${dateLabel(contract.end)}")
                if(instrument.tracking==TrackingMode.DEPOSIT) {
                    Text(contract.convention.label)
                    Text("Gross contract estimate: ${money(value.contractEstimate,instrument.currency,hidden)}")
                    Text("Estimate only; bank conventions, tax, penalties and already-earned pre-opening interest may differ. Actual observed values are shown separately. No automatic installments or maturity cash are recorded.")
                    if(instrument.category==AssetCategory.RD)Text("Agreed installment ${money(contract.scheduledInstallment,instrument.currency,hidden)}; only recorded payments count.")
                } else Text("Principal balance uses actual repayment principal. Future schedule is an estimate; interest payments do not reduce principal.")
            }}
            if(instrument.tracking==TrackingMode.LOAN && !hidden)item {DetailsDisclosure("Original remaining-term schedule · estimate") {
                Text("Monthly EMI ${money(Contracts.emi(contract.openingPrincipal,contract.annualRate,contract.termMonths,instrument.currency.scale),instrument.currency)}")
                Text("Monthly reducing balance, nominal rate / 12, HALF_EVEN at currency scale; final payment clears rounding residue. Contract rate is held fixed. Your bank's daily interest/reset conventions may differ.",style=MaterialTheme.typography.bodyMedium)
                Contracts.schedule(contract.openingPrincipal,contract.annualRate,contract.termMonths,instrument.currency.scale).take(6).forEach {payment ->
                    Text("#${payment.number} · ${dateLabel(contract.start.plusMonths(payment.number.toLong()))}\nPrincipal ${money(payment.principal,instrument.currency)} · Interest ${money(payment.interest,instrument.currency)} · Remaining ${money(payment.closing,instrument.currency)}")
                }
                Text("First 6 projected payments shown. Actual repayments do not rewrite this original contract schedule.",style=MaterialTheme.typography.bodyMedium)
            }}
        }
        if(entries.isNotEmpty())item {DisclosureHeader("Investment history",trades,{trades=!trades},"${entries.size} entries")}
        if(trades)items(entries,key={it.id}) {entry -> SectionCard("${entry.kind.label} · ${dateLabel(entry.date)} · #${entry.sequence}") {
            Text("Units ${number(entry.units,hidden)}${if(entry.kind==TradeKind.SPLIT || entry.kind==TradeKind.BONUS)" per ${number(entry.ratioDenominator,hidden)} existing" else ""}")
            Text("Gross / basis ${money(entry.gross,instrument.currency,hidden)} · Fees ${money(entry.fees,instrument.currency,hidden)}")
            if(entry.note.isNotBlank())Text(if(hidden)"Note hidden" else entry.note)
            Text("Recorded ${recordedLabel(entry.recordedAt)}",style=MaterialTheme.typography.bodyMedium)
        }}
        if(events.isNotEmpty())item {DisclosureHeader("Actual contract events",payments,{payments=!payments},"${events.size} entries")}
        if(payments)items(events,key={it.id}) {event -> SectionCard("${event.kind.label} · ${dateLabel(event.date)} · #${event.sequence}") {
            Text("Principal ${money(event.principal,instrument.currency,hidden)} · Interest ${money(event.interest,instrument.currency,hidden)} · Fees ${money(event.fees,instrument.currency,hidden)}")
            if(event.note.isNotBlank())Text(if(hidden)"Note hidden" else event.note)
            Text("Recorded ${recordedLabel(event.recordedAt)}",style=MaterialTheme.typography.bodyMedium)
        }}
        if(valuations.isNotEmpty())item {DisclosureHeader("Valuation history",prices,{prices=!prices},"${valuations.size} observations")}
        if(prices)items(valuations,key={it.id}) {observation -> SectionCard("${dateLabel(observation.date)} · Revision ${observation.revision}") {
            Text((if(observation.kind==ValuationKind.UNIT_PRICE && !hidden)Amounts.formatQuote(observation.value,instrument.currency) else money(observation.value,instrument.currency,hidden))+(if(observation.kind==ValuationKind.UNIT_PRICE)" per ${instrument.unit}" else " observed total"))
            observation.reportedInvested?.let {Text("Reported invested amount: ${money(it,instrument.currency,hidden)}")}
            observation.reportedUnits?.let {Text("Reported units: ${number(it,hidden)}")}
            observation.reportedPrice?.let {Text("Reported NAV / price: ${if(hidden)"••••••" else Amounts.formatQuote(it,instrument.currency)}")}
            if(observation.calculatedTotal)Text("Total calculated from reported units × price")
            if(observation.note.isNotBlank())Text(if(hidden)"Note hidden" else observation.note)
            observation.source?.let {Text(PublicRules.describe(it,today));Text("Source date ${it.datum.date} · Retrieved ${recordedLabel(it.retrievedAt)}")}
            Text("Recorded ${recordedLabel(observation.recordedAt)}",style=MaterialTheme.typography.bodyMedium)
        }}
    }
}
