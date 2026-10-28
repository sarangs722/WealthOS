package com.wealthos.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wealthos.database.*
import com.wealthos.finance.*
import com.wealthos.publicdata.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BankUiState(val loading: Boolean = true, val book: BankBook = BankBook(), val demo: Boolean = false,
                       val storageError: Boolean = false, val saving: Boolean = false, val formError: String? = null,
                       val refreshing:PublicProvider?=null,val publicMessage:String?=null,val backup:BackupState=BackupState(),val openPublicScreen:Boolean=false)

class BankViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(BankUiState())
    val state = mutable.asStateFlow()
    private var realBook = BankBook()
    private var db: BankDatabase? = null
    private var planningRepository:PlanningRepository?=null
    private var repository: BankRepository? = null
    private var portfolioRepository: PortfolioRepository? = null
    private var householdRepository:HouseholdRepository? = null
    private var publicRepository:PublicDataRepository?=null
    private val publicAttempts=mutableMapOf<PublicProvider,Instant>()
    private var backupRepository:RoomBackupRepository?=null
    private var restorePlan:PreparedRestore?=null
    private var exportBytes:ByteArray?=null
    private var exportPickerClaim:String?=null
    private var backupForeground=false
    private var pendingNavFile:UUID?=null

    init {
        viewModelScope.launch {
            var opening:BankDatabase?=null
            try {
                val opened = withContext(Dispatchers.IO) {
                    BankDatabase.open(application, DatabaseKeyStore(application).loadOrCreate()).also {
                        opening=it
                        it.openHelper.writableDatabase // verify decryption/migration before showing a ready state
                    }
                }
                db = opened
                repository = RoomBankRepository(opened)
                portfolioRepository = RoomPortfolioRepository(opened)
                householdRepository=RoomHouseholdRepository(opened)
                planningRepository=RoomPlanningRepository(opened)
                publicRepository=RoomPublicRepository(opened)
                backupRepository=RoomBackupRepository(application,opened)
                mutable.update {it.copy(backup=it.backup.copy(safety=backupRepository!!.safetyBackups()))}
                repository!!.observe().collect { book ->
                    realBook = book
                    mutable.update { it.copy(loading = false, book = if (it.demo) demoBook(true) else book) }
                }
            } catch (error: kotlinx.coroutines.CancellationException) { opening?.close();throw error }
            catch (_: Exception) { opening?.close();db=null;mutable.update { it.copy(loading = false, storageError = true) } }
        }
    }
    fun demo(enabled: Boolean) = mutable.update { it.copy(demo = enabled, book = if (enabled) demoBook(true) else realBook, formError = null) }
    fun clearFormError() = mutable.update { it.copy(formError = null) }

    fun add(name: String, bank: String, currency: CurrencyCode, kind: AccountKind, amount: String, date: String, done: () -> Unit) = save(done) {
        it.addAccount(name, bank, currency, kind, Amounts.parse(amount, currency), LocalDate.parse(date))
    }
    fun edit(id: UUID, name: String, bank: String, kind: AccountKind, done: () -> Unit) = save(done) { it.editAccount(id, name, bank, kind) }
    fun balance(id: UUID, currency: CurrencyCode, amount: String, date: String, note: String, done: () -> Unit) = save(done) {
        it.recordBalance(id, Amounts.parse(amount, currency), LocalDate.parse(date), note)
    }
    fun portfolio(done: () -> Unit, action: suspend (PortfolioRepository) -> Unit) = save(done) {
        action(requireNotNull(portfolioRepository))
    }
    fun household(done:()->Unit,action:suspend (HouseholdRepository)->Unit)=save(done) {action(requireNotNull(householdRepository))}
    fun planning(done:()->Unit,action:suspend (PlanningRepository)->Unit)=save(done) {action(requireNotNull(planningRepository))}
    fun publicChange(done:()->Unit={},action:suspend (PublicDataRepository)->Unit)=save(done) {action(requireNotNull(publicRepository))}
    fun refreshPublic(provider:PublicProvider) {
        val s=mutable.value
        if(s.demo || s.storageError || s.loading || s.saving || restorePlan!=null || s.refreshing!=null || !provider.network || !s.book.publicData.networkEnabled)return
        val attempted=Instant.now()
        if(publicAttempts[provider]?.let {it>attempted.minusSeconds(60)}==true || s.book.publicData.runs.any {it.provider==provider && it.attemptedAt>attempted.minusSeconds(60)}){
            mutable.update {it.copy(publicMessage="Please wait one minute between requests to this provider.")};return
        }
        publicAttempts[provider]=attempted
        mutable.update {it.copy(refreshing=provider,publicMessage=null)}
        viewModelScope.launch {
            try {
                val batch=withContext(Dispatchers.IO){PublicFeeds(HttpsPublicTransport { !mutable.value.demo && mutable.value.book.publicData.networkEnabled }).fetch(provider)}
                if(!mutable.value.demo && mutable.value.book.publicData.networkEnabled){
                    requireNotNull(publicRepository).cache(provider,batch.rows,attempted,batch.retrievedAt,batch.message)
                    mutable.update {it.copy(publicMessage=batch.message)}
                }
            }catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){
                val message=when(e){is PublicFetchException->e.message ?: "Public data unavailable.";is java.io.IOException->"Connection unavailable; cached references retained.";else->"Provider format or validation failed; cached references retained."}
                if(!mutable.value.demo)try {publicRepository?.recordFailure(provider,attempted,message)}catch(_:Exception){/* Existing cache and financial records remain intact. */}
                mutable.update {it.copy(publicMessage=message)}
            }finally {mutable.update {it.copy(refreshing=null)}}
        }
    }
    fun importNav(uri:android.net.Uri,bindingId:UUID)=publicChange {
        val binding=requireNotNull(realBook.publicData.currentBindings().find {b->b.id==bindingId}) {"The selected fund binding changed. Choose the file again."}
        val at=Instant.now()
        val row=withContext(Dispatchers.IO){
            val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)).use {input->
                java.io.InputStreamReader(input,decoder).use {reader->AmfiNavParser.parse(reader,binding.schemeCode,binding.isin,at)}
            }
        }
        it.cache(PublicProvider.AMFI_FILE,listOf(row),at,Instant.now(),"Selected fund quote imported for review. No financial valuation was changed.")
    }
    fun prepareNavPicker(bindingId:UUID):Boolean {
        val s=mutable.value
        if(s.loading || s.saving || s.demo || s.storageError || restorePlan!=null || s.book.publicData.currentBindings().none {it.id==bindingId})return false
        pendingNavFile=bindingId;return true
    }
    fun navFileReturned(uri:android.net.Uri?) {
        val binding=pendingNavFile;pendingNavFile=null
        mutable.update {it.copy(openPublicScreen=true,publicMessage=when{uri==null->"File selection cancelled.";binding==null->"File selection expired after the app restarted. Choose the fund file again.";else->null})}
        if(uri!=null && binding!=null)importNav(uri,binding)
    }
    fun consumePublicNavigation(){mutable.update {it.copy(openPublicScreen=false)}}
    private fun save(done: () -> Unit, action: suspend (BankRepository) -> Unit) {
        if (mutable.value.saving || mutable.value.demo || mutable.value.storageError || mutable.value.loading || restorePlan!=null) return
        mutable.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            try {
                action(requireNotNull(repository))
                mutable.update { it.copy(saving = false) }
                done()
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: java.time.format.DateTimeParseException) { failure("Use a valid date (YYYY-MM-DD) or month (YYYY-MM).") }
            catch (error: IllegalArgumentException) { failure(error.message ?: "Check the entered values.") }
            catch (_: Exception) { failure("Could not save. Your existing records are preserved. Please retry.") }
        }
    }
    private fun failure(message: String) = mutable.update { it.copy(saving = false, formError = message) }
    fun prepareExport(password:CharArray)=backupTask(password) {
        val bytes=withContext(Dispatchers.IO){requireNotNull(backupRepository).export(password)}
        exportBytes?.fill(0);exportBytes=bytes
        mutable.update {it.copy(backup=it.backup.copy(exportId=UUID.randomUUID().toString(),message=null))}
    }
    fun prepareSafetyExport(id:String)=backupTask {
        val bytes=withContext(Dispatchers.IO){requireNotNull(backupRepository).readSafety(id)}
        exportBytes?.fill(0);exportBytes=bytes
        mutable.update {it.copy(backup=it.backup.copy(exportId=UUID.randomUUID().toString(),message=null))}
    }
    fun claimExportPicker(id:String):Boolean {
        if(id!=mutable.value.backup.exportId || exportPickerClaim==id)return false
        exportPickerClaim=id;return true
    }
    fun selectRestoreFile(uri:android.net.Uri?) {
        mutable.update {it.copy(formError=null,backup=it.backup.copy(source=uri,openScreen=true,message=if(uri==null)"File selection cancelled." else null))}
    }
    fun consumeBackupNavigation(){mutable.update {it.copy(backup=it.backup.copy(openScreen=false))}}
    fun writeExport(uri:android.net.Uri?) {
        if(exportBytes==null){
            mutable.update {it.copy(formError="Backup export expired after the app restarted. Create it again; no backup was saved.",backup=it.backup.copy(openScreen=true,exportId=null))}
            return
        }
        backupTask {
        val bytes=requireNotNull(exportBytes){"Backup export expired. Create it again."}
        try {
            if(uri==null){mutable.update {it.copy(backup=it.backup.copy(message="Export cancelled; your records were not changed."))};return@backupTask}
            withContext(Dispatchers.IO){
                val resolver=getApplication<Application>().contentResolver
                requireNotNull(resolver.openOutputStream(uri,"wt")).use {it.write(bytes);it.flush()}
                val verified=requireNotNull(resolver.openInputStream(uri)).use {com.wealthos.security.BackupEnvelope.read(it)}
                try{require(java.security.MessageDigest.isEqual(bytes,verified)){"Backup could not be verified. Choose a different destination."}}finally{verified.fill(0)}
            }
            mutable.update {it.copy(backup=it.backup.copy(message="Encrypted backup saved and read back successfully. Keep its password separately; test restoration before relying on the file."))}
        }finally{exportBytes?.fill(0);exportBytes=null;mutable.update {it.copy(backup=it.backup.copy(exportId=null,openScreen=true))}}
        }
    }
    fun prepareRestore(uri:android.net.Uri,password:CharArray)=backupTask(password) {
        var candidate:PreparedRestore?=null
        try {
            val plan=withContext(Dispatchers.IO){
                val file=requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)).use {com.wealthos.security.BackupEnvelope.read(it)}
                try{requireNotNull(backupRepository).prepare(file,password).also {candidate=it}}finally{file.fill(0)}
            }
            require(backupForeground){"Prepare this restore again after returning to the app."}
            restorePlan?.close();restorePlan=plan;candidate=null
            mutable.update {it.copy(backup=it.backup.copy(preview=plan.info,currentRecords=plan.currentRecords,message=null))}
        }finally{candidate?.close()}
    }
    fun confirmRestore()=backupTask {
        val plan=requireNotNull(restorePlan){"Prepare this restore again."}
        try {
            withContext(Dispatchers.IO){requireNotNull(backupRepository).restore(plan)}
            mutable.update {it.copy(backup=it.backup.copy(message="Financial backup restored. Networking is off. An encrypted pre-restore safety copy was retained on this device; export it to keep it after uninstalling."))}
        }finally{plan.close();restorePlan=null;mutable.update {it.copy(backup=it.backup.copy(preview=null,safety=backupRepository!!.safetyBackups()))}}
    }
    fun discardRestore(){if(mutable.value.saving)return;restorePlan?.close();restorePlan=null;mutable.update {it.copy(backup=it.backup.copy(preview=null))}}
    fun backupsForeground(active:Boolean){backupForeground=active;if(!active)discardRestore()}
    fun leaveBackups(){
        if(mutable.value.saving)return
        discardRestore()
        // A system document picker can outlive this screen when the device access lock activates.
        // Retain only ciphertext until its result/cancellation arrives or the ViewModel is cleared.
    }
    private fun backupTask(password:CharArray?=null,action:suspend ()->Unit){
        val s=mutable.value
        if(s.saving || s.loading || s.storageError || s.demo || s.refreshing!=null){password?.fill('\u0000');return}
        mutable.update {it.copy(saving=true,formError=null)}
        viewModelScope.launch {
            try{action()}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){
                val message=e.message ?: ""
                val safe=listOf("Use a password","Incorrect password","Your vault changed","This backup","Backup exceeds","Backup export expired","Backup could not be verified","Prepare this restore again")
                mutable.update {it.copy(formError=if(safe.any(message::startsWith))message.take(300) else "Backup operation failed or the file is invalid. Existing financial records were preserved; any incomplete export must not be used.")}
            }finally{password?.fill('\u0000');mutable.update {it.copy(saving=false)}}
        }
    }
    override fun onCleared() {restorePlan?.close();exportBytes?.fill(0);db?.close()}
}

