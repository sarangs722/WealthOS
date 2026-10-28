package com.wealthos.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wealthos.finance.*
import java.time.LocalDate

@Composable fun AccountEditor(mode: String, account: BankAccount?, state: BankUiState, model: BankViewModel, done: () -> Unit) {
    var name by rememberSaveable(mode,account?.id) { mutableStateOf(account?.name ?: "") }
    var institution by rememberSaveable(mode,account?.id) { mutableStateOf(account?.institution ?: "") }
    var kindName by rememberSaveable(mode,account?.id) { mutableStateOf((account?.kind ?: AccountKind.SAVINGS).name) }
    var currencyName by rememberSaveable(mode,account?.id) { mutableStateOf((account?.currency ?: CurrencyCode.INR).name) }
    var amount by rememberSaveable(mode,account?.id) { mutableStateOf("") }
    var date by rememberSaveable(mode,account?.id) { mutableStateOf(LocalDate.now().toString()) }
    var note by rememberSaveable(mode,account?.id) { mutableStateOf("") }
    val currency=CurrencyCode.valueOf(currencyName)
    val kind=AccountKind.valueOf(kindName)
    val editing=mode=="edit"
    val balance=mode=="balance"
    val enabled=!state.saving && !state.demo
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("account-form"),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {
            Text(when { editing -> "Edit account"; balance -> "Update balance"; else -> "Add bank account" },style=MaterialTheme.typography.headlineMedium)
            Text(if(balance) "${account?.name} · Record a dated observation" else if(editing) "Account identity stays the same. Balance history is preserved." else "Quick Add · Start from a known balance",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(!balance) {
            item { OutlinedTextField(name,{if(it.length<=80)name=it},label={Text("Account name")},placeholder={Text("e.g. Everyday savings")},singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth()) }
            item { OutlinedTextField(institution,{if(it.length<=80)institution=it},label={Text("Bank name (optional)")},singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth()) }
            item { Text("Account type",style=MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    AccountKind.entries.forEach { type -> FilterChip(kind==type,{kindName=type.name},enabled=enabled,label={Text(type.label)}) }
                }
                if(kind==AccountKind.BROKER_CASH)Text("Cash only. Add holdings separately.")
            }
            if(!editing) item {
                Text("Native currency",style=MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    CurrencyCode.entries.forEach { code -> FilterChip(currency==code,{currencyName=code.name},enabled=enabled,label={Text(code.name)}) }
                }
                if(currency!=CurrencyCode.INR) Text("An exchange rate is needed for your INR total.",style=MaterialTheme.typography.bodyMedium)
            }
            if(editing) item { Text("Currency: $currency · Immutable to preserve balance history.") }
        }
        if(!editing) {
            item { OutlinedTextField(amount,{amount=it.take(40)},label={Text("Balance in $currency")},supportingText={Text("No commas. Up to ${currency.scale} decimal places. Negative = overdraft.")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth()) }
            item { OutlinedTextField(date,{date=it.take(10)},label={Text("Balance date (YYYY-MM-DD)")},singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth()) }
            if(balance) item { OutlinedTextField(note,{if(it.length<=500)note=it},label={Text("Note / correction explanation")},supportingText={Text("Required when correcting an existing balance on the same date.")},enabled=enabled,modifier=Modifier.fillMaxWidth(),minLines=2) }
        }
        item {DetailsDisclosure("About balance updates"){Text(if(editing)"Changing the nickname or bank does not change balances." else "Enter the full balance shown by your bank on this date, including any recorded transfers or purchases. This replaces the carried-forward balance for that date; previous observations and corrections remain in history. A difference is not automatically income or expense.")}}
        if(state.formError!=null) item { Text(state.formError,color=MaterialTheme.colorScheme.error,modifier=Modifier.semanticsLabel("Validation error: ${state.formError}")) }
        item { Button(onClick={ when {
            editing && account!=null -> model.edit(account.id,name,institution,kind,done)
            balance && account!=null -> model.balance(account.id,currency,amount,date,note,done)
            else -> model.add(name,institution,currency,kind,amount,date,done)
        } },enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
            if(state.saving) { CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp); Spacer(Modifier.width(12.dp)) }
            Text(if(state.saving) "Saving…" else if(editing) "Save account" else "Save balance")
        } }
    }
}

@Composable fun AccountDetails(account: BankAccount, book: BankBook, hidden: Boolean, demo: Boolean, onEdit: () -> Unit, onBalance: () -> Unit,onReference:()->Unit={}) {
    val observations=book.observations.filter {it.accountId==account.id}
        .sortedWith(compareByDescending<BalanceObservation> {it.effectiveDate}.thenByDescending {it.revision})
    val current=BankValuation.at(book,LocalDate.now()).inputs.first {it.account.id==account.id}
    val latestPerDate=observations.groupBy {it.effectiveDate}.mapValues {(_,rows)->rows.maxOf {it.revision}}
    var cashHistory by rememberSaveable(account.id){mutableStateOf(false)}
    var balanceHistory by rememberSaveable(account.id){mutableStateOf(false)}
    var references by rememberSaveable(account.id){mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize().testTag("account-detail"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text(account.name,style=MaterialTheme.typography.headlineMedium); Text("${account.institution.ifBlank {"Cash account"}} · ${account.kind.label} · ${account.currency}",color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SectionCard("Current balance") {
            AmountDisplay(money(current.value,account.currency,hidden),style=MaterialTheme.typography.headlineLarge)
            Text(current.observation?.let {"As of ${dateLabel(it.effectiveDate)}"} ?: "No dated observation",color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(current.provisional)StatusPill("Provisional cash",true)
            else if(current.observation?.effectiveDate?.isBefore(LocalDate.now().minusDays(30))==true)StatusPill("Balance needs updating",true)
            if(current.provisional)Text("Observed ${money(current.observation?.amount,account.currency,hidden)} · Adjustment ${money(current.provisionalDelta,account.currency,hidden)}")
        } }
        if(!demo) item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Button(onClick=onBalance,modifier=Modifier.weight(1f)) { Text("Update balance") }
            OutlinedButton(onClick=onEdit,modifier=Modifier.weight(1f)) { Text("Edit account") }
        } }
        if(account.kind==AccountKind.BROKER_CASH) {
            if(!demo)item {OutlinedButton(onClick=onReference) {Text("Add statement reference")}}
            val refs=book.portfolio.references.filter {it.accountId==account.id}.sortedWith(compareByDescending<StatementReference>{it.date}.thenByDescending {it.revision})
            if(refs.isNotEmpty())item{DisclosureHeader("Statement references",references,{references=!references},"${refs.size} records")}
            if(references)items(refs,key={it.id}) {ref->SectionCard("Statement reference · ${dateLabel(ref.date)} · Revision ${ref.revision}") {
                Text("Reported total ${money(ref.total,account.currency,hidden)}")
                Text("Calculated minus reported ${money(WealthValuation.reconcile(book,ref),account.currency,hidden)}")
                Text("Reference only. Never counted as an additional asset.")
            }}
        }
        val movements=book.portfolio.cashMovements.filter {it.accountId==account.id}.sortedByDescending {it.date}
        if(movements.isNotEmpty())item {DisclosureHeader("Linked cash movements",cashHistory,{cashHistory=!cashHistory},"${movements.size} records")}
        if(cashHistory)items(movements,key={it.id}) {movement->SectionCard(dateLabel(movement.date)) {
            Text(money(movement.amount,account.currency,hidden))
            Text(if(hidden)"Note hidden" else movement.note)
            Text(if(movement.date>(current.observation?.effectiveDate ?: LocalDate.MAX) || (movement.anchorObservationId==current.observation?.id && movement.applyToAnchor))"Applied provisionally to latest eligible balance" else "Absorbed / already reflected in latest eligible balance")
        }}
        item {DisclosureHeader("Balance history",balanceHistory,{balanceHistory=!balanceHistory},"${observations.size} observations")}
        if(balanceHistory)items(observations,key={it.id}) { observation ->
            SectionCard("${dateLabel(observation.effectiveDate)} · Revision ${observation.revision}") {
                Text(money(observation.amount,observation.currency,hidden),style=MaterialTheme.typography.titleLarge)
                if(observation.revision!=latestPerDate[observation.effectiveDate]) Text("Superseded · Retained for history",fontWeight=FontWeight.Medium)
                if(observation.note.isNotBlank()) Text(if(hidden) "Note hidden" else observation.note)
                Text("Recorded ${observation.recordedAt.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm:ss z"))}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item{DetailsDisclosure("How this balance is calculated"){
            Text("The latest dated observation carries forward. Only eligible cash movements after it, or explicitly applied to it, change the provisional balance. A new bank observation absorbs earlier movements.")
            Text("Corrections retain previous revisions. A balance change is not automatically income or investment return.")
        }}
    }
}
