package com.wealthos.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wealthos.finance.*
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Composable fun WealthApp(hidden: Boolean, appLock: Boolean, privacyMessage: String?, onHide: () -> Unit, onLock: () -> Unit,
                         createBackupFile:(String)->Unit,chooseBackupFile:()->Unit,
                         chooseNavFile:(UUID)->Unit,
                         model: BankViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by rememberSaveable {mutableStateOf(false)}
    var newCategory by rememberSaveable {mutableStateOf(AssetCategory.MUTUAL_FUND.name)}
    var route by rememberSaveable { mutableStateOf("main") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAsset by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSource by rememberSaveable {mutableStateOf<String?>(null)}
    var selectedPolicy by rememberSaveable {mutableStateOf<String?>(null)}
    var selectedScenario by rememberSaveable {mutableStateOf<String?>(null)}
    var selectedGoal by rememberSaveable {mutableStateOf<String?>(null)}
    var selectedCalculator by rememberSaveable {mutableStateOf(CalculatorKind.INFLATION.name)}
    var selectedMonth by rememberSaveable {mutableStateOf(YearMonth.now().toString())}
    var summaryCurrency by rememberSaveable {mutableStateOf(CurrencyCode.INR.name)}
    val account = state.book.accounts.find { it.id.toString() == selected }
    val asset = state.book.portfolio.instruments.find {it.id.toString()==selectedAsset}
    val source=state.book.household.sources.find {it.id.toString()==selectedSource}
    val policy=state.book.household.policies.find {it.id.toString()==selectedPolicy}
    val scenario=state.book.planning.latestScenarios().find {it.key.toString()==selectedScenario}
    val goal=state.book.planning.latestGoals().find {it.key.toString()==selectedGoal}
    fun navigate(next: String) { model.clearFormError(); route = next }
    LaunchedEffect(state.backup.openScreen){if(state.backup.openScreen){route="backups";model.consumeBackupNavigation()}}
    LaunchedEffect(state.openPublicScreen){if(state.openPublicScreen){route="public-data";model.consumePublicNavigation()}}
    fun parent()=when(route) {
        "balance","edit"->"detail"
        "unit-tracking","investment-entry","asset-valuation","contract-event","edit-asset"->"asset-detail"
        "cashflow-edit"->"cashflow"
        "income-sources"->"cashflow"
        "add-income-source","income-source-detail"->"income-sources"
        "edit-income-source","income-rate","income-record"->"income-source-detail"
        "edit-policy","premium-settlement"->"policy-detail"
        "scenario-edit"->if(scenario==null)"main" else "scenario-detail"
        "goal-edit"->"goals"
        "nav-binding","economic-reference"->"public-data"
        "backups"->"settings"
        else->"main"
    }
    BackHandler(enabled = route != "main") { if(!state.saving) route=parent() }
    val sectionTitle=when {
        route=="main"->listOf("WealthOS","Portfolio","Forecast","Tools","Protect")[tab]
        route=="settings"->"Settings"
        route=="backups"->"Backup & restore"
        route in listOf("public-data","nav-binding","economic-reference")->"Market data"
        route in listOf("cashflow","cashflow-edit")->"Cash flow"
        route.startsWith("income") || route in listOf("add-income-source","edit-income-source")->"Income"
        route.startsWith("scenario") || route in listOf("goals","goal-edit","analytics")->"Planning"
        route in listOf("calculator")->"Tools"
        route in listOf("emergency-target","emergency-allocation","add-policy","edit-policy","policy-detail","premium-settlement")->"Protect"
        else->"Portfolio"
    }
    WealthFrame(tab,sectionTitle,route=="main",state.demo,state.saving,
        onTab={tab=it},onBack={navigate(parent())},onSettings={navigate("settings")},
        onExitDemo={model.demo(false);navigate("main")},onAdd={showAdd=true},
        addVisible=!state.loading && !state.storageError && (state.book.accounts.isNotEmpty() || state.book.portfolio.instruments.isNotEmpty())) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.storageError -> EmptyPanel("Your vault could not be opened", "WealthOS preserved your existing files. Close and reopen the app. If this continues, keep app data intact; do not reinstall or clear storage. No replacement database was created.")
                route == "settings" -> SettingsScreen(hidden, appLock, privacyMessage, onHide, onLock,
                    onDemo = { model.demo(!state.demo); navigate("main") }, demo = state.demo,onPublic={navigate("public-data")},onBackups={navigate("backups")})
                route == "backups" -> BackupScreen(state,model,createBackupFile,chooseBackupFile)
                route == "public-data" -> PublicDataScreen(state,hidden,model,{selectedAsset=it.toString();navigate("nav-binding")},{navigate("economic-reference")},chooseNavFile)
                route == "nav-binding" && asset!=null -> NavBindingForm(asset,state,model){navigate("public-data")}
                route == "economic-reference" -> EconomicReferenceForm(state,model){navigate("public-data")}
                route == "scenario-edit" -> ScenarioForm(state,model,scenario) {selectedScenario=it.toString();navigate("scenario-detail")}
                route == "scenario-detail" && scenario!=null -> ScenarioDetails(scenario,state,hidden) {navigate("scenario-edit")}
                route == "goals" -> GoalsScreen(state,hidden,{selectedGoal=null;navigate("goal-edit")}) {selectedGoal=it.toString();navigate("goal-edit")}
                route == "goal-edit" -> GoalForm(state,model,goal) {navigate("goals")}
                route == "analytics" -> AnalyticsScreen(state,hidden)
                route == "calculator" -> CalculatorScreen(CalculatorKind.valueOf(selectedCalculator),hidden)
                route == "add-asset" -> InstrumentForm(state,model,initialCategory=AssetCategory.valueOf(newCategory)) {selectedAsset=it.toString();navigate("asset-detail")}
                route == "edit-asset" && asset!=null -> InstrumentForm(state,model,asset) {navigate("asset-detail")}
                route == "asset-detail" && asset!=null -> InstrumentDetails(asset,state,hidden) {navigate(it)}
                route == "investment-entry" && asset!=null -> InvestmentEntryForm(asset,state,model) {navigate("asset-detail")}
                route == "unit-tracking" && asset!=null -> UnitTrackingForm(asset,state,model) {navigate("asset-detail")}
                route == "asset-valuation" && asset!=null -> ValuationForm(asset,state,model) {navigate("asset-detail")}
                route == "contract-event" && asset!=null -> ContractEventForm(asset,state,model) {navigate("asset-detail")}
                route == "transfer" -> TransferForm(state,model) {navigate("main")}
                route == "fx" -> FxForm(state,model,hidden) {navigate("main")}
                route == "reference" -> ReferenceForm(state,model,account) {navigate("main")}
                route == "cashflow" -> CashFlowScreen(state,hidden,onEdit={month,currency->selectedMonth=month.toString();summaryCurrency=currency.name;navigate("cashflow-edit")},onIncome={navigate("income-sources")})
                route == "cashflow-edit" -> CashFlowForm(YearMonth.parse(selectedMonth),CurrencyCode.valueOf(summaryCurrency),state,model) {navigate("cashflow")}
                route == "income-sources" -> IncomeSourcesScreen(state,hidden,{navigate("add-income-source")}) {selectedSource=it.toString();navigate("income-source-detail")}
                route == "add-income-source" -> IncomeSourceForm(state,model,null) {selectedSource=it.toString();navigate("income-source-detail")}
                route == "edit-income-source" && source!=null -> IncomeSourceForm(state,model,source) {navigate("income-source-detail")}
                route == "income-source-detail" && source!=null -> IncomeSourceDetails(source,state,hidden) {navigate(it)}
                route == "income-rate" && source!=null -> IncomeRateForm(source,state,model) {navigate("income-source-detail")}
                route == "income-record" && source!=null -> IncomeRecordForm(source,state,model) {navigate("income-source-detail")}
                route == "emergency-target" -> EmergencyTargetForm(state,model) {navigate("main")}
                route == "emergency-allocation" -> EmergencyAllocationForm(state,model,hidden) {navigate("main")}
                route == "add-policy" -> PolicyForm(state,model,null) {selectedPolicy=it.toString();navigate("policy-detail")}
                route == "edit-policy" && policy!=null -> PolicyForm(state,model,policy) {navigate("policy-detail")}
                route == "policy-detail" && policy!=null -> PolicyDetails(policy,state,hidden,{navigate("edit-policy")},{navigate("premium-settlement")})
                route == "premium-settlement" && policy!=null -> PremiumSettlementForm(policy,state,model) {navigate("policy-detail")}
                route == "add" || route == "edit" || route == "balance" -> AccountEditor(route, account, state, model) {
                    if(route=="add"){tab=1;navigate("main")}else navigate("detail")
                }
                route == "detail" && account != null -> AccountDetails(account, state.book, hidden, state.demo,
                    onEdit = { navigate("edit") }, onBalance = { navigate("balance") },onReference={navigate("reference")})
                else -> when (tab) {
                    0 -> HomeScreen(state.book, hidden, state.demo, onAdd = { showAdd=true },
                        onDemo = { model.demo(true) }, onAccount = { selected = it.toString(); navigate("detail") }, onPrivacy = onHide,
                        onCashFlow={navigate("cashflow")},onIncome={navigate("income-sources")},onProtect={tab=4},onAnalytics={navigate("analytics")},onPublic={navigate("public-data")},onPortfolio={tab=1})
                    1 -> PortfolioScreen(state.book, hidden, state.demo, onAdd = { showAdd=true },
                        onAccount = { selected = it.toString(); navigate("detail") },onAddAsset={showAdd=true},
                        onAsset={selectedAsset=it.toString();navigate("asset-detail")},onTransfer={navigate("transfer")},onFx={navigate("fx")})
                    2 -> ForecastScreen(state,hidden,{selectedScenario=null;navigate("scenario-edit")},{selectedScenario=it.toString();navigate("scenario-detail")},{navigate("goals")},{navigate("analytics")})
                    3 -> ToolsScreen {selectedCalculator=it.name;navigate("calculator")}
                    4 -> ProtectScreen(state,hidden,{navigate("emergency-target")},{navigate("emergency-allocation")},{navigate("add-policy")}) {selectedPolicy=it.toString();navigate("policy-detail")}
                }
            }
    }
    if(showAdd)AddRecordSheet(onDismiss={showAdd=false},onBank={showAdd=false;navigate("add")},
        onAsset={newCategory=it.name;showAdd=false;navigate("add-asset")},
        onExisting={showAdd=false;tab=1;navigate("main")})
}

