package com.wealthos.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.wealthos.finance.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PublicRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name="public-test-${UUID.randomUUID()}.db";private val key=ByteArray(32).also {java.security.SecureRandom().nextBytes(it)}
    private val day=LocalDate.parse("2026-10-27");private val at=Instant.parse("2026-10-27T12:00:00Z");private val clock=Clock.fixed(at,ZoneOffset.UTC)
    private lateinit var db:BankDatabase;private lateinit var repo:RoomPublicRepository;private lateinit var bank:RoomBankRepository
    @Before fun open(){db=BankDatabase.open(context,key,name);repo=RoomPublicRepository(db,clock);bank=RoomBankRepository(db,clock)}
    @After fun close(){db.close();context.deleteDatabase(name);key.fill(0)}
    private fun fx(value:String="80.123456789012")=PublicDatum(PublicProvider.ECB,PublicKind.FX,"FX:USD","USD to INR reference",value.toBigDecimal(),CurrencyCode.USD,"INR per native unit",day,"https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml",PublicValidation.HTTPS_VALIDATED,"EUR-base cross rate","Source: ECB; fixture derived cross rate","Published source date")
    private fun nav()=PublicDatum(PublicProvider.AMFI_FILE,PublicKind.NAV,"NAV:123456","Fictional Direct Growth","90.123456789012".toBigDecimal(),CurrencyCode.INR,"INR per unit",day.minusDays(1),PublicProvider.AMFI_FILE.home,PublicValidation.USER_FILE,"code 123456","Fictional selected fund fixture","File date","INF000000001")
    private suspend fun fund():Instrument {val i=Instrument(UUID.randomUUID(),"Fund",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.TRANSACTIONS,day,at);RoomPortfolioRepository(db,clock).addInstrument(NewInstrument(i,openingUnits="5".toBigDecimal(),openingCost="400.00".toBigDecimal()));repo.saveBinding(NavBinding(UUID.randomUUID(),i.id,"123456","INF000000001",true,0,at));return i}
    private suspend fun cache(d:PublicDatum){repo.cache(d.provider,listOf(d),at,at,"Reference cached")}
    private suspend fun book()=bank.observe().first()
    @Test fun networkDefaultsOffAndNoPublicFinancialPosting()=runTest {
        bank.addAccount("USD cash","",CurrencyCode.USD,AccountKind.SAVINGS,"10.00".toBigDecimal(),day)
        assertFalse(book().publicData.networkEnabled)
        try{cache(fx());fail()}catch(_:IllegalArgumentException){}
        repo.setNetwork(true);cache(fx());val b=book();assertTrue(b.portfolio.fx.isEmpty());assertTrue(b.portfolio.cashMovements.isEmpty());assertNull(WealthValuation.at(b,day).netWorthInr);assertEquals(1,b.publicData.observations.size)
    }
    @Test fun dedupRevisionAndFailureRetainLastKnown()=runTest {
        repo.setNetwork(true);cache(fx("80.1234"));cache(fx("80.123400"));assertEquals(1,book().publicData.observations.size)
        cache(fx("81"));repo.recordFailure(PublicProvider.ECB,at,"Offline; cache retained");val b=book();assertEquals(listOf(1,2),b.publicData.observations.map {it.revision});assertEquals(1,b.publicData.runs.count {!it.success});assertEquals("81",b.publicData.latest().single().datum.value.toPlainString());assertTrue(b.portfolio.fx.isEmpty())
    }
    @Test fun explicitFxApplicationWorksOfflineAndReopensExactly()=runTest {
        bank.addAccount("USD cash","",CurrencyCode.USD,AccountKind.SAVINGS,"10.00".toBigDecimal(),day);repo.setNetwork(true);cache(fx());val id=book().publicData.observations.single().id;repo.setNetwork(false);repo.applyFx(id,false);repo.applyFx(id,false)
        db.close();db=BankDatabase.open(context,key,name);bank=RoomBankRepository(db,clock);val b=book();assertEquals(1,b.portfolio.fx.size);assertEquals(id,b.portfolio.fx.single().source!!.id);assertEquals("80.123456789012",b.portfolio.fx.single().inrPerUnit.toPlainString());assertEquals(0,"801.234567890120".toBigDecimal().compareTo(WealthValuation.at(b,day).netWorthInr!!));assertFalse(b.publicData.networkEnabled)
    }
    @Test fun revertedProviderCorrectionAppendsAThirdRevision()=runTest {
        repo.setNetwork(true);cache(fx("80"));cache(fx("81"));cache(fx("80.0"));cache(fx("80.00"))
        val b=book();assertEquals(listOf(1,2,3),b.publicData.observations.map {it.revision});assertEquals(0,"80".toBigDecimal().compareTo(b.publicData.latest().single().datum.value));assertEquals(listOf(1,1,1,0),b.publicData.runs.map {it.count}.sortedDescending())
    }
    @Test fun manualSameDateFxRequiresReviewAndRetainsRevision()=runTest {
        RoomPortfolioRepository(db,clock).addFx(CurrencyCode.USD,"79".toBigDecimal(),day,"Manual source");repo.setNetwork(true);cache(fx());val id=book().publicData.observations.single().id
        try{repo.applyFx(id,false);fail()}catch(_:IllegalArgumentException){}
        assertEquals(1,book().portfolio.fx.size);repo.applyFx(id,true);assertEquals(listOf(1,2),book().portfolio.fx.map {it.revision});assertEquals("79",book().portfolio.fx.first().inrPerUnit.toPlainString())
    }
    @Test fun navUsesExactIdentityWithoutBackdatingPositionsOrCash()=runTest {
        val i=fund();cache(nav());val o=book().publicData.observations.single();repo.applyNav(o.id,i.id,false);repo.applyNav(o.id,i.id,false)
        val b=book();assertEquals(1,b.portfolio.entries.size);assertEquals(1,b.portfolio.valuations.size);assertTrue(b.portfolio.cashMovements.isEmpty());assertEquals(o.id,b.portfolio.valuations.single().source!!.id);assertNull(WealthValuation.asset(i,b.portfolio,day.minusDays(1)).nativeValue);assertEquals(0,"450.617283945060".toBigDecimal().compareTo(WealthValuation.asset(i,b.portfolio,day).nativeValue!!))
    }
    @Test fun changedBindingCannotApplyOldQuoteAndNoFuzzyIdentityMatching()=runTest {
        val i=fund();cache(nav());val o=book().publicData.observations.single();repo.saveBinding(NavBinding(UUID.randomUUID(),i.id,"654321","",true,0,at));try{repo.applyNav(o.id,i.id,true);fail()}catch(_:IllegalArgumentException){};assertTrue(book().portfolio.valuations.isEmpty());assertEquals(2,book().publicData.bindings.size)
    }
    @Test fun publishedClosingNavIsNotDiscountedAgainForLateRecordedSameDaySplit()=runTest {
        val i=Instrument(UUID.randomUUID(),"Fund",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.TRANSACTIONS,day.minusDays(2),at);val portfolio=RoomPortfolioRepository(db,clock)
        portfolio.addInstrument(NewInstrument(i,openingUnits="10".toBigDecimal(),openingCost="100.00".toBigDecimal()));repo.saveBinding(NavBinding(UUID.randomUUID(),i.id,"123456","INF000000001",true,0,at))
        cache(nav().copy(value="5".toBigDecimal()));repo.applyNav(book().publicData.observations.single().id,i.id,false)
        portfolio.addEntry(InvestmentEntry(UUID.randomUUID(),i.id,TradeKind.SPLIT,day.minusDays(1),0,"2".toBigDecimal(),recordedAt=at),null)
        db.close();db=BankDatabase.open(context,key,name);bank=RoomBankRepository(db,clock);val b=book()
        assertEquals(0,"100".toBigDecimal().compareTo(WealthValuation.asset(i,b.portfolio,day).nativeValue!!));assertEquals(0,"20".toBigDecimal().compareTo(WealthValuation.asset(i,b.portfolio,day).holding!!.units));assertEquals(1,b.publicData.applications.size);assertTrue(b.portfolio.cashMovements.isEmpty())
    }
    @Test fun cacheAndApplyRollbackWithAuditFailure()=runTest {
        val i=fund();cache(nav());val o=book().publicData.observations.single();val before=db.bankDao().audits().size
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_public BEFORE INSERT ON audit_events WHEN NEW.action='APPLY_PUBLIC_NAV' BEGIN SELECT RAISE(ABORT,'injected'); END")
        try{repo.applyNav(o.id,i.id,true);fail()}catch(_:android.database.SQLException){}
        assertTrue(book().portfolio.valuations.isEmpty());assertTrue(book().publicData.applications.isEmpty());assertEquals(before,db.bankDao().audits().size)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_cache BEFORE INSERT ON audit_events WHEN NEW.action='PUBLIC_CACHE' BEGIN SELECT RAISE(ABORT,'injected'); END")
        try{cache(nav().copy(value="99".toBigDecimal()));fail()}catch(_:android.database.SQLException){}
        assertEquals(1,book().publicData.observations.size);assertEquals(1,book().publicData.runs.size)
    }
    @Test fun invalidBatchAndOrphanApplicationNeverPersist()=runTest {
        repo.setNetwork(true)
        try{repo.cache(PublicProvider.ECB,listOf(fx(),fx("0")),at,at,"Invalid");fail()}catch(_:IllegalArgumentException){}
        assertTrue(book().publicData.observations.isEmpty());assertTrue(book().publicData.runs.isEmpty())
        try{db.publicDao().insert(PublicApplicationEntity(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"fx:USD",null,null,UUID.randomUUID().toString(),at.toString()));fail()}catch(_:android.database.SQLException){}
    }
    @Test fun populatedEncryptedSchemaFiveRetainsEveryExistingTable()=runTest {
        db.close();val schema=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.wealthos.database.BankDatabase/5.json").bufferedReader().use {it.readText()}).getJSONObject("database")
        System.loadLibrary("sqlcipher")
        val helper=SupportOpenHelperFactory(key).create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object:SupportSQLiteOpenHelper.Callback(5){
            override fun onCreate(db:SupportSQLiteDatabase){val entities=schema.getJSONArray("entities");for(n in 0 until entities.length()){val e=entities.getJSONObject(n);val t=e.getString("tableName");db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",t));val indices=e.optJSONArray("indices");if(indices!=null)for(j in 0 until indices.length())db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",t))};val setup=schema.getJSONArray("setupQueries");for(n in 0 until setup.length())db.execSQL(setup.getString(n))}
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected fixture upgrade")
        }).build())
        val account=UUID.randomUUID().toString();val stamp=at.toString();val plan=UUID.randomUUID().toString();val fund=UUID.randomUUID().toString();helper.writableDatabase.apply {
            execSQL("INSERT INTO bank_accounts VALUES (?, 'Existing cash', '', 'INR', 'SAVINGS', ?, ?)",arrayOf(account,stamp,stamp))
            execSQL("INSERT INTO balance_observations VALUES (?, ?, '12345.67', 'INR', ?, ?, 1, 'Retain')",arrayOf(UUID.randomUUID().toString(),account,day.toString(),stamp))
            execSQL("INSERT INTO audit_events VALUES (?, ?, 'OLD_AUDIT', ?, NULL, 'Retain')",arrayOf(UUID.randomUUID().toString(),account,stamp))
            execSQL("INSERT INTO instruments VALUES (?, 'Existing fund', 'MUTUAL_FUND', 'INR', 'TRANSACTIONS', ?, ?, 1, 'INDIA', 'units', NULL)",arrayOf(fund,day.toString(),stamp))
            execSQL("INSERT INTO forecast_scenarios VALUES (?, ?, 'Existing plan', 'BASE', 'CONTRIBUTION', ?, 120, '0.05', '0.01', '0.06', '100.00', '0.01', '0', '0', '0', '0', '0.05', '0', 0, 0, 1, ?, 'Keep plan')",arrayOf(plan,UUID.randomUUID().toString(),day.toString(),stamp))
            execSQL("INSERT INTO forecast_assets VALUES (?, ?, '0.060000000001', '10.00')",arrayOf(plan,fund))
            execSQL("INSERT INTO forecast_fx VALUES (?, 'USD', '0.02')",arrayOf(plan))
            execSQL("INSERT INTO forecast_withdrawals VALUES (?, ?, 6, NULL, '50.00', 'Keep withdrawal')",arrayOf(UUID.randomUUID().toString(),plan))
            execSQL("INSERT INTO financial_goals VALUES (?, ?, 'Existing goal', 'INR', '100000.00', ?, 1, '5000.00', '0.06', '0.05', 1, ?, 'Keep goal', ?, 1)",arrayOf(UUID.randomUUID().toString(),UUID.randomUUID().toString(),day.plusYears(5).toString(),stamp,day.toString()))
        }
        val oldTables=mutableMapOf<String,List<List<String?>>>()
        fun rows(sql:SupportSQLiteDatabase,t:String):List<List<String?>> {sql.query("SELECT * FROM `$t` ORDER BY rowid").use {c->val all=mutableListOf<List<String?>>();while(c.moveToNext())all+=List(c.columnCount){if(c.isNull(it))null else c.getString(it)};return all}}
        val es=schema.getJSONArray("entities");for(n in 0 until es.length()){val t=es.getJSONObject(n).getString("tableName");oldTables[t]=rows(helper.readableDatabase,t)};helper.close()
        db=BankDatabase.open(context,key,name);bank=RoomBankRepository(db,clock);val b=book();assertEquals(7,db.openHelper.readableDatabase.version)
        oldTables.forEach {(t,expected)->assertEquals("Preserve $t",expected,rows(db.openHelper.readableDatabase,t).map {if(t=="asset_valuations")it.take(10)else it})};assertFalse(b.publicData.networkEnabled);assertTrue(b.publicData.observations.isEmpty());assertEquals("12345.67",b.observations.single().amount.toPlainString());assertEquals("Existing plan",b.planning.scenarios.single().name);assertEquals("0.060000000001",b.planning.assets.single().annualReturn.toPlainString());assertEquals("Existing goal",b.planning.goals.single().name);assertFalse(String(context.getDatabasePath(name).readBytes().take(16).toByteArray()).startsWith("SQLite format"))
    }
}
