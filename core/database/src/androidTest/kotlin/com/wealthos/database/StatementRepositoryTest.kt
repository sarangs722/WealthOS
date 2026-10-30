package com.wealthos.database

import androidx.test.core.app.ApplicationProvider
import com.wealthos.finance.*
import com.wealthos.security.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class StatementRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name="statement-test-${UUID.randomUUID()}.db";private val key=ByteArray(32).also(java.security.SecureRandom()::nextBytes)
    private val at=Instant.parse("2026-10-20T12:00:00Z");private val clock=Clock.fixed(at,ZoneOffset.UTC);private val month=YearMonth.of(2026,10)
    private lateinit var db:BankDatabase;private lateinit var repo:RoomStatementRepository
    @Before fun open(){db=BankDatabase.open(context,key,name);repo=RoomStatementRepository(db,clock)}
    @After fun close(){db.close();context.deleteDatabase(name);key.fill(0)}
    private fun parsed(hash:Char='a',count:Int=2)=ParsedStatement(StatementKind.HDFC_BANK,hash.toString().repeat(64),month.atDay(1),month.atDay(20),(1..count).map {StatementInputRow(it,month.atDay(2),null,"Fictional identical shop","","10.25".toBigDecimal(),StatementDirection.DEBIT)})
    private fun categories(count:Int=2,category:SpendingCategory=SpendingCategory.ESSENTIAL)=(1..count).associateWith {category}
    @Test fun saveReopenAndSameFileAreIdempotentWithoutChangingWealth()=runTest {
        val bank=RoomBankRepository(db,clock);bank.addAccount("Fictional bank","",CurrencyCode.INR,AccountKind.SAVINGS,"1000.00".toBigDecimal(),month.atDay(1))
        val before=bank.observe().first();val id=repo.save(parsed(),"Fictional",month,categories(),emptySet(),true)
        assertEquals("20.50",db.readStatements().totals(month).netSpending.toPlainString())
        try{repo.save(parsed(),"Different nickname",month,categories(),emptySet(),true);fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,bank.observe().first());db.close();db=BankDatabase.open(context,key,name);repo=RoomStatementRepository(db,clock)
        assertEquals(id,db.readStatements().imports.single().id);assertEquals(2,db.readStatements().lines.size)
    }
    @Test fun overlappingFileRequiresExplicitKeepOrDuplicateDecision()=runTest {
        repo.save(parsed(count=1),"Fictional",month,categories(1),emptySet(),true)
        try{repo.save(parsed('b',1),"fictional",month,categories(1),emptySet(),false);fail()}catch(_:IllegalArgumentException){}
        assertEquals(1,db.readStatements().imports.size)
        repo.save(parsed('b',1),"Fictional",month,categories(1),setOf(1),true)
        assertEquals("20.50",db.readStatements().totals(month).netSpending.toPlainString())
        repo.save(parsed('c',1),"Fictional",month,categories(1,SpendingCategory.DUPLICATE),emptySet(),true)
        assertEquals("20.50",db.readStatements().totals(month).netSpending.toPlainString())
    }
    @Test fun draftCorrectionVoidAndStaleRevisionRetainHistory()=runTest {
        val id=repo.save(parsed(),"Fictional",month,categories(category=SpendingCategory.UNREVIEWED),emptySet(),false)
        assertEquals(2,db.readStatements().totals(month).unresolved)
        repo.revise(id,1,categories(),emptySet(),true)
        try{repo.revise(id,1,categories(),emptySet(),false);fail()}catch(_:IllegalArgumentException){}
        repo.revise(id,2,categories(),emptySet(),false,true)
        val book=db.readStatements();assertEquals(3,book.reviews.size);assertEquals(6,book.decisions.size);assertEquals(2,book.lines.size);assertEquals(0,book.totals(month).netSpending.signum())
        repo.revise(id,3,categories(),emptySet(),true);assertEquals("20.50",db.readStatements().totals(month).netSpending.toPlainString())
    }
    @Test fun partialDatabaseFailureRollsBackEveryImportRowAndAudit()=runTest {
        val before=db.bankDao().audits()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_statement BEFORE INSERT ON statement_decisions BEGIN SELECT RAISE(ABORT,'fictional injected failure'); END")
        try{repo.save(parsed(),"Fictional",month,categories(),emptySet(),true);fail()}catch(_:android.database.SQLException){}
        assertEquals(StatementBook(),db.readStatements());assertEquals(before,db.bankDao().audits())
    }
    @Test fun futureAndInvalidCoverageAreRejectedWithoutPartialWrites()=runTest {
        try{repo.save(parsed(),"Fictional",month,categories(category=SpendingCategory.LOAN_PAYMENT),emptySet(),true);fail()}catch(_:IllegalArgumentException){}
        val future=parsed().copy(lastCovered=month.atEndOfMonth(),rows=parsed().rows.map {it.copy(date=month.atDay(30))})
        try{repo.save(future,"Fictional",month,categories(),emptySet(),false);fail()}catch(_:IllegalArgumentException){}
        assertEquals(StatementBook(),db.readStatements())
    }
    @Test fun encryptedPortableBackupRetainsRowsRevisionsAndVoidAcrossDeviceKeys()=runTest(timeout=5.minutes) {
        val id=repo.save(parsed(),"Fictional",month,categories(),emptySet(),true)
        repo.revise(id,1,categories(),emptySet(),false,true)
        val before=db.readStatements();val backup=RoomBackupRepository(context,db,clock)
        val password="Fictional portable statement password".toCharArray();val bytes=backup.export(password)
        val receiverName="statement-receiver-${UUID.randomUUID()}.db";val receiverKey=ByteArray(32).also(java.security.SecureRandom()::nextBytes)
        val receiver=BankDatabase.open(context,receiverKey,receiverName)
        try {val other=RoomBackupRepository(context,receiver,clock);val plan=other.prepare(bytes,password);other.restore(plan);assertEquals(before,receiver.readStatements());assertFalse(String(context.getDatabasePath(receiverName).readBytes().take(16).toByteArray()).startsWith("SQLite format"))}
        finally{receiver.close();context.deleteDatabase(receiverName);receiverKey.fill(0);password.fill('\u0000');bytes.fill(0)}
    }
    @Test fun schemaSevenBackupMigratesAndPreservesBankObservations()=runTest(timeout=5.minutes) {
        RoomBankRepository(db,clock).addAccount("Fictional old bank","",CurrencyCode.INR,AccountKind.SAVINGS,"1234.56".toBigDecimal(),month.atDay(1))
        val backup=RoomBackupRepository(context,db,clock);val current=backup.snapshot();val old=current.copy(schema=7,tables=current.tables.filterNot {it.name in setOf("statement_imports","statement_lines","statement_reviews","statement_decisions")})
        val password="Fictional schema seven password".toCharArray();val file=BackupEnvelope.encrypt(SnapshotCodec.encode(old),password)
        try{val plan=backup.prepare(file,password);assertEquals(8,plan.info.schema);backup.restore(plan);assertEquals("1234.56",db.readBook().observations.single().amount.toPlainString());assertEquals(StatementBook(),db.readStatements())}
        finally{password.fill('\u0000');file.fill(0)}
    }
    @Test fun invalidBackupCategoriesAndBooleanCannotReachCurrentVault()=runTest(timeout=5.minutes) {
        repo.save(parsed(),"Fictional",month,categories(),emptySet(),true)
        val backup=RoomBackupRepository(context,db,clock);val before=backup.snapshot();val password="Fictional invalid statement password".toCharArray()
        for((table,column,value) in listOf(Triple("statement_decisions","category",Cell.Text("REFUND")),Triple("statement_reviews","coverageConfirmed",Cell.Integer(2)),Triple("statement_imports","currency",Cell.Text("USD")))) {
            val bad=before.copy(tables=before.tables.map {t->if(t.name!=table)t else t.copy(rows=t.rows.map {r->r.toMutableList().also {it[t.columns.indexOf(column)]=value}})})
            val file=BackupEnvelope.encrypt(SnapshotCodec.encode(bad),password)
            try{backup.prepare(file,password);fail()}catch(_:IllegalArgumentException){}finally{file.fill(0)}
            assertEquals(before.tables,backup.snapshot().tables)
        }
        password.fill('\u0000')
    }
}
