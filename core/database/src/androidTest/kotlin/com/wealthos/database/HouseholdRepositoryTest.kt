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
import java.time.YearMonth
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
class HouseholdRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name="household-test-${UUID.randomUUID()}.db";private val key=ByteArray(32).also {java.security.SecureRandom().nextBytes(it)}
    private val today=LocalDate.of(2026,10,27);private val month=YearMonth.from(today)
    private val clock=Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"),ZoneOffset.UTC)
    private lateinit var db:BankDatabase;private lateinit var bank:RoomBankRepository;private lateinit var repo:RoomHouseholdRepository
    @Before fun open() {db=BankDatabase.open(context,key,name);bank=RoomBankRepository(db,clock);repo=RoomHouseholdRepository(db,clock)}
    @After fun close() {db.close();context.deleteDatabase(name);key.fill(0)}
    private fun number(expected:String,actual:BigDecimal?)=assertEquals(0,expected.toBigDecimal().compareTo(actual!!))
    private fun source()=IncomeSource(UUID.randomUUID(),"Salary fixture",IncomeKind.SALARY,CurrencyCode.INR,today.withDayOfMonth(1),null,clock.instant(),clock.instant())
    private fun summary()=MonthlyCashFlow(UUID.randomUUID(),month,CurrencyCode.INR,IncomeMode.RECORDED_SOURCES,null,"20000".toBigDecimal(),"5000".toBigDecimal(),"1000".toBigDecimal(),"3000".toBigDecimal(),"1000".toBigDecimal(),BigDecimal.ZERO,"10000".toBigDecimal(),true,true,0,clock.instant(),"")
    private fun policy()=InsurancePolicy(UUID.randomUUID(),"Policy fixture","Insurer",PolicyKind.HEALTH,CurrencyCode.INR,clock.instant(),clock.instant())
    private fun terms(p:InsurancePolicy)=InsuranceTerms(UUID.randomUUID(),p.id,today,today,today.plusYears(1),"1000000".toBigDecimal(),"1000".toBigDecimal(),PremiumFrequency.MONTHLY,today.plusDays(5),CoverageScope.FAMILY_FLOATER,true,0,clock.instant(),"")
    @Test fun salaryRateAndActualIncomeRoundTripWithExactRevisions()=runTest {
        val s=source();repo.saveSource(s,"100000.00".toBigDecimal());repo.saveCashFlow(summary())
        assertNull(CashFlows.at(bank.observe().first().household,month,CurrencyCode.INR).income)
        val r=IncomeRecord(UUID.randomUUID(),s.id,month,"90000.00".toBigDecimal(),"100000.00".toBigDecimal(),"10000.00".toBigDecimal(),0,clock.instant(),"")
        repo.saveIncome(r)
        repo.saveIncome(r.copy(id=UUID.randomUUID(),net="95000.00".toBigDecimal(),gross="105000.00".toBigDecimal(),note="Corrected payslip"))
        val book=bank.observe().first();assertEquals(2,book.household.income.size);number("95000",CashFlows.at(book.household,month,CurrencyCode.INR).income)
        db.close();db=BankDatabase.open(context,key,name);bank=RoomBankRepository(db,clock)
        assertEquals("95000.00",bank.observe().first().household.income.maxBy {it.revision}.net.toPlainString())
    }
    @Test fun invalidSalaryAndFutureActualMonthDoNotPersist()=runTest {
        val s=source();repo.saveSource(s,null)
        val bad=IncomeRecord(UUID.randomUUID(),s.id,month,"90000".toBigDecimal(),"100000".toBigDecimal(),"5000".toBigDecimal(),0,clock.instant(),"")
        try {repo.saveIncome(bad);fail()}catch(_:IllegalArgumentException){}
        try {repo.saveIncome(bad.copy(gross=null,deductions=null,month=month.plusMonths(1)));fail()}catch(_:IllegalArgumentException){}
        assertTrue(bank.observe().first().household.income.isEmpty())
    }
    @Test fun monthlySummaryRevisionsReplaceAndNeverTouchBankCash()=runTest {
        val account=bank.addAccount("Wallet","Bank",CurrencyCode.INR,AccountKind.SAVINGS,"20000".toBigDecimal(),today)
        val original=summary().copy(incomeMode=IncomeMode.MANUAL_TOTAL,manualIncome="90000".toBigDecimal())
        repo.saveCashFlow(original)
        repo.saveCashFlow(original.copy(id=UUID.randomUUID(),discretionary="10000".toBigDecimal(),note="Corrected monthly total"))
        val book=bank.observe().first();number("55000",CashFlows.at(book.household,month,CurrencyCode.INR).surplusBeforeInvestments)
        assertEquals(2,book.household.cashFlows.size);number("20000",WealthValuation.at(book,today).netWorthInr);assertEquals(account,book.accounts.single().id)
    }
    @Test fun incomeAndPolicyFailuresRollBackTheirFinancialRowsAndAudits()=runTest {
        val s=source();repo.saveSource(s,null);val before=db.bankDao().audits().size
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_income BEFORE INSERT ON audit_events WHEN NEW.action='MONTHLY_INCOME' BEGIN SELECT RAISE(ABORT,'injected'); END")
        try {repo.saveIncome(IncomeRecord(UUID.randomUUID(),s.id,month,"10".toBigDecimal(),null,null,0,clock.instant(),""));fail()}catch(_:android.database.SQLException){}
        assertTrue(bank.observe().first().household.income.isEmpty());assertEquals(before,db.bankDao().audits().size)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_policy BEFORE INSERT ON insurance_terms BEGIN SELECT RAISE(ABORT,'injected'); END")
        val p=policy();try {repo.savePolicy(p,terms(p));fail()}catch(_:android.database.SQLException){}
        assertTrue(bank.observe().first().household.policies.isEmpty());assertEquals(before,db.bankDao().audits().size)
    }
    @Test fun earmarksAreCappedByLaterBankValuesAndCanBeReleased()=runTest {
        val id=bank.addAccount("Reserve","",CurrencyCode.INR,AccountKind.SAVINGS,"20000".toBigDecimal(),today.minusDays(1))
        repo.saveTarget(EmergencyTarget(UUID.randomUUID(),today,"30000".toBigDecimal(),0,clock.instant(),""))
        val a=EmergencyAllocation(UUID.randomUUID(),id,null,today.minusDays(1),"10000".toBigDecimal(),0,clock.instant(),"")
        repo.saveAllocation(a);number("10000",EmergencyFunds.at(bank.observe().first(),today).allocatedInr)
        bank.recordBalance(id,"5000".toBigDecimal(),today,"")
        val book=bank.observe().first();number("5000",EmergencyFunds.at(book,today).allocatedInr);number("5000",WealthValuation.at(book,today).netWorthInr)
        try {repo.saveAllocation(a.copy(id=UUID.randomUUID(),date=today,nativeAmount="6000".toBigDecimal()));fail()}catch(_:IllegalArgumentException){}
        repo.saveAllocation(a.copy(id=UUID.randomUUID(),date=today,nativeAmount=BigDecimal.ZERO,note="Released allocation"))
        number("0",EmergencyFunds.at(bank.observe().first(),today).allocatedInr)
    }
    @Test fun policyAndSettlementHistoryDoNotCreateWealthOrRenewCover()=runTest {
        bank.addAccount("Cash","",CurrencyCode.INR,AccountKind.SAVINGS,"10000".toBigDecimal(),today)
        val p=policy();val t=terms(p);repo.savePolicy(p,t)
        val receipt=PremiumSettlement(UUID.randomUUID(),p.id,t.anchorDue!!,today,"500".toBigDecimal(),0,clock.instant(),"")
        repo.saveSettlement(receipt);repo.saveSettlement(receipt.copy(id=UUID.randomUUID(),amount="1000".toBigDecimal(),note="Remaining amount received"))
        val book=bank.observe().first();assertEquals(2,book.household.settlements.size);assertEquals(t.validUntil,Insurance.view(book.household,p,today).terms!!.validUntil)
        assertEquals(t.anchorDue!!.plusMonths(1),Insurance.view(book.household,p,today).nextDue!!.dueOn);number("10000",WealthValuation.at(book,today).netWorthInr)
    }
    @Test fun futurePolicyTermsRemainPlannedAndSourceCurrencyIsImmutable()=runTest {
        val p=policy();repo.savePolicy(p,terms(p).copy(effectiveFrom=today.plusDays(1)))
        assertEquals("Planned / no terms effective yet",Insurance.view(bank.observe().first().household,p,today).status)
        val s=source();repo.saveSource(s,null)
        try {repo.saveSource(s.copy(currency=CurrencyCode.USD),null);fail()}catch(_:IllegalArgumentException){}
        assertEquals(CurrencyCode.INR,bank.observe().first().household.sources.single().currency)
    }
    @Test fun populatedEncryptedSchemaThreeKeepsPortfolioCashAndAuditRows()=runTest {
        db.close();val schema=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.wealthos.database.BankDatabase/3.json").bufferedReader().use {it.readText()}).getJSONObject("database")
        System.loadLibrary("sqlcipher")
        val helper=SupportOpenHelperFactory(key).create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object:SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db:SupportSQLiteDatabase) {
                val entities=schema.getJSONArray("entities")
                for(n in 0 until entities.length()) {val e=entities.getJSONObject(n);val table=e.getString("tableName");db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table));val indices=e.optJSONArray("indices");if(indices!=null)for(i in 0 until indices.length())db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}",table))}
                val setup=schema.getJSONArray("setupQueries");for(n in 0 until setup.length())db.execSQL(setup.getString(n))
            }
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected upgrade")
        }).build())
        val account=UUID.randomUUID().toString();val snapshot=UUID.randomUUID().toString();val asset=UUID.randomUUID().toString();val trade=UUID.randomUUID().toString();val price=UUID.randomUUID().toString();val movement=UUID.randomUUID().toString();val audit=UUID.randomUUID().toString();val stamp=clock.instant().toString();val start=today.withDayOfMonth(1).toString()
        helper.writableDatabase.apply {
            execSQL("INSERT INTO bank_accounts VALUES (?, 'Existing bank', 'Bank', 'INR', 'SAVINGS', ?, ?)",arrayOf(account,stamp,stamp))
            execSQL("INSERT INTO balance_observations VALUES (?, ?, '10000.25', 'INR', ?, ?, 1, 'Existing bank note')",arrayOf(snapshot,account,start,stamp))
            execSQL("INSERT INTO instruments VALUES (?, 'Existing fund', 'MUTUAL_FUND', 'INR', 'TRANSACTIONS', ?, ?, 1, 'INDIA', 'units', NULL)",arrayOf(asset,start,stamp))
            execSQL("INSERT INTO investment_entries VALUES (?, ?, 'BUY', ?, 1, '5.00', '400.00', '0.00', '1', 'Existing transaction', ?)",arrayOf(trade,asset,start,stamp))
            execSQL("INSERT INTO asset_valuations VALUES (?, ?, 'UNIT_PRICE', '100.123456', ?, 1, ?, 'Existing quote', 0, 1)",arrayOf(price,asset,start,stamp))
            execSQL("INSERT INTO cash_movements VALUES (?, ?, 'INR', ?, '-400.00', ?, 1, ?, NULL, NULL, ?, 'Existing purchase cash')",arrayOf(movement,account,start,snapshot,trade,stamp))
            execSQL("INSERT INTO audit_events VALUES (?, ?, 'INVESTMENT_EVENT', ?, NULL, 'Original audit')",arrayOf(audit,trade,stamp))
        }
        helper.close();db=BankDatabase.open(context,key,name);bank=RoomBankRepository(db,clock)
        val book=bank.observe().first();assertEquals(8,db.openHelper.readableDatabase.version)
        assertEquals(account,book.accounts.single().id.toString());assertEquals("10000.25",book.observations.single().amount.toPlainString());assertEquals("Existing bank note",book.observations.single().note)
        assertEquals(asset,book.portfolio.instruments.single().id.toString());assertEquals("5.00",book.portfolio.entries.single().units.toPlainString());assertEquals("100.123456",book.portfolio.valuations.single().value.toPlainString())
        assertEquals(movement,book.portfolio.cashMovements.single().id.toString());assertEquals(audit,db.bankDao().audits().single().id)
        assertTrue(book.household.sources.isEmpty());assertTrue(book.household.policies.isEmpty());number("10100.86728",WealthValuation.at(book,today).netWorthInr)
        val encrypted=context.getDatabasePath(name).readBytes();assertFalse(String(encrypted.take(16).toByteArray()).startsWith("SQLite format"))
    }
}
