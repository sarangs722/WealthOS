package com.wealthos.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wealthos.finance.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Composable internal fun PublicDataScreen(state:BankUiState,hidden:Boolean,model:BankViewModel,onBinding:(UUID)->Unit,onManual:()->Unit,chooseNavFile:(UUID)->Unit) {
    var applyObservation by rememberSaveable {mutableStateOf<String?>(null)}
    var applyInstrument by rememberSaveable {mutableStateOf<String?>(null)}
    val p=state.book.publicData;val today=LocalDate.now();val enabled=!state.demo && !state.saving
    val latest=p.latest().sortedWith(compareBy<PublicObservation>{it.datum.provider.ordinal}.thenBy {it.datum.series})
    val uri=LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize().testTag("public-data-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {
            Text("Public data & sources",style=MaterialTheme.typography.headlineMedium)
            Text("A reference, with a date",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleMedium)
            Text("Review public quotes and economic observations here. Refreshing only caches references. Apply a quote explicitly to use it in your recorded wealth.")
        }
        item {SectionCard("Optional public refresh") {
            Row {
                Column(Modifier.weight(1f)) {Text(if(p.networkEnabled)"Network refresh enabled" else "Network refresh off",style=MaterialTheme.typography.titleMedium);Text("Only generic public-data requests leave the app. No financial records, account names or holding identifiers are sent. Refresh is manual; there are no background requests.")}
                Switch(p.networkEnabled,{value->model.publicChange {it.setNetwork(value)}},enabled=enabled,modifier=Modifier.semanticsLabel("Allow public network refresh"))
            }
            Text("Cached references and manual tracking work offline. Reference prices may differ from executable market prices.",style=MaterialTheme.typography.bodyMedium)
        }}
        if(state.refreshing!=null)item {LinearProgressIndicator(Modifier.fillMaxWidth());Text("Refreshing ${state.refreshing.label}…")}
        state.publicMessage?.let {message->item {Text(message,color=MaterialTheme.colorScheme.primary)}}
        state.formError?.let {message->item {Text(message,color=MaterialTheme.colorScheme.error)}}
        items(listOf(PublicProvider.ECB,PublicProvider.MOSPI,PublicProvider.BIS)) {provider->
            val run=p.runs.filter {it.provider==provider}.maxByOrNull {it.attemptedAt}
            SectionCard(provider.label) {
                Text(when(provider){PublicProvider.ECB->"Working-day reference rates, around 16:00 CET. INR cross rates are derived from the published EUR table.";PublicProvider.MOSPI->"Monthly All India Combined General CPI, 2024 base. The previous two months are checked; publication and later revisions may lag.";else->"RBI repo rate from the BIS daily series, normally disseminated mid-week. This is not a live announcement feed."})
                Text("Attribution: ${when(provider){PublicProvider.ECB->"European Central Bank (derived cross rates)";PublicProvider.MOSPI->"Ministry of Statistics and Programme Implementation";else->"Reserve Bank of India; Bank for International Settlements"}}")
                if(run!=null)Text("${if(run.success)"Last refresh" else "Last attempt failed"} · ${recordedLabel(run.finishedAt)}\n${run.message}") else Text("No refresh recorded. No sample value is substituted.")
                OutlinedButton({model.refreshPublic(provider)},enabled=enabled && p.networkEnabled && state.refreshing==null) {Text("Refresh ${provider.name}")}
                TextButton({uri.openUri(provider.home)}){Text("Official source · opens browser")}
                TextButton({uri.openUri(provider.terms)}){Text("Source guidance & terms · opens browser")}
            }
        }
        item {Text("Mutual fund NAV files",style=MaterialTheme.typography.titleLarge)
            Text("Link an INR fund to its exact AMFI scheme code. Import your personal NAV file to keep only that selected quote. Compare plan, option and ISIN before applying. The publisher of a user-selected file cannot be authenticated.")
            Text("AMFI terms limit electronic storage and linking. WealthOS does not automatically download or cache its fund catalogue.",style=MaterialTheme.typography.bodyMedium)
            TextButton({uri.openUri(PublicProvider.AMFI_FILE.home)}){Text("AMFI homepage · opens browser")}
        }
        val funds=state.book.portfolio.instruments.filter {it.category==AssetCategory.MUTUAL_FUND && it.currency==CurrencyCode.INR && it.tracking==TrackingMode.TRANSACTIONS}
        if(funds.isEmpty())item {Text("Add an INR mutual-fund holding in Portfolio to link a NAV file.")}
        items(funds,key={"fund:${it.id}"}) {fund->
            val binding=p.currentBindings().find {it.instrumentId==fund.id}
            SectionCard(fund.name) {
                Text(if(binding==null)"No active NAV binding" else "AMFI code ${binding.schemeCode} · ISIN ${binding.isin.ifEmpty {"not supplied"}}")
                OutlinedButton({onBinding(fund.id)},enabled=enabled,modifier=Modifier.testTag("nav-binding:${fund.id}")){Text("Edit NAV binding")}
                if(binding!=null)OutlinedButton({chooseNavFile(binding.id)},enabled=enabled){Text("Import selected fund NAV")}
            }
        }
        item {Text("Cached references",style=MaterialTheme.typography.titleLarge)
            Text("Freshness policy: FX/NAV 4 days; CPI 45 days; policy rate 14 days; bank references 30 days. Older values are labeled last-known and remain available for review.",style=MaterialTheme.typography.bodyMedium)}
        if(latest.isEmpty())item {Text("No cached references yet. Manual prices and FX remain available in Portfolio.")}
        items(latest,key={"quote:${it.id}"}) {o->
            PublicObservationCard(o,hidden,today)
            if(o.datum.kind==PublicKind.FX) {
                val applied=p.applications.any {it.observationId==o.id && it.fxId!=null}
                OutlinedButton({applyObservation=o.id.toString();applyInstrument=null},enabled=enabled && !applied && !hidden){Text(if(applied)"FX reference applied" else "Review FX application")}
            }
            if(o.datum.kind==PublicKind.NAV) {
                p.currentBindings().filter {"NAV:${it.schemeCode}"==o.datum.series}.forEach {binding->
                    val fund=state.book.portfolio.instruments.find {it.id==binding.instrumentId}
                    val applied=p.applications.any {it.observationId==o.id && it.instrumentId==binding.instrumentId}
                    OutlinedButton({applyObservation=o.id.toString();applyInstrument=binding.instrumentId.toString()},enabled=enabled && !applied && !hidden){Text(if(applied)"NAV applied · ${fund?.name}" else "Review NAV · ${fund?.name}")}
                }
            }
        }
        if(p.observations.isNotEmpty())item {SectionCard("Source revision history") {
            Text("${p.observations.size} retained observations · ${p.applications.size} explicit applications. Earlier source revisions and financial valuations are retained.")
            p.observations.sortedWith(compareByDescending<PublicObservation>{it.datum.date}.thenByDescending {it.revision}).take(60).forEach {o->
                Text("${o.datum.label} · ${o.datum.date} · source revision ${o.revision}\n${if(hidden)"••••••" else o.datum.value.toPlainString()} ${o.datum.units} · retrieved ${recordedLabel(o.retrievedAt)}",style=MaterialTheme.typography.bodySmall)
            }
            if(p.observations.size>60)Text("Showing the latest 60; all observations remain in the vault.")
        }}
        item {SectionCard("Manual references & data health") {
            OutlinedButton(onManual,enabled=enabled){Text("Add economic reference")}
            Text("Economic and bank-rate references never change saved scenario assumptions or contracted deposit/loan rates.")
            Text("No verified free stock, ETF or commodity connector is enabled. Enter prices manually in Portfolio. Missing and stale references are never replaced with fabricated values.")
            Text("${latest.count {java.time.temporal.ChronoUnit.DAYS.between(it.datum.date,today)>it.datum.kind.recentDays}} stale cached series · ${funds.count {f->p.currentBindings().none {it.instrumentId==f.id}}} funds without an active NAV binding.")
        }}
    }
    val o=p.observations.find {it.id.toString()==applyObservation}
    if(o!=null)AlertDialog(onDismissRequest={if(!state.saving)applyObservation=null},title={Text("Apply reviewed reference?")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(if(hidden)"Reference amount hidden" else "${o.datum.value.toPlainString()} ${o.datum.units} · ${o.datum.date}")
            Text(o.datum.label);Text(o.datum.validation.label);Text(o.datum.publication);Text(PublicRules.freshness(o,today))
            Text("This adds a dated financial valuation. Existing same-day values are retained as earlier revisions. An older quote affects only eligible history; it does not replace a newer dated valuation. No cash movement or investment transaction is created.")
            state.formError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton({model.publicChange({applyObservation=null}){repo->if(applyInstrument==null)repo.applyFx(o.id,true) else repo.applyNav(o.id,UUID.fromString(applyInstrument),true)}},enabled=enabled && !hidden){Text("Confirm application")}},dismissButton={TextButton({applyObservation=null},enabled=!state.saving){Text("Cancel")}})
}

