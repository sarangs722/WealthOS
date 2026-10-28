package com.wealthos.database

import android.content.Context
import android.database.Cursor
import android.util.AtomicFile
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.wealthos.finance.*
import com.wealthos.security.*
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject

data class BackupInfo(val createdAt:Instant,val schema:Int,val records:Int,val tables:Map<String,Int>)
data class SafetyBackup(val id:String,val bytes:Long)
class PreparedRestore internal constructor(internal val data:VaultSnapshot,internal val expected:ByteArray,internal val password:CharArray,val currentRecords:Int):AutoCloseable {
    val info=BackupInfo(data.createdAt,data.schema,data.records,data.tables.associate {it.name to it.rows.size})
    internal var closed=false
    override fun close(){password.fill('\u0000');expected.fill(0);closed=true}
    override fun toString()="PreparedRestore(records=${info.records})"
}

/** Consistent logical backups; never copies a live SQLite/WAL file or changes the receiving key. */
class RoomBackupRepository(private val context:Context,private val db:BankDatabase,private val clock:Clock=Clock.systemUTC(),private val safetyDirectory:File=File(context.noBackupFilesDir,"restore-safety")) {
    private val schema6 by lazy {JSONObject(context.assets.open("com.wealthos.database.BankDatabase/6.json").bufferedReader().use {it.readText()}).getJSONObject("database")}
    private val names by lazy {val e=schema6.getJSONArray("entities");(0 until e.length()).map {e.getJSONObject(it).getString("tableName")}.toSet()}
    private data class Column(val name:String,val type:String,val pk:Int)
    private data class Table(val name:String,val columns:List<Column>,val parents:Set<String>)

