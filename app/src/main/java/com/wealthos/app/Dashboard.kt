package com.wealthos.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wealthos.finance.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID

internal fun Modifier.semanticsLabel(label: String) = semantics { contentDescription = label }
private val ShortDate = DateTimeFormatter.ofPattern("d MMM uuuu", java.util.Locale.ENGLISH)
internal fun dateLabel(date: LocalDate) = date.format(ShortDate)
internal fun recordedLabel(instant:java.time.Instant)=instant.atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm:ss z",java.util.Locale.ENGLISH))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeScreen(book: BankBook, hidden: Boolean, demo: Boolean, onAdd: () -> Unit,
                          onDemo: () -> Unit, onAccount: (UUID) -> Unit, onPrivacy: () -> Unit,
                          onCashFlow:()->Unit={},onIncome:()->Unit={},onProtect:()->Unit={},onAnalytics:()->Unit={},onPublic:()->Unit={},onPortfolio:()->Unit={}) {
    val today=LocalDate.now();val valuation=WealthValuation.at(book,today)
    val empty=book.accounts.isEmpty() && book.portfolio.instruments.isEmpty()
    val recent=book.household.cashFlows.filter {it.currency==CurrencyCode.INR && it.month<=YearMonth.now()}.maxWithOrNull(compareBy<MonthlyCashFlow>{it.month}.thenBy{it.revision})
    val cash=recent?.let {CashFlows.at(book.household,it.month,CurrencyCode.INR)}
    val reserve=EmergencyFunds.at(book,today)
    var dataDetails by rememberSaveable {mutableStateOf(false)}
    val needsAttention=valuation.missingAccounts+valuation.foreignAccounts+valuation.staleAccounts+valuation.staleFx
    LazyColumn(Modifier.fillMaxSize().testTag("home-list"),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=8.dp,bottom=100.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {Text(dateLabel(today),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(empty){
            item {Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
                Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                    Icon(WealthIcons.wallet,null,Modifier.size(36.dp),tint=MaterialTheme.colorScheme.primary)
                    Text("Your wealth starts here",style=MaterialTheme.typography.headlineMedium)
                    Text("Add an account or investment to see your net worth.",color=MaterialTheme.colorScheme.onPrimaryContainer)
                    Button(onAdd){Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Add your first record")}
                    TextButton(onDemo){Text("Try fictional demo")}
                }
            }}
        }else{
            item {Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF153E35))){
                Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){Text("Net worth",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,color=Color(0xFFD1E9DB));TextButton(onPrivacy,colors=ButtonDefaults.textButtonColors(contentColor=Color.White)){Text(if(hidden)"Show" else "Hide")}}
                    AmountDisplay(money(valuation.netWorthInr,hidden=hidden),Modifier.testTag("net-worth-value"),style=MaterialTheme.typography.headlineLarge,color=Color.White)
                    Text(if(valuation.complete)"Assets minus debt · INR" else "Incomplete · some values or exchange rates are missing",style=MaterialTheme.typography.bodyMedium,color=Color(0xFFD1E9DB))
                    if(valuation.assets.any {it.status.contains("estimate",true)})Text("Includes estimated values",style=MaterialTheme.typography.labelMedium,color=Color(0xFFD1E9DB))
                }
            }}
            item {AdaptivePair(stack=maxOf(money(valuation.assetsInr,hidden=hidden).length,money(valuation.liabilitiesInr,hidden=hidden).length)>12,tag="metric-pair",
                first={MetricCard("Assets",money(valuation.assetsInr,hidden=hidden),it)},second={MetricCard("Debt",money(valuation.liabilitiesInr,hidden=hidden),it)})}
            if(needsAttention>0 || valuation.inputs.any {it.provisional})item {
                NavigationRow(if(!valuation.complete)"Complete your wealth picture" else "Records to review",
                    listOfNotNull(if(valuation.missingAccounts>0)"${valuation.missingAccounts} missing values" else null,if(valuation.foreignAccounts>0)"${valuation.foreignAccounts} missing FX rates" else null,if(valuation.staleAccounts+valuation.staleFx>0)"${valuation.staleAccounts+valuation.staleFx} stale inputs" else null,if(valuation.inputs.any{it.provisional})"Provisional cash" else null).joinToString(" · "),Icons.Default.Info,{dataDetails=true})
            }
        }
        item {SectionHeading("Your next steps")}
        item {AdaptivePair(first={cardModifier->
            Card(onClick=onCashFlow,modifier=cardModifier,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("Cash flow",style=MaterialTheme.typography.titleMedium)
                    if(recent==null)Text("Add monthly summary",style=MaterialTheme.typography.titleMedium)else AmountDisplay(money(cash?.remainingCash,hidden=hidden),style=MaterialTheme.typography.titleMedium)
                    Text(if(recent==null)"Income & expenses" else "Left after investing · ${recent.month}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },second={cardModifier->
            Card(onClick=onProtect,modifier=cardModifier,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("Emergency fund",style=MaterialTheme.typography.titleMedium)
                    Text(if(reserve.target==null)"Set a target" else money(reserve.allocatedInr,hidden=hidden),style=MaterialTheme.typography.titleMedium)
                    Text(if(reserve.target==null)"Build your safety net" else "of ${money(reserve.target!!.targetInr,hidden=hidden)}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        })}
        if(!empty){
            item {SectionHeading("Accounts", "View all",onPortfolio)}
            items(valuation.inputs.sortedByDescending{it.observation?.effectiveDate}.take(3),key={it.account.id}){input->AccountCard(input,hidden){onAccount(input.account.id)}}
            if(valuation.inputs.isEmpty())item {NavigationRow("View your holdings","Investments, deposits and property",WealthIcons.wallet,onPortfolio)}
            item {NavigationRow("Wealth insights","History, allocation and returns",WealthIcons.forecast,onAnalytics)}
            item {DetailsDisclosure("Net worth details"){
                Text("Liquid net worth ${money(valuation.liquidNetWorthInr,hidden=hidden)}",style=MaterialTheme.typography.titleMedium)
                Text("Accessible cash and assets marked liquid, less all debt. Insurance cover and earmarked funds do not add wealth.")
                valuation.allocation.forEach {(label,amount)->Row(Modifier.fillMaxWidth()){Text(label,Modifier.weight(1f));Text(money(amount,hidden=hidden))}}
                valuation.currencyExposure.forEach{(currency,amount)->Text("${currency.name} exposure ${money(amount,currency,hidden)}")}
                TextButton({dataDetails=true}){Text("View sources & data status")}
            }}
        }
    }
    if(dataDetails)ModalBottomSheet(onDismissRequest={dataDetails=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Data status",style=MaterialTheme.typography.headlineSmall)
            Text(if(valuation.complete)"All recorded positions can be valued" else "Your INR total is incomplete")
            if(!valuation.complete)Text("Known INR subtotal ${money(valuation.knownInrSubtotal,hidden=hidden)}")
            Text("${valuation.missingAccounts} missing values · ${valuation.foreignAccounts} missing FX rates")
            Text("${valuation.staleAccounts} inputs older than 30 days · ${valuation.staleFx} FX inputs older than 7 days")
            Text("Provisional cash includes recorded movements not yet absorbed by a dated bank balance. Contract estimates remain separate from observations.")
            Text("Balances carry forward until updated. A change in balance is not automatically income or investment return.")
            TextButton({dataDetails=false;onPublic()}){Text("Market data & exchange rates")}
            TextButton({dataDetails=false;onPortfolio()}){Text("Open portfolio")}
        }
    }
}

@Composable private fun MonthlyOverview(book:BankBook,summary:MonthlyCashFlow,hidden:Boolean) {
    SectionCard("Monthly surplus · ${summary.month}") {
        val result=CashFlows.at(book.household,summary.month,CurrencyCode.INR)
        Text(money(result.surplusBeforeInvestments,hidden=hidden),style=MaterialTheme.typography.titleLarge)
        Text("Before investment contributions · Remaining ${money(result.remainingCash,hidden=hidden)}")
        Text(if(summary.month==YearMonth.now())"Current month / month-to-date. Confirm completeness in Monthly cash flow." else "Based on the recorded monthly summary, not bank balance changes.")
    }
}

@Composable internal fun HistoryCard(book: BankBook, hidden: Boolean) {
    val history = WealthValuation.history(book,LocalDate.now())
    val complete = history.filter { it.complete }
    SectionCard("Recorded wealth") {
        if (hidden) Text("History hidden while amounts are obscured.")
        else if (history.size < 2) Text("Add balances on another date to see recorded wealth movement.")
        else {
            val first = history.first(); val last = history.last()
            Text("${dateLabel(first.asOf)} – ${dateLabel(last.asOf)}", style = MaterialTheme.typography.bodyMedium)
            if (first.complete && last.complete) {
                Text("Balance change ${money(last.netWorthInr!! - first.netWorthInr!!)}", style = MaterialTheme.typography.titleMedium)
            } else Text("Period change unavailable: incomplete starting or ending data.")
            if (complete.size >= 2) {
                val color = MaterialTheme.colorScheme.primary
                val low = complete.minOf { it.netWorthInr!! }; val high = complete.maxOf { it.netWorthInr!! }
                val span = (high-low).takeUnless { it.signum()==0 } ?: java.math.BigDecimal.ONE
                Canvas(Modifier.fillMaxWidth().height(116.dp).padding(vertical=12.dp).semanticsLabel("Recorded bank wealth chart. Exact dates and values follow below.")) {
                    val points = history.map { value ->
                        value.netWorthInr?.let { net ->
                            val x = ((value.asOf.toEpochDay()-first.asOf.toEpochDay()).toFloat() /
                                (last.asOf.toEpochDay()-first.asOf.toEpochDay()).toFloat()) * size.width
                            val y = size.height * (1f-(net-low).divide(span,8,java.math.RoundingMode.HALF_EVEN).toFloat())
                            Offset(x,y.coerceIn(2f,size.height-2f))
                        }
                    }
                    points.zipWithNext().forEach { (a,b) -> if(a!=null && b!=null) {
                        val corner=Offset(b.x,a.y)
                        drawLine(color,a,corner,3.dp.toPx()); drawLine(color,corner,b,3.dp.toPx())
                    } }
                    points.filterNotNull().forEach { drawCircle(color,4.dp.toPx(),it) }
                }
            }
            history.takeLast(6).forEach { value ->
                Row(Modifier.fillMaxWidth()) {
                    Text(dateLabel(value.asOf),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    Text(money(value.netWorthInr),style=MaterialTheme.typography.bodyMedium)
                }
            }
            DetailsDisclosure("Chart details"){Text("Latest 6 dates shown. Gaps mean incomplete data; observations carry forward. Contract estimates may accrue between dates. This chart shows wealth movement, not investment return.")}
        }
    }
}

@Composable internal fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
@Composable private fun MetricCard(title: String, amount: String, modifier: Modifier) {
    Card(modifier, shape=RoundedCornerShape(20.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,style=MaterialTheme.typography.bodyMedium)
            AmountDisplay(amount,Modifier.testTag("${title.lowercase()}-value"))
        }
    }
}

@Composable fun PortfolioScreen(book: BankBook, hidden: Boolean, demo: Boolean, onAdd: () -> Unit, onAccount: (UUID) -> Unit,
                               onAddAsset:()->Unit={},onAsset:(UUID)->Unit={},onTransfer:()->Unit={},onFx:()->Unit={}) {
    var query by rememberSaveable {mutableStateOf("")};var newest by rememberSaveable {mutableStateOf(false)}
    var filter by rememberSaveable {mutableStateOf("All")};var menu by remember {mutableStateOf(false)}
    val inputs=BankValuation.at(book,LocalDate.now()).inputs.filter{it.account.name.contains(query,true)||it.account.institution.contains(query,true)}.let{if(newest)it.sortedByDescending{v->v.observation?.effectiveDate}else it.sortedBy{v->v.account.name.lowercase()}}
    val assetValues=WealthValuation.at(book,LocalDate.now()).assets.filter{it.instrument.name.contains(query,true)||it.instrument.category.label.contains(query,true)}
    fun group(category:AssetCategory)=when(category){AssetCategory.FD,AssetCategory.RD->"Deposits";AssetCategory.EPF,AssetCategory.PPF,AssetCategory.NPS->"Retirement";AssetCategory.PROPERTY->"Property";AssetCategory.LOAN->"Loans";else->"Investments"}
    LazyColumn(Modifier.fillMaxSize().testTag("portfolio-list"),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=8.dp,bottom=100.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(query,{query=it},placeholder={Text("Accounts, investments or loans")},label={Text("Search portfolio")},leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.weight(1f),singleLine=true)
            Box {IconButton({menu=true}){Icon(Icons.Default.MoreVert,"Portfolio options")};DropdownMenu(menu,{menu=false}){
                DropdownMenuItem(text={Text(if(newest)"Sort by name" else "Sort by last update")},onClick={newest=!newest;menu=false})
                if(!demo){DropdownMenuItem(text={Text("Record a transfer")},onClick={menu=false;onTransfer()});DropdownMenuItem(text={Text("Exchange rates")},onClick={menu=false;onFx()})}
            }}
        }}
        item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("All","Cash","Investments","Deposits","Retirement","Property","Loans").forEach {label->FilterChip(filter==label,{filter=label},label={Text(label)})}
        }}
        if(filter in listOf("All","Cash") && inputs.isNotEmpty()){
            item {SectionHeading("Cash accounts")}
            items(inputs,key={it.account.id}){input->AccountCard(input,hidden){onAccount(input.account.id)}}
        }
        AssetCategory.entries.forEach {category->
            val rows=assetValues.filter{it.instrument.category==category && (filter=="All" || filter==group(category))}.let{if(newest)it.sortedByDescending{v->v.date}else it.sortedBy{v->v.instrument.name.lowercase()}}
            if(rows.isNotEmpty())item {SectionHeading(category.label)}
            items(rows,key={it.instrument.id}){value->
                Surface(onClick={onAsset(value.instrument.id)},shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow){
                    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Column(Modifier.weight(1f)){
                            Text(value.instrument.name,style=MaterialTheme.typography.titleMedium)
                            Text(value.date?.let(::dateLabel) ?: "Value needed",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(value.status.contains("estimate",true))StatusPill("Estimated")
                        }
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.End){Text(money(value.nativeValue,value.instrument.currency,hidden),style=MaterialTheme.typography.titleMedium);Text(value.instrument.currency.name,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    }
                }
            }
        }
        val visible=(if(filter in listOf("All","Cash"))inputs.size else 0)+assetValues.count{filter=="All" || filter==group(it.instrument.category)}
        if(visible==0)item {SectionCard(if(query.isBlank())"Nothing here yet" else "No matching records"){
            Text(if(query.isBlank())"Start with a bank balance or existing holdings." else "Try another name or category.")
            if(!demo && query.isBlank())Button(onAdd){Text("Add a record")}
        }}
    }
}

@Composable internal fun AccountCard(input: BalanceInput, hidden: Boolean, onClick: () -> Unit) {
    Surface(onClick=onClick,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow){
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Icon(WealthIcons.wallet,null,tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)){
                Text(input.account.name,style=MaterialTheme.typography.titleMedium)
                Text(input.account.institution.ifBlank{input.account.kind.label},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(!input.provisional)Text(input.observation?.effectiveDate?.let(::dateLabel) ?: "Balance needed",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(input.provisional)Text("Provisional",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.error)
            }
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.End){
                Text(money(input.value,input.account.currency,hidden),style=MaterialTheme.typography.titleMedium)
                Text(input.account.currency.name,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