@Composable fun EmptyPanel(title: String, body: String, eyebrow: String = "LOCAL VAULT") {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text(eyebrow, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun LockScreen(message: String?, unlock: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(28.dp), verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text("Your wealth.\nYour private space.", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Text("Unlock with your device security to open WealthOS.")
            if (message != null) { Spacer(Modifier.height(12.dp)); Text(message, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
            Button(onClick = unlock) { Text("Unlock WealthOS") }
        }
    }
}

@Composable internal fun SettingsScreen(hidden: Boolean, locked: Boolean, message: String?, onHide: () -> Unit, onLock: () -> Unit,
                                      onDemo: () -> Unit, demo: Boolean,onPublic:()->Unit,onBackups:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("Privacy",style=MaterialTheme.typography.titleLarge)
        SettingToggle("Obscure amounts","Hide balances and financial notes",hidden,onHide)
        SettingToggle("Device access lock","Use your existing device security",locked,onLock)
        message?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        SectionHeading("Your data")
        NavigationRow("Manage backups","Export or restore an encrypted backup",Icons.Default.Lock,onBackups)
        NavigationRow("Public data & sources","Optional prices, rates and inflation",WealthIcons.forecast,onPublic)
        SectionHeading("Explore")
        NavigationRow(if(demo)"Exit demo mode" else "Explore demo mode","Fictional records; your vault stays separate",WealthIcons.wallet,onDemo)
        DetailsDisclosure("Privacy details"){
            Text("Financial records stay on this device. No ads, account or tracking.")
            Text("The database is encrypted and its key is protected by Android Keystore. Screenshots and system backups are disabled.")
            Text("Access lock protects the interface. The database key is device-bound and is not authenticated for each use.")
            Text("Keep an encrypted portable backup and its password before uninstalling or changing devices.")
        }
        val context=LocalContext.current
        @Suppress("DEPRECATION")
        val version=remember(context){context.packageManager.getPackageInfo(context.packageName,0).versionName.orEmpty()}
        Text("WealthOS $version",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun SettingToggle(title: String, body: String, checked: Boolean, action: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(body, style = MaterialTheme.typography.bodyMedium) }
        Switch(checked, onCheckedChange = { action() }, modifier = Modifier.semanticsLabel(title))
    }
}

internal fun money(value: java.math.BigDecimal?, currency: CurrencyCode = CurrencyCode.INR, hidden: Boolean = false): String =
    if (hidden) "••••••" else value?.let { Amounts.format(it,currency) } ?: "Unavailable"