    suspend fun export(password:CharArray):ByteArray {
        BackupEnvelope.validatePassword(password)
        val snapshot=snapshot()
        val payload=SnapshotCodec.encode(snapshot)
        return try{BackupEnvelope.encrypt(payload,password)}finally{payload.fill(0)}
    }
    internal suspend fun snapshot():VaultSnapshot=db.withTransaction {capture(db.openHelper.writableDatabase)}
    suspend fun prepare(file:ByteArray,password:CharArray):PreparedRestore {
        val payload=BackupEnvelope.decrypt(file,password)
        val incoming=try{SnapshotCodec.decode(payload)}finally{payload.fill(0)}
        require(incoming.schema==6){"This backup's schema is unsupported. Update WealthOS before restoring; existing records were not changed."}
        val validated=stage(incoming)
        val current=db.withTransaction {capture(db.openHelper.writableDatabase)}
        return PreparedRestore(validated,SnapshotCodec.fingerprint(current),password.copyOf(),current.records)
    }
    suspend fun restore(plan:PreparedRestore):SafetyBackup {
        require(!plan.closed){"Prepare this restore again."}
        try {
            return db.withTransaction {
                currentCoroutineContext().ensureActive()
                val sql=db.openHelper.writableDatabase;val before=capture(sql)
                val token=SnapshotCodec.fingerprint(before)
                try{require(MessageDigest.isEqual(token,plan.expected)){"Your vault changed after the preview. Nothing was replaced; prepare the restore again."}}finally{token.fill(0)}
                val plain=SnapshotCodec.encode(before)
                val encrypted=try{BackupEnvelope.encrypt(plain,plan.password)}finally{plain.fill(0)}
                val safety=try{writeSafety(encrypted)}finally{encrypted.fill(0)}
                currentCoroutineContext().ensureActive()
                replace(sql,plan.data)
                // Network consent belongs to the current restore session, never to an imported file.
                db.publicDao().save(PublicSettingsEntity(1,false,clock.instant().toString()))
                db.bankDao().insertAudit(AuditEntity(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"RESTORE_BACKUP",clock.instant().toString(),null,"User-confirmed replacement from ${plan.info.createdAt}; ${plan.info.records} rows. Encrypted safety copy ${safety.id}; network off."))
                integrity(sql);validateRestoredBook(db)
                currentCoroutineContext().ensureActive()
                safety
            }
        }finally{plan.close()}
    }
    fun safetyBackups():List<SafetyBackup> = safetyDirectory.listFiles()?.filter {it.isFile && Regex("-?[0-9]{1,19}-[0-9a-f-]{36}\\.wosbackup").matches(it.name)}?.sortedByDescending {it.lastModified()}?.map {SafetyBackup(it.name,it.length())} ?: emptyList()
    fun readSafety(id:String):ByteArray {
        require(safetyBackups().any {it.id==id}){"Safety backup not found."}
        return File(safetyDirectory,id).inputStream().use {BackupEnvelope.read(it)}
    }
    private fun writeSafety(bytes:ByteArray):SafetyBackup {
        require(safetyDirectory.isDirectory || safetyDirectory.mkdirs()){"Cannot create the encrypted safety copy; current records were not replaced."}
        val id="${clock.millis()}-${UUID.randomUUID()}.wosbackup";val atomic=AtomicFile(File(safetyDirectory,id));val out=atomic.startWrite()
        try{out.write(bytes);atomic.finishWrite(out)}catch(e:Exception){atomic.failWrite(out);throw e}
        require(File(safetyDirectory,id).readBytes().contentEquals(bytes)){"Safety backup verification failed; current records were not replaced."}
        return SafetyBackup(id,bytes.size.toLong())
    }
    private suspend fun stage(input:VaultSnapshot):VaultSnapshot {
        val name="backup-stage-${UUID.randomUUID()}.db";val key=ByteArray(32).also(SecureRandom()::nextBytes)
        var helper:SupportSQLiteOpenHelper?=null;var opened:BankDatabase?=null
        try {
            System.loadLibrary("sqlcipher")
            helper=SupportOpenHelperFactory(key).create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object:SupportSQLiteOpenHelper.Callback(6){
                override fun onConfigure(db:SupportSQLiteDatabase){db.setForeignKeyConstraintsEnabled(true)}
                override fun onCreate(db:SupportSQLiteDatabase){
                    val entities=schema6.getJSONArray("entities")
                    for(n in 0 until entities.length()){val e=entities.getJSONObject(n);val table=e.getString("tableName");db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table));val indices=e.optJSONArray("indices");if(indices!=null)for(j in 0 until indices.length())db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))}
                    val setup=schema6.getJSONArray("setupQueries");for(n in 0 until setup.length())db.execSQL(setup.getString(n))
                }
                override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Use explicit Room migrations")
            }).build())
            val sql=helper.writableDatabase;sql.beginTransaction()
            try{replace(sql,input);integrity(sql);sql.setTransactionSuccessful()}finally{sql.endTransaction()}
            helper.close();helper=null
            // Future schema versions migrate this trusted v6 database through the normal chain.
            opened=BankDatabase.open(context,key,name)
            return opened.withTransaction {integrity(opened.openHelper.writableDatabase);validateRestoredBook(opened);capture(opened.openHelper.writableDatabase).copy(createdAt=input.createdAt)}
        }finally{opened?.close();helper?.close();context.deleteDatabase(name);key.fill(0)}
    }
    private fun tables(sql:SupportSQLiteDatabase):List<Table> {
        val actual=mutableSetOf<String>();sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table','android_metadata')").use {while(it.moveToNext())actual+=it.getString(0)}
        require(actual==names){"Unexpected database tables; a complete backup cannot be guaranteed."}
        val all=names.sorted().map {name->
            val columns=mutableListOf<Column>();sql.query("PRAGMA table_info(`$name`)").use {c->while(c.moveToNext())columns+=Column(c.getString(c.getColumnIndexOrThrow("name")),c.getString(c.getColumnIndexOrThrow("type")).uppercase(),c.getInt(c.getColumnIndexOrThrow("pk")))}
            require(columns.all {it.type in listOf("INTEGER","TEXT")})
            val parents=mutableSetOf<String>();sql.query("PRAGMA foreign_key_list(`$name`)").use {c->while(c.moveToNext())parents+=c.getString(c.getColumnIndexOrThrow("table"))}
            Table(name,columns,parents)
        }
        val sorted=mutableListOf<Table>();val todo=all.toMutableList()
        while(todo.isNotEmpty()){val ready=todo.filter {t->t.parents.all {p->sorted.any {it.name==p}}};require(ready.isNotEmpty()){ "Unsupported relationship cycle."};sorted+=ready;todo.removeAll(ready.toSet())}
        return sorted
    }
    private fun capture(sql:SupportSQLiteDatabase):VaultSnapshot {
        var count=0;var textBytes=0
        val data=tables(sql).map {t->val rows=mutableListOf<List<Cell>>();val order=t.columns.filter {it.pk>0}.sortedBy {it.pk}.joinToString(","){"`${it.name}`"};require(order.isNotBlank())
            sql.query("SELECT * FROM `${t.name}` ORDER BY $order").use {c->while(c.moveToNext()){require(++count<=SnapshotCodec.MAX_ROWS){"Vault exceeds the backup row limit; no records were changed."};rows+=List(c.columnCount){n->when(c.getType(n)){Cursor.FIELD_TYPE_NULL->Cell.Null;Cursor.FIELD_TYPE_INTEGER->Cell.Integer(c.getLong(n));Cursor.FIELD_TYPE_STRING->{val s=c.getString(n);textBytes+=s.toByteArray(Charsets.UTF_8).size;require(textBytes<=BackupEnvelope.MAX_PAYLOAD);Cell.Text(s)};else->throw IllegalArgumentException("Unsupported financial storage type; backup was not created.")}}}}
            SnapshotTable(t.name,t.columns.map {it.name},rows)
        };return VaultSnapshot(sql.version,clock.instant(),data)
    }
    private fun replace(sql:SupportSQLiteDatabase,snapshot:VaultSnapshot){
        val schema=tables(sql);require(snapshot.tables.map {it.name}.toSet()==schema.map {it.name}.toSet()){"Backup tables are incomplete or unsupported."}
        val incoming=snapshot.tables.associateBy {it.name}
        schema.forEach {t->val table=incoming.getValue(t.name);require(table.columns==t.columns.map {it.name}){"Backup columns do not match the trusted schema."};table.rows.forEach {row->require(row.size==t.columns.size);row.forEachIndexed {n,c->
            val column=t.columns[n].name
            require(c==Cell.Null || (t.columns[n].type=="TEXT" && c is Cell.Text) || (t.columns[n].type=="INTEGER" && c is Cell.Integer && c.value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())){"Backup contains incompatible storage types."}
            // Public observations use `units` for descriptive labels; entry units are decimals.
            if(c is Cell.Text && column in decimalColumns && (column!="units" || t.name=="investment_entries"))require(Regex("-?(0|[1-9][0-9]{0,23})(\\.[0-9]{1,14})?").matches(c.value)){"Backup contains an invalid financial decimal in ${t.name}.$column."}
            if(c is Cell.Text && column in uuidColumns)require(UUID.fromString(c.value).toString()==c.value){"Backup contains a noncanonical identifier."}
            if(c is Cell.Integer && column in booleanColumns)require(c.value==0L || c.value==1L){"Backup contains an invalid boolean."}
            if(c is Cell.Integer && column in listOf("revision","sequence"))require(c.value>0)
            if(t.name=="public_settings" && column=="id")require(c==Cell.Integer(1))
        }}}
        schema.asReversed().forEach {sql.execSQL("DELETE FROM `${it.name}`")}
        schema.forEach {t->val columns=t.columns.joinToString(","){"`${it.name}`"};val insert="INSERT INTO `${t.name}` ($columns) VALUES (${t.columns.joinToString(","){"?"}})"
            incoming.getValue(t.name).rows.forEach {row->sql.execSQL(insert,row.map<Cell,Any?> {when(it){Cell.Null->null;is Cell.Integer->it.value;is Cell.Text->it.value}}.toTypedArray())}
        }
    }
    private fun integrity(sql:SupportSQLiteDatabase){
        sql.query("PRAGMA integrity_check(1)").use {require(it.moveToFirst() && it.getString(0)=="ok"){"Backup database integrity check failed."}}
        sql.query("PRAGMA foreign_key_check").use {require(!it.moveToFirst()){"Backup contains broken relationships."}}
        sql.query("PRAGMA cipher_integrity_check").use {require(!it.moveToFirst()){"Encrypted staging database integrity check failed."}}
    }
    companion object {
        private val booleanColumns=setOf("liquid","applyToAnchor","incomeConfirmed","expensesConfirmed","active","reinvestDeposits","includeGoals","targetInTodayMoney","networkEnabled","success")
        private val uuidColumns=setOf("id","accountId","instrumentId","entryId","contractEventId","transferId","sourceId","policyId","scenarioId","scenarioKey","goalKey","observationId","valuationId","fxId","anchorObservationId","custodyAccountId","entityId")
        private val decimalColumns=setOf("amount","units","gross","fees","ratioDenominator","value","openingPrincipal","annualRate","scheduledInstallment","principal","interest","inrPerUnit","total","monthlyNet","net","deductions","manualIncome","essential","discretionary","premiums","loanPrincipal","loanInterest","otherOutflows","investments","targetInr","nativeAmount","cover","premium","inflation","cashReturn","investmentReturn","monthlyContribution","stepUp","salary","salaryGrowth","expenseInflation","annualReturn","monthlyAmount","annualChange","target","allocated")
    }
}
