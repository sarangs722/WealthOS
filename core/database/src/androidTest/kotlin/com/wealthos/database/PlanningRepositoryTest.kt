package com.wealthos.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.wealthos.finance.*
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlanningRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name="planning-test-${UUID.randomUUID()}.db";private val key=ByteArray(32).also {java.security.SecureRandom().nextBytes(it)}
    private val today=LocalDate.of(2026,10,27);private val clock=Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"),ZoneOffset.UTC)
    private lateinit var db:BankDatabase;private lateinit var bank:RoomBankRepository;private lateinit var plans:RoomPlanningRepository
    @Before fun open(){db=BankDatabase.open(context,key,name);bank=RoomBankRepository(db,clock);plans=RoomPlanningRepository(db,clock)}
    @After fun close(){db.close();context.deleteDatabase(name);key.fill(0)}
    private fun s()=ForecastScenario(UUID.randomUUID(),UUID.randomUUID(),"Plan",ScenarioKind.BASE,ForecastMode.CONTRIBUTION,today,120,"0.050000000001".toBigDecimal(),BigDecimal.ZERO,"0.06".toBigDecimal(),"100.00".toBigDecimal(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,false,false,0,clock.instant(),"")
    private fun g()=FinancialGoal(UUID.randomUUID(),UUID.randomUUID(),"Goal",CurrencyCode.INR,"100000.00".toBigDecimal(),today.plusYears(5),true,"5000.00".toBigDecimal(),"0.06".toBigDecimal(),"0.05".toBigDecimal(),0,clock.instant(),"")
    @Test fun independentScenarioRevisionsAndExactReopen()=runTest {
        val base=s();plans.saveScenario(base,emptyList(),listOf(FxAssumption(base.id,CurrencyCode.USD,"0.020000000001".toBigDecimal())),listOf(PlannedWithdrawal(UUID.randomUUID(),base.id,6,null,"100.00".toBigDecimal(),"Trip")))
        val optimistic=base.copy(id=UUID.randomUUID(),key=UUID.randomUUID(),kind=ScenarioKind.OPTIMISTIC,investmentReturn="0.1".toBigDecimal());plans.saveScenario(optimistic,emptyList(),emptyList(),emptyList())
        plans.saveScenario(base.copy(id=UUID.randomUUID(),name="Changed",note="Updated return",investmentReturn="0.07".toBigDecimal()),emptyList(),emptyList(),emptyList())
        db.close();db=BankDatabase.open(context,key,name);bank=RoomBankRepository(db,clock)
        val b=bank.observe().first();assertEquals(3,b.planning.scenarios.size);assertEquals(2,b.planning.latestScenarios().size);assertEquals("0.050000000001",b.planning.scenarios.first().inflation.toPlainString());assertEquals("0.020000000001",b.planning.fx.single().annualChange.toPlainString());assertEquals("100.00",b.planning.withdrawals.single().amount.toPlainString())
    }
    @Test fun scenarioAndChildRowsRollbackTogetherWithAudit()=runTest {
        val i=Instrument(UUID.randomUUID(),"Fund",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.TRANSACTIONS,today,clock.instant());RoomPortfolioRepository(db,clock).addInstrument(NewInstrument(i))
        val before=db.bankDao().audits().size;db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_plan BEFORE INSERT ON audit_events WHEN NEW.action='FORECAST_SCENARIO' BEGIN SELECT RAISE(ABORT,'injected'); END")
        val s=s();try {plans.saveScenario(s,listOf(AssetAssumption(s.id,i.id,"0.06".toBigDecimal(),"100.00".toBigDecimal())),listOf(FxAssumption(s.id,CurrencyCode.USD,BigDecimal.ZERO)),listOf(PlannedWithdrawal(UUID.randomUUID(),s.id,2,null,"10.00".toBigDecimal(),"Trip")));fail()}catch(_:android.database.SQLException){}
        val p=bank.observe().first().planning;assertTrue(p.scenarios.isEmpty());assertTrue(p.assets.isEmpty());assertTrue(p.fx.isEmpty());assertTrue(p.withdrawals.isEmpty());assertEquals(before,db.bankDao().audits().size)
    }
    @Test fun goalRevisionsAreEarmarksAndCurrencyCannotChange()=runTest {
        bank.addAccount("Cash","",CurrencyCode.INR,AccountKind.SAVINGS,"20000.00".toBigDecimal(),today)
        val g=g();plans.saveGoal(g);plans.saveGoal(g.copy(id=UUID.randomUUID(),target="120000.00".toBigDecimal(),note="Changed target"))
        try {plans.saveGoal(g.copy(id=UUID.randomUUID(),currency=CurrencyCode.USD,note="Wrong currency"));fail()}catch(_:IllegalArgumentException){}
        val b=bank.observe().first();assertEquals(2,b.planning.goals.size);assertEquals(1,b.planning.latestGoals().size);assertEquals(0,"20000".toBigDecimal().compareTo(WealthValuation.at(b,today).netWorthInr!!))
    }
    @Test fun invalidRateOrOrphanAssumptionDoesNotPersist()=runTest {
        val s=s();try {plans.saveScenario(s.copy(inflation=BigDecimal("-1")),emptyList(),emptyList(),emptyList());fail()}catch(_:IllegalArgumentException){}
        try {plans.saveScenario(s,listOf(AssetAssumption(s.id,UUID.randomUUID(),BigDecimal.ZERO,BigDecimal.ZERO)),emptyList(),emptyList());fail()}catch(_:IllegalArgumentException){}
        assertTrue(bank.observe().first().planning.scenarios.isEmpty())
        try {db.planningDao().insert(ForecastAssetEntity(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"0","0"));fail()}catch(_:android.database.SQLException){}
    }
    @Test fun concurrentScenarioEditsHaveSequentialRevisions()=runTest {
        val first=s();plans.saveScenario(first,emptyList(),emptyList(),emptyList())
        (1..2).map {async {plans.saveScenario(first.copy(id=UUID.randomUUID(),note="Concurrent explained revision"),emptyList(),emptyList(),emptyList())}}.awaitAll()
        assertEquals(listOf(1,2,3),bank.observe().first().planning.scenarios.map {it.revision})
    }
    @Test fun completedGoalRoundTripsWithReferenceDateAndNoCashPosting()=runTest {
        bank.addAccount("Cash","",CurrencyCode.INR,AccountKind.SAVINGS,"1000.00".toBigDecimal(),today)
        val original=g();plans.saveGoal(original);plans.saveGoal(original.copy(id=UUID.randomUUID(),active=false,note="Goal completed; cash already recorded independently"))
        db.close();db=BankDatabase.open(context,key,name);bank=RoomBankRepository(db,clock);val book=bank.observe().first();val goal=book.planning.latestGoals().single()
        assertFalse(goal.active);assertEquals(today,goal.basisDate);assertEquals(2,book.planning.goals.size);assertEquals("1000.00",book.observations.single().amount.toPlainString());assertTrue(book.portfolio.cashMovements.isEmpty())
    }
    @Test fun populatedEncryptedSchemaFourPreservesHouseholdAndPortfolio()=runTest {
        db.close();val schema=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.wealthos.database.BankDatabase/4.json").bufferedReader().use {it.readText()}).getJSONObject("database")
        System.loadLibrary("sqlcipher")
        val helper=SupportOpenHelperFactory(key).create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object:SupportSQLiteOpenHelper.Callback(4){
            override fun onCreate(db:SupportSQLiteDatabase){val entities=schema.getJSONArray("entities");for(n in 0 until entities.length()){val e=entities.getJSONObject(n);val table=e.getString("tableName");db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table));val indices=e.optJSONArray("indices");if(indices!=null)for(j in 0 until indices.length())db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))};val setup=schema.getJSONArray("setupQueries");for(n in 0 until setup.length())db.execSQL(setup.getString(n))}
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected fixture upgrade")
        }).build())
        val account=UUID.randomUUID().toString();val obs=UUID.randomUUID().toString();val fund=UUID.randomUUID().toString();val source=UUID.randomUUID().toString();val policy=UUID.randomUUID().toString();val audit=UUID.randomUUID().toString();val stamp=clock.instant().toString();val day=today.toString()
        helper.writableDatabase.apply {
            execSQL("INSERT INTO bank_accounts VALUES (?, 'Existing cash', '', 'INR', 'SAVINGS', ?, ?)",arrayOf(account,stamp,stamp))
            execSQL("INSERT INTO balance_observations VALUES (?, ?, '10000.25', 'INR', ?, ?, 1, 'Keep bank history')",arrayOf(obs,account,day,stamp))
            execSQL("INSERT INTO instruments VALUES (?, 'Existing fund', 'MUTUAL_FUND', 'INR', 'TRANSACTIONS', ?, ?, 1, 'INDIA', 'units', NULL)",arrayOf(fund,day,stamp))
            execSQL("INSERT INTO investment_entries VALUES (?, ?, 'BUY', ?, 1, '5.00', '400.00', '0.00', '1', 'Keep purchase', ?)",arrayOf(UUID.randomUUID().toString(),fund,day,stamp))
            execSQL("INSERT INTO asset_valuations VALUES (?, ?, 'UNIT_PRICE', '100.123456', ?, 1, ?, 'Keep quote', 0, 1)",arrayOf(UUID.randomUUID().toString(),fund,day,stamp))
            execSQL("INSERT INTO income_sources VALUES (?, 'Existing salary', 'SALARY', 'INR', ?, NULL, ?, ?)",arrayOf(source,day,stamp,stamp))
            execSQL("INSERT INTO income_records VALUES (?, ?, '2026-10', '90000.00', '100000.00', '10000.00', 1, ?, 'Keep salary')",arrayOf(UUID.randomUUID().toString(),source,stamp))
            execSQL("INSERT INTO emergency_targets VALUES (?, ?, '30000.00', 1, ?, 'Keep target')",arrayOf(UUID.randomUUID().toString(),day,stamp))
            execSQL("INSERT INTO emergency_allocations VALUES (?, ?, NULL, ?, '5000.00', 1, ?, 'Keep earmark')",arrayOf(UUID.randomUUID().toString(),account,day,stamp))
            execSQL("INSERT INTO insurance_policies VALUES (?, 'Existing policy', 'Insurer', 'HEALTH', 'INR', ?, ?)",arrayOf(policy,stamp,stamp))
            execSQL("INSERT INTO insurance_terms VALUES (?, ?, ?, ?, ?, '1000000.00', '1000.00', 'MONTHLY', ?, 'FAMILY_FLOATER', 1, 1, ?, 'Keep cover', NULL)",arrayOf(UUID.randomUUID().toString(),policy,day,day,today.plusYears(1).toString(),today.plusDays(1).toString(),stamp))
            execSQL("INSERT INTO premium_settlements VALUES (?, ?, ?, ?, '500.00', 1, ?, 'Keep partial settlement')",arrayOf(UUID.randomUUID().toString(),policy,today.plusDays(1).toString(),day,stamp))
            execSQL("INSERT INTO audit_events VALUES (?, ?, 'OLD_AUDIT', ?, NULL, 'Keep audit')",arrayOf(audit,source,stamp))
        };helper.close()
        db=BankDatabase.open(context,key,name);bank=RoomBankRepository(db,clock);val b=bank.observe().first()
        assertEquals(8,db.openHelper.readableDatabase.version);assertEquals(account,b.accounts.single().id.toString());assertEquals("10000.25",b.observations.single().amount.toPlainString());assertEquals("5.00",b.portfolio.entries.single().units.toPlainString());assertEquals("100.123456",b.portfolio.valuations.single().value.toPlainString())
        assertEquals(source,b.household.sources.single().id.toString());assertEquals("90000.00",b.household.income.single().net.toPlainString());assertEquals("5000.00",b.household.allocations.single().nativeAmount.toPlainString());assertEquals(policy,b.household.policies.single().id.toString());assertEquals("500.00",b.household.settlements.single().amount.toPlainString());assertEquals(audit,db.bankDao().audits().single().id);assertTrue(b.planning.scenarios.isEmpty());assertTrue(b.planning.goals.isEmpty())
        assertEquals(0,"10500.86728".toBigDecimal().compareTo(WealthValuation.at(b,today).netWorthInr!!));assertFalse(String(context.getDatabasePath(name).readBytes().take(16).toByteArray()).startsWith("SQLite format"))
    }
}
