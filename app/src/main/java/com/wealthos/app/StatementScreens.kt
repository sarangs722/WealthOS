package com.wealthos.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wealthos.finance.*
import java.time.YearMonth
import java.util.UUID

data class StatementImportState(val busy:Boolean=false,val parsed:ParsedStatement?=null,val openScreen:Boolean=false,val message:String?=null)

@Composable fun StatementsScreen(state:BankUiState,hidden:Boolean,chooseFile:()->Unit,onReview:(UUID?)->Unit) {
    if(state.demo){EmptyPanel("Statement imports","Exit demo mode to import a statement.");return}
    if(state.statementsLoading){Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center){CircularProgressIndicator()};return}
    val book=state.statements
    val months=book.imports.map {it.month}.distinct().sortedDescending()
    var monthText by rememberSaveable {mutableStateOf((months.firstOrNull() ?: YearMonth.now()).toString())}
    val month=YearMonth.parse(monthText);val totals=book.totals(month)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text("Statement spending",style=MaterialTheme.typography.headlineMedium);Text("Import, review, then save your monthly spending.")}
        item {Button(onClick=chooseFile,enabled=!state.demo && !state.saving && !state.statementImport.busy,modifier=Modifier.fillMaxWidth().testTag("choose-statement")){Text("Import statement")}}
        if(state.statementImport.busy)item {LinearProgressIndicator(Modifier.fillMaxWidth());Text("Checking file locally…")}
        state.statementImport.message?.let {message->item {Text(message)}}
        if(state.statementImport.parsed!=null)item {OutlinedButton(onClick={onReview(null)},modifier=Modifier.fillMaxWidth()){Text("Continue review")}}
        if(months.isNotEmpty()) {
            item {Choice("Month",monthText,months.map {it.toString() to it.toString()}) {monthText=it}}
            item {SectionCard("$month · INR") {
                Text(if(totals.reviewedRows==0)"Not reviewed" else money(totals.netSpending,CurrencyCode.INR,hidden),style=MaterialTheme.typography.headlineLarge,modifier=Modifier.testTag("statement-spending-total"))
                Text("Reviewed spending less refunds")
                Text(if(totals.sourceCoverageConfirmed)"Reviewed for the imported sources. Other accounts may be missing." else "Partial / draft · ${totals.unresolved} items need review")
                Text("Bank cash change ${money(totals.bankCashChange,CurrencyCode.INR,hidden)}")
                Text("Card repayments from bank ${money(totals.bankCardRepayments,CurrencyCode.INR,hidden)} · excluded from spending")
            }}
            items(book.imports.filter {it.month==month}.sortedByDescending {it.createdAt},key={it.id}) {batch->
                val review=book.latest(batch.id)!!
                OutlinedCard(onClick={onReview(batch.id)},modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(if(hidden)batch.kind.label else batch.sourceName,style=MaterialTheme.typography.titleMedium)
                    Text("${batch.kind.label} · Revision ${review.revision}${if(review.voided)" · Voided" else ""}")
                    Text("Dates in file ${dateLabel(batch.firstCovered)} – ${dateLabel(batch.lastCovered)}")
                }}
            }
        } else item {SectionCard("Start with a statement") {Text("HDFC Bank and Tata Neu HDFC card: original XLS or matching UTF-8 CSV, up to 256 KB. Files stay on your device.")}}
        item {Text("Spending uses transaction dates. Imports do not change bank balances, card debt or your manual cash-flow summary.",style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable fun StatementReviewScreen(state:BankUiState,hidden:Boolean,model:BankViewModel,existingId:UUID?,openSaved:(UUID)->Unit={},done:()->Unit) {
    if(state.demo){EmptyPanel("Statement imports","Exit demo mode to review personal statements.");return}
    if(state.statementsLoading){Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center){CircularProgressIndicator()};return}
    val book=state.statements;val batch=book.imports.find {it.id==existingId};val savedReview=batch?.let {book.latest(it.id)}
    val parsed=if(batch==null)state.statementImport.parsed else ParsedStatement(batch.kind,batch.fileHash,batch.firstCovered,batch.lastCovered,book.lines.filter {it.importId==batch.id}.map {it.row},batch.parserVersion,batch.periodStart,batch.periodEnd,batch.currency)
    if(parsed==null){EmptyPanel("Choose a statement","The unsaved file expired. Import it again to continue.");return}
    key(parsed.fileHash,existingId,savedReview?.revision) {
        val knownNames=book.imports.filter {it.kind==parsed.kind}.map {it.sourceName}.distinct()
        var name by rememberSaveable {mutableStateOf(batch?.sourceName ?: knownNames.singleOrNull() ?: if(knownNames.isEmpty())parsed.kind.label else "")}
        var monthText by rememberSaveable {mutableStateOf((batch?.month ?: parsed.months().first()).toString())}
        val month=YearMonth.parse(monthText);val rows=parsed.rows.filter {YearMonth.from(it.date)==month}
        val alreadyImported=if(batch==null)book.imports.find {it.fileHash==parsed.fileHash && it.month==month} else null
        val initial=if(savedReview==null)emptyMap() else book.decisions.filter {it.reviewId==savedReview.id}.associate {d->book.lines.single {it.id==d.lineId}.row.index to d.category}
        val categories=remember(monthText) {mutableStateMapOf<Int,SpendingCategory>().apply {rows.forEach {put(it.index,initial[it.index] ?: SpendingCategory.UNREVIEWED)}}}
        val keep=remember(monthText) {mutableStateMapOf<Int,Boolean>().apply {if(savedReview!=null)book.decisions.filter {it.reviewId==savedReview.id && it.overlapKept}.forEach {d->put(book.lines.single {it.id==d.lineId}.row.index,true)}}}
        var complete by remember(monthText) {mutableStateOf(savedReview?.coverageConfirmed ?: false)}
        var showVoid by remember {mutableStateOf(false)}
        var onlyReview by remember {mutableStateOf(false)}
        var showName by remember {mutableStateOf(false)}
        var showDates by remember {mutableStateOf(false)}
        val overlaps=book.overlaps(parsed.kind,name,rows,existingId)
        val remaining=categories.values.count {it in listOf(SpendingCategory.UNREVIEWED,SpendingCategory.LOAN_PAYMENT)}
        val unresolvedOverlap=overlaps.any {categories[it] !in listOf(SpendingCategory.DUPLICATE,SpendingCategory.EXCLUDED) && keep[it]!=true}
        val net=rows.fold(java.math.BigDecimal.ZERO){sum,r->when(categories[r.index]){SpendingCategory.ESSENTIAL,SpendingCategory.DISCRETIONARY->sum+r.amount;SpendingCategory.REFUND->sum-r.amount;else->sum}}
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                item {Text(if(batch==null)"Review statement" else "Revise statement",style=MaterialTheme.typography.titleLarge)}
                if(batch==null) {
                    item {Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Text(if(hidden)parsed.kind.label else name.ifBlank {"Choose account / card"},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick={showName=true}){Text("Change")}}}
                    item {Choice("Month",monthText,parsed.months().map {it.toString() to it.toString()}){monthText=it}}
                }
                if(alreadyImported!=null)item {SectionCard("This file and month are already saved") {Button(onClick={openSaved(alreadyImported.id)},modifier=Modifier.fillMaxWidth()){Text("Open saved statement")};Text("Revise the saved import or choose another month. Totals will not be added twice.")}}
                item {Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("Reviewed spending · INR",style=MaterialTheme.typography.labelLarge)
                    Text(if(categories.values.none {it !in listOf(SpendingCategory.UNREVIEWED,SpendingCategory.LOAN_PAYMENT)})"Not reviewed" else money(net,CurrencyCode.INR,hidden),style=MaterialTheme.typography.headlineMedium)
                    Text("${rows.size} items · $remaining need review",style=MaterialTheme.typography.bodyMedium)
                }}}
                item {TextButton(onClick={showDates=true}){Text("Dates & coverage")}}
                parsed.periodStart?.let {start->if(parsed.firstCovered<start || parsed.lastCovered>parsed.periodEnd!!)item {Text("Some transaction dates extend beyond the requested period.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}}
                if(parsed.kind==StatementKind.HDFC_NEU_CARD && alreadyImported==null)item {OutlinedButton(onClick={rows.filter {it.direction==StatementDirection.DEBIT && categories[it.index]==SpendingCategory.UNREVIEWED && it.index !in overlaps && !Regex("(?i)\\b(EMI|LOAN|INSTALMENT|INSTALLMENT|CASH ADVANCE)\\b").containsMatchIn(it.description)}.forEach {categories[it.index]=SpendingCategory.DISCRETIONARY};complete=false;onlyReview=true},enabled=!state.saving,modifier=Modifier.fillMaxWidth()) {Text("Confirm card spending")};Text("Review repayments, refunds and loan/EMI entries separately.",style=MaterialTheme.typography.bodySmall)}
                item {Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Switch(onlyReview,{onlyReview=it},modifier=Modifier.semantics {contentDescription="Only items needing review"});Text("Needs review only",Modifier.padding(start=10.dp))}}
                items(rows.filter {alreadyImported==null && (!onlyReview || categories[it.index] in listOf(SpendingCategory.UNREVIEWED,SpendingCategory.LOAN_PAYMENT) || (it.index in overlaps && keep[it.index]!=true && categories[it.index] !in listOf(SpendingCategory.DUPLICATE,SpendingCategory.EXCLUDED)))},key={it.index}) {row->
                    var expanded by remember {mutableStateOf(false)}
                    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(if(hidden)"Transaction hidden" else row.description,style=MaterialTheme.typography.titleSmall)
                        Text("${dateLabel(row.date)} · ${if(row.direction==StatementDirection.CREDIT)"Credit" else "Debit"} ${money(row.amount,CurrencyCode.INR,hidden)}")
                        row.valueDate?.takeIf {it!=row.date}?.let {valueDate->Text("Value date ${dateLabel(valueDate)}",style=MaterialTheme.typography.bodySmall)}
                        Box {TextButton(onClick={expanded=true},enabled=!state.saving,modifier=Modifier.testTag("statement-category-${row.index}")){Text(categories.getValue(row.index).label)}
                            DropdownMenu(expanded,{expanded=false}) {
                                SpendingCategory.entries.filter {runCatching {StatementRules.decision(parsed.kind,row,it)}.isSuccess}.forEach {category->DropdownMenuItem(text={Text(category.label)},onClick={categories[row.index]=category;complete=false;expanded=false})}
                            }
                        }
                        if(row.index in overlaps && categories[row.index] !in listOf(SpendingCategory.DUPLICATE,SpendingCategory.EXCLUDED)) {
                            Text("Possible overlap with an earlier import",color=MaterialTheme.colorScheme.error)
                            Row {Checkbox(keep[row.index]==true,onCheckedChange={keep[row.index]=it;complete=false},enabled=!state.saving,modifier=Modifier.semantics {contentDescription="Keep row ${row.index} as a separate real transaction"});Text("Keep as a separate real transaction",Modifier.weight(1f).padding(top=12.dp))}
                        }
                    }}
                }
                item {Row {Checkbox(complete,onCheckedChange={complete=it},enabled=remaining==0 && !unresolvedOverlap && !state.saving,modifier=Modifier.semantics {contentDescription="All transactions for this source and month are included"});Text("All transactions for this source and month are included",Modifier.weight(1f).padding(top=12.dp))}}
                if(batch!=null)item {TextButton(onClick={showVoid=true},enabled=!state.saving){Text(if(savedReview!!.voided)"Keep voided" else "Void this import")};Text("Earlier revisions remain in your encrypted backup. Voiding removes this import from spending totals.",style=MaterialTheme.typography.bodySmall)}
                state.formError?.let {error->item {Text(error,color=MaterialTheme.colorScheme.error)}}
                item {Text("Refunds reduce spending on their actual date. Card repayments and own transfers are excluded from spending. Loan payments stay incomplete until their split is known.",style=MaterialTheme.typography.bodySmall)}
            }
            Surface(tonalElevation=3.dp) {Button(onClick={
                val choices=categories.toMap();val kept=keep.filterValues {it}.keys
                if(batch==null)model.saveStatement(parsed,name,month,choices,kept,complete,done)
                else model.reviseStatement(batch.id,savedReview!!.revision,choices,kept,complete,false,done)
            },enabled=!state.saving && !unresolvedOverlap && name.trim().length in 1..60 && alreadyImported==null,modifier=Modifier.fillMaxWidth().padding(16.dp).testTag("save-statement")) {Text(if(state.saving)"Saving…" else if(remaining>0)"Save draft" else if(savedReview?.voided==true)"Restore import" else "Save reviewed spending")}}
        }
        if(showName)AlertDialog(onDismissRequest={showName=false},title={Text("Account / card")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {Field("Nickname",name,{name=it;keep.clear();complete=false});if(knownNames.isNotEmpty())Choice("Existing source",name,(knownNames+name).distinct().map {it to it}){name=it;keep.clear();complete=false};Text("Use the same nickname for statements from the same account or card.")}},confirmButton={TextButton(onClick={showName=false},enabled=name.trim().length in 1..60){Text("Done")}})
        if(showDates)AlertDialog(onDismissRequest={showDates=false},title={Text("Dates & coverage")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {Text("Transaction dates: ${dateLabel(parsed.firstCovered)} – ${dateLabel(parsed.lastCovered)}");parsed.periodStart?.let {Text("Requested period: ${dateLabel(it)} – ${dateLabel(parsed.periodEnd!!)}")};Text("Monthly spending uses transaction dates. Value dates are retained separately. A billing cycle or partial export may not cover the full calendar month.")}},confirmButton={TextButton(onClick={showDates=false}){Text("Done")}})
        if(showVoid)AlertDialog(onDismissRequest={showVoid=false},title={Text("Void statement import?")},text={Text("This removes its spending totals. All imported rows and earlier reviews are retained.")},confirmButton={TextButton(onClick={showVoid=false;model.reviseStatement(batch!!.id,savedReview!!.revision,categories.toMap(),keep.filterValues {it}.keys,false,true,done)}){Text("Void import")}},dismissButton={TextButton(onClick={showVoid=false}){Text("Cancel")}})
    }
}
