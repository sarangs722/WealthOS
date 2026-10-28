package com.wealthos.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wealthos.finance.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val LocalFormEnabled=compositionLocalOf {true}
@Composable internal fun Form(title:String,subtitle:String,state:BankUiState,saveLabel:String="Save record",onSave:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                if(subtitle.length>90) InfoHelp("About this record",subtitle)
            }
            if(subtitle.isNotBlank() && subtitle.length<=90)Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            CompositionLocalProvider(LocalFormEnabled provides (!state.saving && !state.demo)) {content()}
        }
        Surface(tonalElevation=2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                state.formError?.let {Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.semantics {contentDescription="Validation error: $it"})}
                Button(onClick=onSave,enabled=!state.saving && !state.demo,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {Text(if(state.saving) "Saving…" else saveLabel)}
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun DateChoice(label:String,value:String,change:(String)->Unit) {
    var open by remember {mutableStateOf(false)}
    val parsed=runCatching {LocalDate.parse(value)}.getOrNull()
    TextButton(onClick={open=true},enabled=LocalFormEnabled.current,contentPadding=PaddingValues(horizontal=4.dp)) {
        Text("$label: ${if(parsed==LocalDate.now())"Today" else parsed?.let(::dateLabel) ?: "Choose date"}")
    }
    if(open) {
        val picker=rememberDatePickerState(initialSelectedDateMillis=parsed?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(onDismissRequest={open=false},confirmButton={TextButton(onClick={picker.selectedDateMillis?.let {change(Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())};open=false},enabled=picker.selectedDateMillis!=null){Text("Use date")}},dismissButton={TextButton(onClick={open=false}){Text("Cancel")}}) {DatePicker(picker)}
    }
}
@Composable internal fun FormDetails(title:String,content:@Composable ColumnScope.()->Unit) {
    var expanded by rememberSaveable {mutableStateOf(false)}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clickable(role=androidx.compose.ui.semantics.Role.Button){expanded=!expanded}.heightIn(min=48.dp).padding(horizontal=4.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
            Icon(if(expanded)Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,if(expanded)"Collapse $title" else "Expand $title",tint=MaterialTheme.colorScheme.primary)
        }
        if(expanded)Column(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
    }
}
@Composable internal fun Field(label:String,value:String,change:(String)->Unit,decimal:Boolean=false,hint:String?=null,multi:Boolean=false) {
    OutlinedTextField(value,{if(it.length<=if(multi)500 else 80)change(it)},label={Text(label)},
        modifier=Modifier.fillMaxWidth(),singleLine=!multi,minLines=if(multi)2 else 1,enabled=LocalFormEnabled.current,
        keyboardOptions=KeyboardOptions(keyboardType=if(decimal)KeyboardType.Decimal else KeyboardType.Text),
        trailingIcon=if(hint!=null){{InfoHelp(label,hint)}}else null)
}
@Composable internal fun Choice(label:String,selected:String,options:List<Pair<String,String>>,onSelect:(String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    val text=options.find {it.first==selected}?.second ?: "Choose…"
    Box(Modifier.fillMaxWidth()) {
        OutlinedCard(onClick={expanded=true},enabled=LocalFormEnabled.current,modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.small) {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text("$label: $text",Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                Icon(Icons.Default.KeyboardArrowDown,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=380.dp)) {
            options.forEach {(key,value) -> DropdownMenuItem(text={Text(value)},onClick={onSelect(key);expanded=false})}
        }
    }
}
@Composable internal fun Toggle(label:String,value:Boolean,change:(Boolean)->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,Modifier.weight(1f));Switch(value,change,enabled=LocalFormEnabled.current,modifier=Modifier.semantics {contentDescription=label})
    }
}
@Composable internal fun CashLinkFields(accounts:List<BankAccount>,currency:CurrencyCode,selected:String,mode:String,
                                      choose:(String)->Unit,treatment:(String)->Unit,label:String="Cash account",optional:Boolean=true) {
    val options=(if(optional)listOf("" to "Unlinked / outside tracked cash") else listOf("" to "Choose account"))+
        accounts.filter {it.currency==currency}.map {it.id.toString() to "${it.name} (${it.currency})"}
    Choice(label,selected,options,choose)
    if(selected.isNotBlank()) {
        Choice("$label treatment",mode,listOf("" to "Choose explicitly","apply" to "Apply provisional cash adjustment","reflected" to "Balance already reflects this cash flow"),treatment)
        FormDetails("How linked cash is counted"){Text("A later balance observation absorbs cash flows through its date. Same-day or backdated flows use your choice above. Provisional amounts are labelled.")}
    } else if(optional) Text("Unlinked: tracked cash stays unchanged.",style=MaterialTheme.typography.bodyMedium)
}
internal fun cashLink(selected:String,mode:String):CashLink? {
    if(selected.isBlank())return null
    require(mode=="apply" || mode=="reflected") {"Choose whether the cash flow is already reflected or should adjust the balance."}
    return CashLink(UUID.fromString(selected),mode=="apply")
}
private fun tracking(category:AssetCategory)=when(category) {
    AssetCategory.FD,AssetCategory.RD->TrackingMode.DEPOSIT
    AssetCategory.LOAN->TrackingMode.LOAN
    AssetCategory.EPF,AssetCategory.PPF,AssetCategory.NPS,AssetCategory.PROPERTY->TrackingMode.OBSERVED
    else->TrackingMode.TRANSACTIONS
}

@Composable fun InstrumentForm(state:BankUiState,model:BankViewModel,existing:Instrument?=null,initialCategory:AssetCategory=AssetCategory.MUTUAL_FUND,done:(UUID)->Unit) {
    var detailed by rememberSaveable {mutableStateOf(false)}
    if(existing==null && HoldingSnapshots.availableForSnapshot(initialCategory) && !detailed) {
        QuickInvestmentForm(state,model,initialCategory,{detailed=true},done)
        return
    }
    var name by rememberSaveable {mutableStateOf(existing?.name ?: "")}
    var categoryName by rememberSaveable {mutableStateOf((existing?.category ?: initialCategory).name)}
    var currencyName by rememberSaveable {mutableStateOf((existing?.currency ?: CurrencyCode.INR).name)}
    var regionName by rememberSaveable {mutableStateOf((existing?.region ?: MarketRegion.INDIA).name)}
    var unit by rememberSaveable {mutableStateOf(existing?.unit ?: "units")}
    var liquid by rememberSaveable {mutableStateOf(existing?.liquid ?: (initialCategory in listOf(AssetCategory.MUTUAL_FUND,AssetCategory.STOCK,AssetCategory.ETF)))}
    var custody by rememberSaveable {mutableStateOf(existing?.custodyAccountId?.toString() ?: "")}
    var openingDate by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var units by rememberSaveable {mutableStateOf("")}
    var cost by rememberSaveable {mutableStateOf("")}
    var value by rememberSaveable {mutableStateOf("")}
    var principal by rememberSaveable {mutableStateOf("")}
    var annualRate by rememberSaveable {mutableStateOf("")}
    var endDate by rememberSaveable {mutableStateOf("")}
    var conventionName by rememberSaveable {mutableStateOf(DepositConvention.QUARTERLY.name)}
    var installment by rememberSaveable {mutableStateOf("")}
    var months by rememberSaveable {mutableStateOf("")}
    var cash by rememberSaveable {mutableStateOf("")};var treatment by rememberSaveable {mutableStateOf("")}
    val stableId by rememberSaveable {mutableStateOf(existing?.id?.toString() ?: UUID.randomUUID().toString())}
    val category=AssetCategory.valueOf(categoryName);val mode=existing?.tracking ?: tracking(category);val currency=CurrencyCode.valueOf(currencyName)
    Form(if(existing==null)"Add ${when(category){AssetCategory.EPF,AssetCategory.PPF,AssetCategory.NPS->category.name;AssetCategory.FD->"fixed deposit";AssetCategory.RD->"recurring deposit";AssetCategory.LOAN->"loan";else->category.label.lowercase()}}" else "Edit asset",if(existing==null)"Start from what you own today." else "Currency, opening date and tracking method are preserved.",state,onSave={
        val id=UUID.fromString(stableId)
        model.portfolio(done={done(id)}) {repo ->
            val accountId=custody.takeIf {it.isNotBlank()}?.let(UUID::fromString)
            if(existing!=null) repo.editInstrument(id,name,unit,MarketRegion.valueOf(regionName),liquid,accountId)
            else {
                val start=LocalDate.parse(openingDate)
                val i=Instrument(id,name.trim(),category,currency,mode,start,Instant.now(),liquid && mode!=TrackingMode.LOAN,MarketRegion.valueOf(regionName),unit.ifBlank {"units"},accountId)
                val contract=if(mode==TrackingMode.DEPOSIT || mode==TrackingMode.LOAN) {
                    val term=if(mode==TrackingMode.LOAN) requireNotNull(months.toIntOrNull()) {"Enter remaining months (1–1200)."} else 0
                    FinancialContract(id,Amounts.parse(principal,currency),DecimalInput.parse(annualRate,6).movePointLeft(2),start,
                        if(mode==TrackingMode.LOAN)start.plusMonths(term.toLong()) else LocalDate.parse(endDate),
                        DepositConvention.valueOf(conventionName),if(category==AssetCategory.RD && installment.isNotBlank())Amounts.parse(installment,currency) else BigDecimal.ZERO,term)
                } else null
                repo.addInstrument(NewInstrument(i,if(mode==TrackingMode.TRANSACTIONS && units.isNotBlank())DecimalInput.parse(units) else BigDecimal.ZERO,
                    cost.takeIf {it.isNotBlank() && mode==TrackingMode.TRANSACTIONS}?.let {Amounts.parse(it,currency)},
                    value.takeIf {it.isNotBlank()}?.let {if(mode==TrackingMode.TRANSACTIONS)DecimalInput.parse(it) else Amounts.parse(it,currency)},contract,
                    if(mode==TrackingMode.DEPOSIT)cashLink(cash,treatment) else null))
            }
        }
    }) {
        Field("Asset name",name,{name=it})
        if(existing!=null) Text("${category.label} · $currency · Opened ${dateLabel(existing.openedOn)}")
        if(existing==null) when(mode) {
            TrackingMode.TRANSACTIONS -> {
                Field("Units held",units,{units=it},true,"Leave blank or enter 0 for a new empty instrument. Purchases go in Details.")
                Field("Cost of these units (optional)",cost,{cost=it},true,"Acquisition cost in $currency, including known fees. Blank means unknown, not zero.")
                Field("NAV / unit price (optional)",value,{value=it},true,"Manual price in $currency, dated at the opening date. Up to 12 decimal places.")
                Text("Existing holdings only. Bank cash stays unchanged.",style=MaterialTheme.typography.bodyMedium)
                FormDetails("Adding earlier transactions"){Text("This opening position already includes your earlier purchases and corporate actions. Add only subsequent events to avoid counting holdings twice. Earlier history is not invented.")}
            }
            TrackingMode.OBSERVED -> {
                Field(if(category==AssetCategory.PROPERTY)"Your share of value in $currency" else "Current balance in $currency",value,{value=it},true,"Copy your latest statement balance, or enter only your share of a property. Returns are not assumed.")
            }
            TrackingMode.DEPOSIT,TrackingMode.LOAN -> {
                Field(if(mode==TrackingMode.LOAN)"Outstanding principal" else "Amount deposited",principal,{principal=it},true,"Known principal in $currency as of the opening date.")
                Field("Contract annual rate (%)",annualRate,{annualRate=it},true,"Nominal contracted rate, not a current advertised rate. Zero is supported.")
                if(mode==TrackingMode.LOAN) {
                    Field("Remaining loan months",months,{months=it},hint="1–1200. The projected monthly schedule does not record actual payments.")
                    FormDetails("About repayments"){Text("Start from a known outstanding principal. Add actual principal and interest components for each repayment. Future payments are never treated as paid.")}
                } else {
                    Field("Maturity date (YYYY-MM-DD)",endDate,{endDate=it})
                    FormDetails("Deposit details (optional)"){
                    Choice("Estimate convention",conventionName,DepositConvention.entries.map {it.name to it.label}) {conventionName=it}
                    if(category==AssetCategory.RD) Field("Scheduled monthly installment (optional)",installment,{installment=it},true,"Only installments you actually record increase the balance.")
                    Field("Observed total (optional)",value,{value=it},true,"A bank-observed total takes precedence over the separately shown gross contract estimate.")
                    FormDetails("Estimate assumptions"){Text("Generic estimate: completed anniversary periods plus an ACT/365 simple stub, or simple ACT/365. No tax, penalties or automatic post-maturity interest. Bank-specific amounts can differ. Interim interest payouts require the simple convention.")}
                    CashLinkFields(state.book.accounts,currency,cash,treatment,{cash=it;treatment=""},{treatment=it},"New deposit funding")
                    Text("Existing deposit? Leave funding unlinked.",style=MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if(existing==null)DateChoice("As of",openingDate,{openingDate=it})
        FormDetails("More options"){
            if(existing==null)Choice("Currency",currencyName,CurrencyCode.entries.map {it.name to it.name}) {currencyName=it;custody="";cash="";treatment=""}
            Choice("Market / location",regionName,MarketRegion.entries.map {it.name to if(it==MarketRegion.INDIA)"India" else "International"}) {regionName=it}
            if(mode==TrackingMode.TRANSACTIONS) Field("Unit label",unit,{unit=it},hint="Units, shares, grams, etc.")
            if(mode!=TrackingMode.LOAN) Toggle("Include in liquid assets",liquid) {liquid=it}
            Text("Liquid net worth uses cash and assets explicitly marked liquid, less all liabilities. Restricted retirement/property balances normally remain excluded.")
            if(mode==TrackingMode.TRANSACTIONS) Choice("Custody cash account",custody,listOf("" to "None / separate institution")+
                state.book.accounts.filter {it.kind==AccountKind.BROKER_CASH && it.currency==currency}.map {it.id.toString() to it.name}) {custody=it}
        }
    }
}

@Composable fun InvestmentEntryForm(instrument:Instrument,state:BankUiState,model:BankViewModel,done:()->Unit) {
    var kindName by rememberSaveable {mutableStateOf(TradeKind.BUY.name)}
    var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var units by rememberSaveable {mutableStateOf("")};var gross by rememberSaveable {mutableStateOf("")}
    var fees by rememberSaveable {mutableStateOf("0")};var denominator by rememberSaveable {mutableStateOf("1")}
    var note by rememberSaveable {mutableStateOf("")};var cash by rememberSaveable {mutableStateOf("")};var mode by rememberSaveable {mutableStateOf("")}
    val kind=TradeKind.valueOf(kindName);val ratio=kind==TradeKind.SPLIT || kind==TradeKind.BONUS
    val monetary=kind in listOf(TradeKind.BUY,TradeKind.SELL,TradeKind.DIVIDEND,TradeKind.REINVEST)
    Form("Add investment event",instrument.name,state,"Save event",onSave={model.portfolio(done) {repo ->
        val entry=InvestmentEntry(UUID.randomUUID(),instrument.id,kind,LocalDate.parse(date),0,
            if(kind==TradeKind.DIVIDEND)BigDecimal.ZERO else DecimalInput.parse(units),
            if(ratio || gross.isBlank())null else Amounts.parse(gross,instrument.currency),
            if(monetary)Amounts.parse(fees,instrument.currency) else BigDecimal.ZERO,
            if(ratio)DecimalInput.positive(denominator) else BigDecimal.ONE,note,Instant.now())
        repo.addEntry(entry,if(monetary)cashLink(cash,mode) else null)
    }}) {
        Choice("Event",kindName,TradeKind.entries.filter {it!=TradeKind.OPENING}.map {it.name to it.label}) {kindName=it;cash="";mode=""}
        Field("Event date (YYYY-MM-DD)",date,{date=it})
        if(kind!=TradeKind.DIVIDEND) Field(if(ratio)"New / additional units in ratio" else "Units (${instrument.unit})",units,{units=it},true,
            if(kind==TradeKind.ADJUST)"Signed unit change. Explain the adjustment below." else if(ratio)"Split: 2 for 1 changes 10 to 20; bonus: 1 extra for 1 adds 10." else "Actual units, up to 12 decimal places.")
        if(ratio) Field("Per existing units",denominator,{denominator=it},true)
        if(!ratio) Field(if(kind==TradeKind.ADJUST)"Cost of added units (optional)" else "Gross consideration in ${instrument.currency}",gross,{gross=it},true,
            if(kind==TradeKind.REINVEST)"Reinvested distribution amount. It adds both distribution income and acquisition basis; only fees reduce cash." else "Actual total amount, before fees. Not a rounded unit price.")
        if(monetary) Field("Fees / withholding in ${instrument.currency}",fees,{fees=it},true,"Buy fees add to basis; sale/distribution fees reduce proceeds. This is book cost, not a tax return.")
        Field("Explanation / note",note,{note=it},multi=true)
        if(monetary)CashLinkFields(state.book.accounts,instrument.currency,cash,mode,{cash=it;mode=""},{mode=it})
        FormDetails("Transaction rules"){Text("Entries are retained. FIFO consumes known lots. Missing opening basis makes gains unavailable. Splits/bonus issues adjust a prior carried quote theoretically until you enter a new observed price; that valuation is labelled an estimate. Backdated events are checked against every later recorded event before saving.")}
    }
}

@Composable fun ValuationForm(instrument:Instrument,state:BankUiState,model:BankViewModel,done:()->Unit) {
    if(instrument.tracking==TrackingMode.OBSERVED && HoldingSnapshots.availableForSnapshot(instrument.category)) {
        SnapshotUpdateForm(instrument,state,model,done);return
    }
    var value by rememberSaveable {mutableStateOf("")};var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())};var note by rememberSaveable {mutableStateOf("")}
    val price=instrument.tracking==TrackingMode.TRANSACTIONS
    Form(if(price)"Update unit price" else "Update observed value",instrument.name,state,"Save valuation",onSave={model.portfolio(done) {repo ->
        repo.addValuation(AssetValuation(UUID.randomUUID(),instrument.id,if(price)ValuationKind.UNIT_PRICE else ValuationKind.OBSERVED_TOTAL,
            if(price)DecimalInput.parse(value) else Amounts.parse(value,instrument.currency),LocalDate.parse(date),0,Instant.now(),note))
    }}) {
        Field(if(price)"Unit price in ${instrument.currency}" else "Observed total in ${instrument.currency}",value,{value=it},true)
        Field("Valuation date (YYYY-MM-DD)",date,{date=it})
        Field("Source / correction explanation",note,{note=it},multi=true)
        Text("Manual observation. Same-day corrections retain earlier revisions and need an explanation. Last-known values are labelled when carried forward.")
    }
}

@Composable fun ContractEventForm(instrument:Instrument,state:BankUiState,model:BankViewModel,done:()->Unit) {
    val contract=state.book.portfolio.contracts.first {it.instrumentId==instrument.id}
    val loan=instrument.tracking==TrackingMode.LOAN
    val kinds=if(loan)listOf(ContractEventKind.REPAYMENT) else buildList {
        if(instrument.category==AssetCategory.RD)add(ContractEventKind.CONTRIBUTION)
        add(ContractEventKind.MATURITY)
        if(contract.convention==DepositConvention.SIMPLE_ACT_365)add(ContractEventKind.INTEREST_PAYOUT)
    }
    var kindName by rememberSaveable {mutableStateOf(kinds.first().name)}
    val kind=ContractEventKind.valueOf(kindName)
    var principal by rememberSaveable {mutableStateOf("")};var interest by rememberSaveable {mutableStateOf("0")};var fees by rememberSaveable {mutableStateOf("0")}
    var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())};var note by rememberSaveable {mutableStateOf("")}
    var cash by rememberSaveable {mutableStateOf("")};var mode by rememberSaveable {mutableStateOf("")}
    Form("Record contract event",instrument.name,state,"Save contract event",onSave={model.portfolio(done) {repo ->
        repo.addContractEvent(ContractEvent(UUID.randomUUID(),instrument.id,kind,LocalDate.parse(date),0,
            if(kind==ContractEventKind.INTEREST_PAYOUT)BigDecimal.ZERO else Amounts.parse(principal,instrument.currency),
            if(kind==ContractEventKind.CONTRIBUTION)BigDecimal.ZERO else Amounts.parse(interest,instrument.currency),Amounts.parse(fees,instrument.currency),Instant.now(),note),cashLink(cash,mode))
    }}) {
        Choice("Contract event",kindName,kinds.map {it.name to it.label}) {kindName=it}
        Field("Event date (YYYY-MM-DD)",date,{date=it})
        if(kind!=ContractEventKind.INTEREST_PAYOUT)Field("Actual principal component",principal,{principal=it},true,
            if(kind==ContractEventKind.MATURITY)"Return the full recorded paid principal. Enter early-closure penalties in fees." else "${instrument.currency}. Principal and interest remain separate.")
        if(kind!=ContractEventKind.CONTRIBUTION)Field("Actual interest component",interest,{interest=it},true)
        Field("Fees / withholding",fees,{fees=it},true)
        Field("Explanation / statement reference",note,{note=it},multi=true)
        CashLinkFields(state.book.accounts,instrument.currency,cash,mode,{cash=it;mode=""},{mode=it})
        Text("Only actual events are recorded. Closing a deposit stops its asset value; its payout becomes tracked cash only when linked or observed in a bank balance. No automatic maturity payout is invented.")
    }
}

@Composable fun TransferForm(state:BankUiState,model:BankViewModel,done:()->Unit) {
    var from by rememberSaveable {mutableStateOf("")};var to by rememberSaveable {mutableStateOf("")}
    var fromMode by rememberSaveable {mutableStateOf("")};var toMode by rememberSaveable {mutableStateOf("")}
    var amount by rememberSaveable {mutableStateOf("")};var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())};var note by rememberSaveable {mutableStateOf("")}
    val currency=state.book.accounts.find {it.id.toString()==from}?.currency ?: CurrencyCode.INR
    Form("Internal transfer","Move existing cash between tracked accounts. No income or wealth is created.",state,"Save transfer",onSave={model.portfolio(done) {repo ->
        repo.transfer(requireNotNull(cashLink(from,fromMode)) {"Choose the source account."},requireNotNull(cashLink(to,toMode)) {"Choose the destination account."},Amounts.parse(amount,currency),LocalDate.parse(date),note)
    }}) {
        Choice("Transfer currency",currency.name,CurrencyCode.entries.map {it.name to it.name}) {selected ->
            from=state.book.accounts.firstOrNull {it.currency.name==selected}?.id?.toString() ?: "";to="";fromMode="";toMode=""
        }
        CashLinkFields(state.book.accounts,currency,from,fromMode,{from=it;fromMode=""},{fromMode=it},"From account",false)
        CashLinkFields(state.book.accounts,currency,to,toMode,{to=it;toMode=""},{toMode=it},"To account",false)
        Field("Transfer amount in $currency",amount,{amount=it},true)
        Field("Transfer date (YYYY-MM-DD)",date,{date=it})
        Field("Transfer explanation",note,{note=it},multi=true)
        Text("Both cash legs save atomically. Cross-currency transfers need actual FX transaction accounting and are not inferred from valuation rates.")
    }
}

@Composable fun FxForm(state:BankUiState,model:BankViewModel,hidden:Boolean=false,done:()->Unit) {
    var currency by rememberSaveable {mutableStateOf(CurrencyCode.USD.name)};var rate by rememberSaveable {mutableStateOf("")}
    var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())};var note by rememberSaveable {mutableStateOf("")}
    Form("Manual FX observation","A dated valuation rate, not an automatic market feed or a cash conversion.",state,"Save FX rate",onSave={model.portfolio(done) {
        it.addFx(CurrencyCode.valueOf(currency),DecimalInput.positive(rate),LocalDate.parse(date),note)
    }}) {
        Choice("Foreign currency",currency,CurrencyCode.entries.filter {it!=CurrencyCode.INR}.map {it.name to it.name}) {currency=it}
        Field("INR per 1 $currency",rate,{rate=it},true)
        Field("FX date (YYYY-MM-DD)",date,{date=it})
        Field("Manual source / assumption",note,{note=it},multi=true)
        Text("The latest eligible rate is used. Rates older than 7 days are flagged; missing rates leave INR totals unavailable. Native values and rate history remain visible.")
        state.book.portfolio.fx.sortedWith(compareByDescending<FxObservation> {it.date}.thenByDescending {it.revision}).take(12).forEach {
            Text(it.source?.datum?.provider?.label ?: "Manual FX")
            Text("${it.currency} · ${dateLabel(it.date)} · Revision ${it.revision}\n${if(hidden)"••••••" else it.inrPerUnit.toPlainString()} INR / ${it.currency}\n${if(hidden)"Source note hidden" else it.note}\nEntered ${recordedLabel(it.recordedAt)}")
        }
    }
}

@Composable fun ReferenceForm(state:BankUiState,model:BankViewModel,initialAccount:BankAccount?,done:()->Unit) {
    var account by rememberSaveable {mutableStateOf(initialAccount?.id?.toString() ?: "")}
    var amount by rememberSaveable {mutableStateOf("")};var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    val selected=state.book.accounts.find {it.id.toString()==account}
    Form("Broker statement reference","A total portfolio value for reconciliation. It is never added to net worth.",state,"Save reference",onSave={model.portfolio(done) {
        it.addReference(requireNotNull(selected) {"Choose a brokerage cash account."}.id,Amounts.parse(amount,selected.currency),LocalDate.parse(date))
    }}) {
        Choice("Brokerage cash account",account,listOf("" to "Choose account")+state.book.accounts.filter {it.kind==AccountKind.BROKER_CASH}.map {it.id.toString() to it.name}) {account=it}
        Field("Statement total in ${selected?.currency ?: CurrencyCode.INR}",amount,{amount=it},true)
        Field("Statement date (YYYY-MM-DD)",date,{date=it})
        Text("Comparison uses cash and all instruments assigned to this custody account as of the statement date. Missing component values make reconciliation unavailable.")
    }
}
