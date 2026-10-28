package com.wealthos.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.wealthos.finance.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name="test-${UUID.randomUUID()}.db"
    private val key=ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
    private lateinit var db: BankDatabase
    private lateinit var repo: RoomBankRepository
    private val today=LocalDate.of(2026,1,31)
    private val clock=Clock.fixed(Instant.parse("2026-01-31T12:00:00Z"),ZoneOffset.UTC)
    @Before fun open() { db=BankDatabase.open(context,key,name); repo=RoomBankRepository(db,clock) }
    @After fun close() { db.close(); context.deleteDatabase(name); key.fill(0) }
    private suspend fun add(value: String="12345678901234567890.12") = repo.addAccount("Private savings", "Example Bank",CurrencyCode.INR,AccountKind.SAVINGS,value.toBigDecimal(),today)

    @Test fun encryptedRoundTripAndWrongKey() = runTest {
        add()
        db.close()
        val bytes=context.getDatabasePath(name).readBytes()
        assertFalse(String(bytes.take(16).toByteArray()).startsWith("SQLite format 3"))
        assertFalse(String(bytes,Charsets.ISO_8859_1).contains("Private savings"))
        val wrong=BankDatabase.open(context,ByteArray(32) { 8 },name)
        try { assertThrows(Exception::class.java) { wrong.openHelper.writableDatabase }; }
        finally { wrong.close() }
        db=BankDatabase.open(context,key,name); repo=RoomBankRepository(db,clock)
        assertEquals("12345678901234567890.12",repo.observe().first().observations.single().amount.toPlainString())
    }
    @Test fun editsAndCorrectionsPreserveHistoryAndAudit() = runTest {
        val id=add("100.10")
        repo.editAccount(id,"Renamed","Bank",AccountKind.CURRENT)
        repo.recordBalance(id,"200.20".toBigDecimal(),today.minusDays(1),"Backdated")
        repo.recordBalance(id,"110.10".toBigDecimal(),today,"Corrected statement")
        val book=repo.observe().first()
        assertEquals("Renamed",book.accounts.single().name)
        assertEquals(3,book.observations.size)
        assertEquals(2,book.observations.maxOf {it.revision})
        assertEquals("110.10",BankValuation.at(book,today).netWorthInr!!.toPlainString())
        assertEquals(5,db.bankDao().audits().size)
        assertNotNull(db.bankDao().audits().first {it.action=="EDIT_ACCOUNT"}.beforeValue)
    }
    @Test fun rejectsFutureDatesPrecisionAndUnexplainedCorrectionWithoutMutatingData() = runTest {
        val id=add("100")
        try { repo.recordBalance(id,"200".toBigDecimal(),today,""); fail() } catch (_:IllegalArgumentException) {}
        try { repo.recordBalance(id,"200".toBigDecimal(),today.plusDays(1),"future"); fail() } catch (_:IllegalArgumentException) {}
        try { repo.recordBalance(id,"1.001".toBigDecimal(),today,"precision"); fail() } catch (_:IllegalArgumentException) {}
        assertEquals(1,repo.observe().first().observations.size)
    }
    @Test fun partialFailureRollsBackAccountBalanceAndAudit() = runTest {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_audit BEFORE INSERT ON audit_events WHEN NEW.action='OBSERVE_BALANCE' BEGIN SELECT RAISE(ABORT, 'injected'); END")
        try { add(); fail("Should reject the transaction") } catch (_: android.database.SQLException) {}
        assertTrue(db.bankDao().read().isEmpty())
        assertTrue(db.bankDao().audits().isEmpty())
    }
    @Test fun foreignKeyRejectsOrphanObservation() = runTest {
        try {
            db.bankDao().insertObservation(ObservationEntity(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"1.00","INR",today.toString(),clock.instant().toString(),1))
            fail("Orphan should not persist")
        } catch (_:android.database.SQLException) {}
        assertTrue(db.bankDao().read().isEmpty())
    }
    @Test fun concurrentCorrectionsHaveUniqueSequentialRevisions() = runTest {
        val id=add("10")
        (1..8).map { n -> async {repo.recordBalance(id,n.toBigDecimal(),today,"Correction $n")} }.awaitAll()
        assertEquals((1..9).toList(),repo.observe().first().observations.map {it.revision}.sorted())
    }
    @Test fun migrationOneToTwoPreservesEncryptedRecords() = runTest {
        db.close()
        System.loadLibrary("sqlcipher")
        val helper=SupportOpenHelperFactory(key).create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object:SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db:SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE bank_accounts (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, institution TEXT NOT NULL, currency TEXT NOT NULL, kind TEXT NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE balance_observations (id TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, amount TEXT NOT NULL, currency TEXT NOT NULL, effectiveDate TEXT NOT NULL, recordedAt TEXT NOT NULL, revision INTEGER NOT NULL, FOREIGN KEY(accountId) REFERENCES bank_accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                    db.execSQL("CREATE UNIQUE INDEX index_balance_observations_accountId_effectiveDate_revision ON balance_observations(accountId,effectiveDate,revision)")
                    db.execSQL("CREATE TABLE audit_events (id TEXT NOT NULL PRIMARY KEY, entityId TEXT NOT NULL, action TEXT NOT NULL, recordedAt TEXT NOT NULL, beforeValue TEXT, afterValue TEXT NOT NULL)")
                    db.execSQL("CREATE INDEX index_audit_events_entityId ON audit_events(entityId)")
                }
                override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int) { error("Unexpected upgrade") }
            }).build())
        val id=UUID.randomUUID().toString()
        helper.writableDatabase.apply {
            execSQL("INSERT INTO bank_accounts VALUES (?, 'Legacy', 'Bank', 'INR', 'SAVINGS', ?, ?)", arrayOf(id,clock.instant().toString(),clock.instant().toString()))
            execSQL("INSERT INTO balance_observations VALUES (?, ?, '999.99', 'INR', ?, ?, 1)",arrayOf(UUID.randomUUID().toString(),id,today.toString(),clock.instant().toString()))
        }
        helper.close()
        db=BankDatabase.open(context,key,name)
        val observation=db.bankDao().read().single().observations.single()
        assertEquals("999.99",observation.amount)
        assertEquals("",observation.note)
        assertEquals(7,db.openHelper.readableDatabase.version)
    }
}