/** Read-only, memory-only fiction; never inserted in the user's database. */
internal fun demoBook(includePortfolio:Boolean=false): BankBook {
    val now = Instant.parse("2026-01-01T00:00:00Z")
    val today = LocalDate.now()
    val a = BankAccount(UUID(0,1), "Everyday savings", "Fictional Bank", CurrencyCode.INR, AccountKind.SAVINGS, now)
    val b = BankAccount(UUID(0,2), "Reserve account", "Example Bank", CurrencyCode.INR, AccountKind.CURRENT, now)
    val observations = listOf(
        Triple(a, "84500.00", 60L), Triple(b, "230000.00",60L),
        Triple(a, "92640.50",30L), Triple(b,"250000.00",30L),
        Triple(a, "112840.50",0L), Triple(b,"265000.00",0L)
    ).mapIndexed { index, (account, value, days) ->
        BalanceObservation(UUID(1,index.toLong()), account.id, value.toBigDecimal(), CurrencyCode.INR,
            today.minusDays(days), now, 1, "Fictional demo balance")
    }
    val portfolio=if(includePortfolio) {
        val fund=Instrument(UUID(5,1),"Fictional index fund",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.TRANSACTIONS,today.minusDays(60),now,true)
        val deposit=Instrument(UUID(5,2),"Example fixed deposit",AssetCategory.FD,CurrencyCode.INR,TrackingMode.DEPOSIT,today,now)
        PortfolioBook(listOf(fund,deposit),listOf(InvestmentEntry(UUID(6,1),fund.id,TradeKind.OPENING,today.minusDays(60),1,"100".toBigDecimal(),"5000".toBigDecimal(),recordedAt=now)),
            listOf(AssetValuation(UUID(7,1),fund.id,ValuationKind.UNIT_PRICE,"80".toBigDecimal(),today,1,now,"Fictional demo price")),
            listOf(FinancialContract(deposit.id,"100000".toBigDecimal(),"0.08".toBigDecimal(),today,today.plusYears(1))))
    } else PortfolioBook()
    val household=if(includePortfolio) {
        val source=IncomeSource(UUID(10,1),"Fictional salary",IncomeKind.SALARY,CurrencyCode.INR,today.withDayOfMonth(1),null,now,now)
        val health=InsurancePolicy(UUID(11,1),"Example family health","Fictional insurer",PolicyKind.HEALTH,CurrencyCode.INR,now,now)
        val term=InsurancePolicy(UUID(11,2),"Example term cover","Fictional insurer",PolicyKind.TERM,CurrencyCode.INR,now,now)
        HouseholdBook(listOf(source),listOf(IncomeRate(UUID(12,1),source.id,source.startedOn,"75000".toBigDecimal(),1,now,"Fictional planning amount")),
            listOf(IncomeRecord(UUID(13,1),source.id,java.time.YearMonth.from(today),"75000".toBigDecimal(),"90000".toBigDecimal(),"15000".toBigDecimal(),1,now,"Fictional salary receipt")),
            listOf(MonthlyCashFlow(UUID(14,1),java.time.YearMonth.from(today),CurrencyCode.INR,IncomeMode.RECORDED_SOURCES,null,"25000".toBigDecimal(),"5000".toBigDecimal(),"1000".toBigDecimal(),java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,"4000".toBigDecimal(),"15000".toBigDecimal(),true,true,1,now,"Fictional month-to-date summary")),
            listOf(EmergencyTarget(UUID(15,1),today,"150000".toBigDecimal(),1,now,"Fictional target")),
            listOf(EmergencyAllocation(UUID(16,1),b.id,null,today,"50000".toBigDecimal(),1,now,"Earmark of existing reserve cash, not a new asset")),
            listOf(health,term),listOf(
                InsuranceTerms(UUID(17,1),health.id,today,today.minusYears(1).plusDays(10),today.plusDays(10),"1000000".toBigDecimal(),"24000".toBigDecimal(),PremiumFrequency.YEARLY,today.plusDays(10),CoverageScope.FAMILY_FLOATER,true,1,now,"Fictional policy; renewal requires entered terms"),
                InsuranceTerms(UUID(17,2),term.id,today,today.minusYears(5),today.plusYears(25),"5000000".toBigDecimal(),"1000".toBigDecimal(),PremiumFrequency.MONTHLY,today.plusDays(25),CoverageScope.INDIVIDUAL,true,1,now,"Fictional term policy")))
    } else HouseholdBook()
    val planning=if(includePortfolio){
        val scenarios=ScenarioKind.entries.mapIndexed {index,kind->ForecastScenario(UUID(20,index.toLong()),UUID(21,index.toLong()),"Fictional ${kind.label.lowercase()} scenario",kind,ForecastMode.CONTRIBUTION,today,120,"0.05".toBigDecimal(),"0.03".toBigDecimal(),listOf("0.02","0.06","0.10")[index].toBigDecimal(),"10000".toBigDecimal(),"0.05".toBigDecimal(),java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,"0.05".toBigDecimal(),java.math.BigDecimal.ZERO,true,false,1,now,"Fictional assumptions, not recommended returns")}
        PlanningBook(scenarios,scenarios.flatMap {s->portfolio.instruments.map {AssetAssumption(s.id,it.id,s.investmentReturn,java.math.BigDecimal.ZERO)}},goals=listOf(FinancialGoal(UUID(22,1),UUID(23,1),"Fictional education goal",CurrencyCode.INR,"200000".toBigDecimal(),today.plusYears(5),true,"50000".toBigDecimal(),"0.06".toBigDecimal(),"0.05".toBigDecimal(),1,now,"Fictional earmark of existing wealth")))
    }else PlanningBook()
    val reference=PublicDatum(PublicProvider.MANUAL,PublicKind.CPI_YOY,"DEMO:CPI","Fictional demo inflation reference","5.00".toBigDecimal(),null,"percent year-on-year",today.minusDays(27),"https://www.mospi.gov.in/",PublicValidation.MANUAL,"Fictional illustration only","Fictional reference, not actual published economic data.","DEMO · fictional")
    val public=if(includePortfolio)PublicBook(observations=listOf(PublicObservation(UUID(24,1),reference,Instant.now(),1,"demo")))else PublicBook()
    return BankBook(listOf(a,b),observations,portfolio,household,planning,public)
}