@Composable internal fun PublicObservationCard(o:PublicObservation,hidden:Boolean,today:LocalDate) {
    SectionCard(o.datum.label) {
        Text(if(hidden)"••••••" else "${o.datum.value.stripTrailingZeros().toPlainString()} ${o.datum.units}",style=MaterialTheme.typography.titleLarge)
        Text("${o.datum.provider.label} · ${dateLabel(o.datum.date)} · Revision ${o.revision}")
        Text(PublicRules.freshness(o,today));Text(o.datum.validation.label)
        Text("Retrieved ${recordedLabel(o.retrievedAt)}",style=MaterialTheme.typography.bodyMedium)
        Text(o.datum.publication,style=MaterialTheme.typography.bodyMedium)
        Text(if(hidden)"Reference inputs hidden" else "${o.datum.basis}\n${o.datum.details}\n${o.datum.sourceUrl}",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable internal fun NavBindingForm(instrument:Instrument,state:BankUiState,model:BankViewModel,done:()->Unit) {
    val old=state.book.publicData.bindings.filter {it.instrumentId==instrument.id}.maxByOrNull {it.revision}
    var code by rememberSaveable {mutableStateOf(old?.schemeCode ?: "")};var isin by rememberSaveable {mutableStateOf(old?.isin ?: "")};var active by rememberSaveable {mutableStateOf(old?.active ?: true)}
    Form("Link a fund NAV","${instrument.name}. Use the exact scheme code and plan/option from your statement. This does not change holdings or prices.",state,"Save NAV binding",{
        model.publicChange(done){it.saveBinding(NavBinding(UUID.randomUUID(),instrument.id,code.trim(),isin.trim().uppercase(java.util.Locale.ROOT),active,0,Instant.now()))}
    }) {Field("AMFI scheme code",code,{code=it});Field("ISIN (optional)",isin,{isin=it});Toggle("Active NAV binding",active,{active=it})}
}

@Composable internal fun EconomicReferenceForm(state:BankUiState,model:BankViewModel,done:()->Unit) {
    var kind by rememberSaveable {mutableStateOf(PublicKind.BANK_RATE.name)};var name by rememberSaveable {mutableStateOf("")};var value by rememberSaveable {mutableStateOf("")}
    var date by rememberSaveable {mutableStateOf(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString())};var source by rememberSaveable {mutableStateOf("")};var basis by rememberSaveable {mutableStateOf("")}
    Form("Economic reference","For your reference only. A new observation does not alter forecasts or contract rates. Enter CPI base/coverage and rate conventions explicitly.",state,"Save reference",{
        model.publicChange(done){repo->val at=Instant.now();val k=PublicKind.valueOf(kind)
            val d=PublicDatum(PublicProvider.MANUAL,k,"MANUAL:${UUID.randomUUID()}",name,DecimalInput.parse(value),null,if(k==PublicKind.CPI_INDEX)"index" else "percent",LocalDate.parse(date),source,PublicValidation.MANUAL,basis,"User-entered reference; verify against the cited publication.","Publication status entered by user")
            require(basis.isNotBlank()){"Enter the base/coverage or rate convention."};repo.cache(PublicProvider.MANUAL,listOf(d),at,at,"Manual reference saved without financial posting.")
        }
    }) {
        Choice("Reference type",kind,listOf(PublicKind.BANK_RATE,PublicKind.POLICY_RATE,PublicKind.CPI_INDEX,PublicKind.CPI_YOY).map {it.name to it.label},{kind=it})
        Field("Reference label",name,{name=it});Field("Reference value",value,{value=it},true);Field("Observation date (YYYY-MM-DD)",date,{date=it},hint="Indian calendar date (Asia/Kolkata); retrieval time is recorded separately.")
        Field("HTTPS source URL",source,{source=it},multi=true);Field("Base / coverage / rate convention",basis,{basis=it})
    }
}
