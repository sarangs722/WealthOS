package com.wealthos.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wealthos.finance.*
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortfolioRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name="portfolio-test-${UUID.randomUUID()}.db"
    private val key=ByteArray(32).also {java.security.SecureRandom().nextBytes(it)}
    private lateinit var db:BankDatabase;private lateinit var bank:RoomBankRepository;private lateinit var portfolio:RoomPortfolioRepository
    private val start=LocalDate.of(2026,1,1);private val today=LocalDate.of(2026,1,31)
    private val clock=Clock.fixed(Instant.parse("2026-01-31T12:00:00Z"),ZoneOffset.UTC)
    @Before fun open() {db=BankDatabase.open(context,key,name);bank=RoomBankRepository(db,clock);portfolio=RoomPortfolioRepository(db,clock)}
    @After fun close() {db.close();context.deleteDatabase(name);key.fill(0)}
    private fun number(expected:String,actual:BigDecimal?)=assertEquals(0,expected.toBigDecimal().compareTo(actual!!))
    private fun instrument(category:AssetCategory=AssetCategory.STOCK,currency:CurrencyCode=CurrencyCode.INR):Instrument {
        val mode=when(category) {AssetCategory.LOAN->TrackingMode.LOAN;AssetCategory.FD,AssetCategory.RD->TrackingMode.DEPOSIT;AssetCategory.PROPERTY,AssetCategory.EPF,AssetCategory.PPF,AssetCategory.NPS->TrackingMode.OBSERVED;else->TrackingMode.TRANSACTIONS}
        return Instrument(UUID.randomUUID(),"Private asset",category,currency,mode,start,clock.instant())
    }
    private fun entry(i:Instrument,kind:TradeKind=TradeKind.BUY,units:String="10",gross:String="1000",date:LocalDate=start.plusDays(1))=
        InvestmentEntry(UUID.randomUUID(),i.id,kind,date,0,units.toBigDecimal(),gross.toBigDecimal(),recordedAt=clock.instant())
    private suspend fun cash(amount:String="5000",kind:AccountKind=AccountKind.SAVINGS)=bank.addAccount("Cash fixture","Bank",CurrencyCode.INR,kind,amount.toBigDecimal(),start)

    @Test fun purchaseAndLaterBankSnapshotNeverDoubleDeductCash()=runTest {
        val account=cash();val i=instrument()
        portfolio.addInstrument(NewInstrument(i,initialValue="100".toBigDecimal()))
        portfolio.addEntry(entry(i),CashLink(account,true))
        var book=bank.observe().first();number("5000",WealthValuation.at(book,today).netWorthInr)
        assertTrue(BankValuation.at(book,today).inputs.single().provisional)
        bank.recordBalance(account,"4000".toBigDecimal(),start.plusDays(1),"")
        book=bank.observe().first();number("5000",WealthValuation.at(book,today).netWorthInr)
        assertFalse(BankValuation.at(book,today).inputs.single().provisional)
        db.close();db=BankDatabase.open(context,key,name);bank=RoomBankRepository(db,clock)
        val restored=bank.observe().first();number("10",restored.portfolio.entries.single().units);assertEquals(1,restored.portfolio.cashMovements.size)
    }
    @Test fun failedCashLegRollsBackTradeAndAuditTogether()=runTest {
        val account=cash();val i=instrument();portfolio.addInstrument(NewInstrument(i))
        val audits=db.bankDao().audits().size
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_cash BEFORE INSERT ON cash_movements BEGIN SELECT RAISE(ABORT,'injected cash failure'); END")
        try {portfolio.addEntry(entry(i),CashLink(account,true));fail("Expected rollback")}catch(_:android.database.SQLException){}
        val book=bank.observe().first();assertTrue(book.portfolio.entries.isEmpty());assertTrue(book.portfolio.cashMovements.isEmpty());assertEquals(audits,db.bankDao().audits().size)
    }
    @Test fun atomicTransferAndDateTreatmentValidation()=runTest {
        val a=cash("1000");val b=cash("500")
        portfolio.transfer(CashLink(a,true),CashLink(b,true),"200".toBigDecimal(),start.plusDays(1),"Move own funds")
        val book=bank.observe().first();number("1500",WealthValuation.at(book,today).netWorthInr);assertEquals(2,book.portfolio.cashMovements.size)
        try {portfolio.transfer(CashLink(a,false),CashLink(b,true),"10".toBigDecimal(),start.plusDays(2),"Invalid old snapshot");fail()}catch(_:IllegalArgumentException){}
        assertEquals(2,bank.observe().first().portfolio.cashMovements.size)
    }
    @Test fun loanPrincipalAndInterestAreSeparateWithLinkedCash()=runTest {
        val account=cash("2000");val i=instrument(AssetCategory.LOAN)
        val c=FinancialContract(i.id,"1000".toBigDecimal(),"0.12".toBigDecimal(),start,start.plusYears(1),termMonths=12)
        portfolio.addInstrument(NewInstrument(i,contract=c))
        portfolio.addContractEvent(ContractEvent(UUID.randomUUID(),i.id,ContractEventKind.REPAYMENT,start.plusDays(10),0,"100".toBigDecimal(),"10".toBigDecimal(),recordedAt=clock.instant()),CashLink(account,true))
        val v=WealthValuation.at(bank.observe().first(),today);number("900",v.liabilitiesInr);number("990",v.netWorthInr)
    }
    @Test fun rdClosureAndBackdatedEventsValidateEntireContractHistory()=runTest {
        val i=instrument(AssetCategory.RD);val c=FinancialContract(i.id,"1000".toBigDecimal(),"0.08".toBigDecimal(),start,start.plusYears(1))
        portfolio.addInstrument(NewInstrument(i,contract=c))
        val payout=ContractEvent(UUID.randomUUID(),i.id,ContractEventKind.MATURITY,today,0,"1000".toBigDecimal(),"5".toBigDecimal(),recordedAt=clock.instant())
        portfolio.addContractEvent(payout)
        try {portfolio.addContractEvent(payout.copy(id=UUID.randomUUID()));fail()}catch(_:IllegalArgumentException){}
        try {portfolio.addContractEvent(payout.copy(id=UUID.randomUUID(),kind=ContractEventKind.CONTRIBUTION,date=start.plusDays(2),interest=BigDecimal.ZERO));fail()}catch(_:IllegalArgumentException){}
        assertEquals(1,bank.observe().first().portfolio.contractEvents.size)
        number("0",WealthValuation.at(bank.observe().first(),today).netWorthInr)
    }
    @Test fun depositClosureUsesNumericEqualityAcrossDecimalScales()=runTest {
        val i=instrument(AssetCategory.FD);val c=FinancialContract(i.id,"1000.0".toBigDecimal(),BigDecimal.ZERO,start,start.plusYears(1))
        portfolio.addInstrument(NewInstrument(i,contract=c))
        portfolio.addContractEvent(ContractEvent(UUID.randomUUID(),i.id,ContractEventKind.MATURITY,today,0,"1000.00".toBigDecimal(),recordedAt=clock.instant()))
        number("0",WealthValuation.at(bank.observe().first(),today).netWorthInr)
    }
    @Test fun sameDayDepositPaymentInvalidatesEarlierObservationUntilReconfirmed()=runTest {
        val i=instrument(AssetCategory.RD);val c=FinancialContract(i.id,"1000".toBigDecimal(),BigDecimal.ZERO,start,start.plusYears(1))
        portfolio.addInstrument(NewInstrument(i,initialValue="1000".toBigDecimal(),contract=c))
        portfolio.addContractEvent(ContractEvent(UUID.randomUUID(),i.id,ContractEventKind.CONTRIBUTION,start,0,"1000".toBigDecimal(),recordedAt=clock.instant()))
        assertNull(WealthValuation.at(bank.observe().first(),today).netWorthInr)
        portfolio.addValuation(AssetValuation(UUID.randomUUID(),i.id,ValuationKind.OBSERVED_TOTAL,"2000".toBigDecimal(),start,0,clock.instant(),"Statement includes both installments"))
        number("2000",WealthValuation.at(bank.observe().first(),today).netWorthInr)
    }
    @Test fun referenceIsNotAnAssetAndCustodyCurrencyIsEnforced()=runTest {
        val account=cash("100",AccountKind.BROKER_CASH);val i=instrument().copy(custodyAccountId=account)
        portfolio.addInstrument(NewInstrument(i,"9".toBigDecimal(),"900".toBigDecimal(),"100".toBigDecimal()))
        portfolio.addReference(account,"1000".toBigDecimal(),start)
        var book=bank.observe().first();number("1000",WealthValuation.at(book,today).netWorthInr);number("0",WealthValuation.reconcile(book,book.portfolio.references.single()))
        try {portfolio.addInstrument(NewInstrument(instrument(currency=CurrencyCode.USD).copy(custodyAccountId=account)));fail()}catch(_:IllegalArgumentException){}
        book=bank.observe().first();assertEquals(1,book.portfolio.instruments.size)
        try {bank.editAccount(account,"Oops","",AccountKind.SAVINGS);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun oversaleInvalidBackdateAndUnknownOpeningCostRemainSafe()=runTest {
        val i=instrument();portfolio.addInstrument(NewInstrument(i,"2".toBigDecimal(),initialValue="100".toBigDecimal()))
        portfolio.addEntry(entry(i,TradeKind.SELL,"1","150"))
        val book=bank.observe().first();assertNull(WealthValuation.asset(i,book.portfolio,today).holding!!.realizedGain)
        try {portfolio.addEntry(entry(i,TradeKind.SELL,"2","300"));fail()}catch(_:IllegalArgumentException){}
        assertEquals(2,bank.observe().first().portfolio.entries.size)
    }
    @Test fun manualFxAndAssetMetadataRoundTripWithoutChangingOpeningPosition()=runTest {
        val i=instrument(currency=CurrencyCode.USD);portfolio.addInstrument(NewInstrument(i,"10".toBigDecimal(),initialValue="100".toBigDecimal()))
        assertNull(WealthValuation.at(bank.observe().first(),today).netWorthInr)
        portfolio.addFx(CurrencyCode.USD,"80.125".toBigDecimal(),start,"Manual reference fixture")
        portfolio.editInstrument(i.id,"Renamed asset","shares",MarketRegion.INTERNATIONAL,true,null)
        val book=bank.observe().first();number("80125",WealthValuation.at(book,today).netWorthInr);assertEquals("Renamed asset",book.portfolio.instruments.single().name);assertEquals(1,book.portfolio.entries.size)
    }
    @Test fun populatedEncryptedSchemaTwoMigratesWithoutChangingBankRecords()=runTest {
        db.close()
        val raw=InstrumentationRegistry.getInstrumentation().context.assets.open("com.wealthos.database.BankDatabase/2.json").bufferedReader().use {it.readText()}
        val schema=JSONObject(raw).getJSONObject("database")
        System.loadLibrary("sqlcipher")
        val helper=SupportOpenHelperFactory(key).create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object:SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db:SupportSQLiteDatabase) {
                val entities=schema.getJSONArray("entities")
                for(index in 0 until entities.length()) {
                    val entity=entities.getJSONObject(index);val table=entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                    val indices=entity.optJSONArray("indices")
                    if(indices!=null)for(n in 0 until indices.length())db.execSQL(indices.getJSONObject(n).getString("createSql").replace("\${TABLE_NAME}",table))
                }
                val setup=schema.getJSONArray("setupQueries")
                for(index in 0 until setup.length())db.execSQL(setup.getString(index))
            }
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected upgrade")
        }).build())
        val accountId=UUID.randomUUID().toString();val observationId=UUID.randomUUID().toString();val auditId=UUID.randomUUID().toString()
        helper.writableDatabase.apply {
            execSQL("INSERT INTO bank_accounts VALUES (?, 'User bank', 'Existing institution', 'INR', 'SAVINGS', ?, ?)",arrayOf(accountId,clock.instant().toString(),clock.instant().toString()))
            execSQL("INSERT INTO balance_observations VALUES (?, ?, '12345678901234567890.12', 'INR', ?, ?, 1, 'Existing private note')",arrayOf(observationId,accountId,start.toString(),clock.instant().toString()))
            execSQL("INSERT INTO audit_events VALUES (?, ?, 'CREATE_ACCOUNT', ?, NULL, 'original audit')",arrayOf(auditId,accountId,clock.instant().toString()))
        }
        helper.close();val encryptedBefore=context.getDatabasePath(name).readBytes()
        assertFalse(String(encryptedBefore.take(16).toByteArray()).startsWith("SQLite format"))
        db=BankDatabase.open(context,key,name);bank=RoomBankRepository(db,clock)
        val book=bank.observe().first();assertEquals(accountId,book.accounts.single().id.toString());assertEquals(observationId,book.observations.single().id.toString())
        assertEquals("12345678901234567890.12",book.observations.single().amount.toPlainString());assertEquals("Existing private note",book.observations.single().note)
        assertEquals(auditId,db.bankDao().audits().single().id);assertTrue(book.portfolio.instruments.isEmpty());assertEquals(7,db.openHelper.readableDatabase.version)
    }
    private fun summary(i:Instrument,value:String="1234.56",date:LocalDate=start)=AssetValuation(UUID.randomUUID(),i.id,ValuationKind.OBSERVED_TOTAL,value.toBigDecimal(),date,0,clock.instant())
    @Test fun valueOnlyFundPersistsWithoutInventingUnitsCostOrCash()=runTest {
        val account=cash("5000");val i=instrument(AssetCategory.MUTUAL_FUND).copy(tracking=TrackingMode.OBSERVED)
        portfolio.addInstrument(NewInstrument(i,initialValue="1234.56".toBigDecimal(),snapshot=summary(i)))
        db.close();db=BankDatabase.open(context,key,name);bank=RoomBankRepository(db,clock)
        val book=bank.observe().first();number("6234.56",WealthValuation.at(book,today).netWorthInr)
        assertTrue(book.portfolio.entries.isEmpty());assertTrue(book.portfolio.cashMovements.isEmpty())
        assertNull(book.portfolio.valuations.single().reportedInvested)
        number("5000",BankValuation.at(book,today).inputs.single {it.account.id==account}.value)
    }
    @Test fun reportedMetadataIsLosslessAndBelongsToEachDatedRevision()=runTest {
        val i=instrument().copy(tracking=TrackingMode.OBSERVED)
        val first=summary(i).copy(reportedInvested="1000.00".toBigDecimal(),reportedUnits="10.000000000001".toBigDecimal(),reportedPrice="123.456789012345".toBigDecimal())
        portfolio.addInstrument(NewInstrument(i,initialValue=first.value,snapshot=first))
        portfolio.addValuation(summary(i,"1300.00",start.plusDays(1)))
        portfolio.addValuation(summary(i,"1301.00",start.plusDays(1)).copy(note="Fictional correction"))
        val p=bank.observe().first().portfolio
        assertEquals(first.reportedUnits!!.toPlainString(),p.valuations.single {it.date==start}.reportedUnits!!.toPlainString())
        val latest=WealthValuation.asset(i,p,today);number("1301.00",latest.nativeValue);assertNull(latest.snapshot!!.reportedInvested)
        assertEquals(listOf(1,2),p.valuations.filter {it.date==start.plusDays(1)}.map {it.revision}.sorted())
        assertNull(latest.unrealizedGain)
    }
    @Test fun startUnitTrackingPreservesIdentityHistoryAndUnknownCost()=runTest {
        val i=instrument().copy(tracking=TrackingMode.OBSERVED);portfolio.addInstrument(NewInstrument(i,initialValue="1234.56".toBigDecimal(),snapshot=summary(i).copy(reportedInvested="1000".toBigDecimal())))
        portfolio.startUnitTracking(i.id,start.plusDays(1),"10".toBigDecimal(),null,"130".toBigDecimal())
        val p=bank.observe().first().portfolio;val upgraded=p.instruments.single()
        assertEquals(i.id,upgraded.id);assertEquals(i.openedOn,upgraded.openedOn);assertEquals(TrackingMode.TRANSACTIONS,upgraded.tracking)
        number("1234.56",WealthValuation.asset(upgraded,p,start).nativeValue)
        val current=WealthValuation.asset(upgraded,p,today);number("1300",current.nativeValue);assertNull(current.holding!!.remainingCost);assertNull(current.unrealizedGain)
        assertTrue(p.cashMovements.isEmpty());assertEquals(1,p.entries.size);validateRestoredBook(db)
        try{portfolio.startUnitTracking(i.id,today,BigDecimal.TEN,null,null);fail()}catch(_:IllegalArgumentException){}
        try{portfolio.addEntry(entry(upgraded,date=start));fail()}catch(_:IllegalArgumentException){}
        assertEquals(1,bank.observe().first().portfolio.entries.size)
    }
    @Test fun sameDayUpgradeUsesSeparateQuoteRevisionAndNoDuplicateValue()=runTest {
        val i=instrument().copy(tracking=TrackingMode.OBSERVED);portfolio.addInstrument(NewInstrument(i,initialValue="1234.56".toBigDecimal()))
        portfolio.startUnitTracking(i.id,start,BigDecimal.TEN,"1000".toBigDecimal(),"130".toBigDecimal())
        val book=bank.observe().first();number("1300",WealthValuation.at(book,today).netWorthInr);assertEquals(2,book.portfolio.valuations.size);validateRestoredBook(db)
    }
    @Test fun failedUpgradeRollsBackTrackingEntryAndAudit()=runTest {
        val i=instrument().copy(tracking=TrackingMode.OBSERVED);portfolio.addInstrument(NewInstrument(i,initialValue="1234.56".toBigDecimal()))
        val audits=db.bankDao().audits().size
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_upgrade BEFORE INSERT ON investment_entries BEGIN SELECT RAISE(ABORT,'injected'); END")
        try{portfolio.startUnitTracking(i.id,today,BigDecimal.TEN,null,null);fail()}catch(_:android.database.SQLException){}
        val p=bank.observe().first().portfolio;assertEquals(TrackingMode.OBSERVED,p.instruments.single().tracking);assertTrue(p.entries.isEmpty());assertEquals(audits,db.bankDao().audits().size)
    }
    @Test fun badDerivedTotalAndBackdatedUpgradeLeaveVaultUnchanged()=runTest {
        val i=instrument().copy(tracking=TrackingMode.OBSERVED)
        try{portfolio.addInstrument(NewInstrument(i,initialValue="1234.56".toBigDecimal(),snapshot=summary(i).copy(reportedUnits=BigDecimal.TEN,reportedPrice=BigDecimal.TEN,calculatedTotal=true)));fail()}catch(_:IllegalArgumentException){}
        assertTrue(bank.observe().first().portfolio.instruments.isEmpty())
        portfolio.addInstrument(NewInstrument(i,initialValue="1234.56".toBigDecimal()))
        portfolio.addValuation(summary(i,"1400.00",today))
        try{portfolio.startUnitTracking(i.id,start,BigDecimal.TEN,null,null);fail()}catch(_:IllegalArgumentException){}
        assertEquals(TrackingMode.OBSERVED,bank.observe().first().portfolio.instruments.single().tracking)
    }
    @Test fun schemaSixMigrationPreservesEncryptedRowsAndMakesNewMetadataUnknown()=runTest {
        db.close()
        val schema=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.wealthos.database.BankDatabase/6.json").bufferedReader().use {it.readText()}).getJSONObject("database")
        context.deleteDatabase(name)
        val helper=SupportOpenHelperFactory(key).create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object:SupportSQLiteOpenHelper.Callback(6){
            override fun onCreate(sql:SupportSQLiteDatabase){
                val entities=schema.getJSONArray("entities");for(n in 0 until entities.length()){
                    val e=entities.getJSONObject(n);val table=e.getString("tableName");sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                    val indices=e.optJSONArray("indices");if(indices!=null)for(j in 0 until indices.length())sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
                }
                val setup=schema.getJSONArray("setupQueries");for(n in 0 until setup.length())sql.execSQL(setup.getString(n))
            }
            override fun onUpgrade(sql:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
        }).build())
        val id=UUID.randomUUID().toString();val valuation=UUID.randomUUID().toString();val sql=helper.writableDatabase
        sql.execSQL("INSERT INTO instruments VALUES (?,?,?,?,?,?,?,?,?,?,?)",arrayOf<Any?>(id,"Fictional legacy fund","MUTUAL_FUND","INR","TRANSACTIONS",start.toString(),clock.instant().toString(),1,"INDIA","units",null))
        sql.execSQL("INSERT INTO asset_valuations VALUES (?,?,?,?,?,?,?,?,?,?)",arrayOf<Any?>(valuation,id,"UNIT_PRICE","123.456789012345",start.toString(),1,clock.instant().toString(),"Fictional old quote",0,0))
        helper.close();db=BankDatabase.open(context,key,name);bank=RoomBankRepository(db,clock)
        val p=bank.observe().first().portfolio;assertEquals(7,db.openHelper.readableDatabase.version)
        assertEquals(id,p.instruments.single().id.toString());assertEquals("123.456789012345",p.valuations.single().value.toPlainString())
        assertNull(p.valuations.single().reportedUnits);assertFalse(p.valuations.single().calculatedTotal)
        db.openHelper.readableDatabase.query("PRAGMA cipher_version").use {assertTrue(it.moveToFirst());assertTrue(it.getString(0).isNotBlank())}
    }

}
