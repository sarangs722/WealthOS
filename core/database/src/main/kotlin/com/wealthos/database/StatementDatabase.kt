package com.wealthos.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wealthos.finance.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.flow.map

@Entity(tableName="statement_imports",indices=[Index(value=["fileHash","month"],unique=true)])
data class StatementImportEntity(@PrimaryKey val id:String,val kind:String,val sourceName:String,val fileHash:String,val month:String,
    val firstCovered:String,val lastCovered:String,val parserVersion:Int,val createdAt:String,val periodStart:String?,val periodEnd:String?,val currency:String)
@Entity(tableName="statement_lines",foreignKeys=[ForeignKey(entity=StatementImportEntity::class,parentColumns=["id"],childColumns=["importId"],onDelete=ForeignKey.RESTRICT)],indices=[Index(value=["importId","sourceRow"],unique=true)])
data class StatementLineEntity(@PrimaryKey val id:String,val importId:String,val sourceRow:Int,val date:String,val valueDate:String?,val description:String,
    val reference:String,val amount:String,val direction:String)
@Entity(tableName="statement_reviews",foreignKeys=[ForeignKey(entity=StatementImportEntity::class,parentColumns=["id"],childColumns=["importId"],onDelete=ForeignKey.RESTRICT)],indices=[Index(value=["importId","revision"],unique=true)])
data class StatementReviewEntity(@PrimaryKey val id:String,val importId:String,val revision:Int,val voided:Boolean,val coverageConfirmed:Boolean,val recordedAt:String)
@Entity(tableName="statement_decisions",primaryKeys=["reviewId","lineId"],foreignKeys=[
    ForeignKey(entity=StatementReviewEntity::class,parentColumns=["id"],childColumns=["reviewId"],onDelete=ForeignKey.RESTRICT),
    ForeignKey(entity=StatementLineEntity::class,parentColumns=["id"],childColumns=["lineId"],onDelete=ForeignKey.RESTRICT)],indices=[Index("lineId")])
data class StatementDecisionEntity(val reviewId:String,val lineId:String,val category:String,val overlapKept:Boolean)
@Dao interface StatementDao {
    @Query("SELECT * FROM statement_imports ORDER BY createdAt,id") suspend fun imports():List<StatementImportEntity>
    @Query("SELECT * FROM statement_lines ORDER BY importId,sourceRow") suspend fun lines():List<StatementLineEntity>
    @Query("SELECT * FROM statement_reviews ORDER BY importId,revision") suspend fun reviews():List<StatementReviewEntity>
    @Query("SELECT * FROM statement_decisions ORDER BY reviewId,lineId") suspend fun decisions():List<StatementDecisionEntity>
    @Insert suspend fun insert(value:StatementImportEntity)
    @Insert suspend fun insertLines(value:List<StatementLineEntity>)
    @Insert suspend fun insert(value:StatementReviewEntity)
    @Insert suspend fun insertDecisions(value:List<StatementDecisionEntity>)
}
internal suspend fun BankDatabase.readStatements():StatementBook {
    val dao=statementDao()
    return StatementBook(dao.imports().map {StatementImport(UUID.fromString(it.id),StatementKind.valueOf(it.kind),it.sourceName,it.fileHash,YearMonth.parse(it.month),LocalDate.parse(it.firstCovered),LocalDate.parse(it.lastCovered),it.parserVersion,Instant.parse(it.createdAt),it.periodStart?.let(LocalDate::parse),it.periodEnd?.let(LocalDate::parse),CurrencyCode.valueOf(it.currency))},
        dao.lines().map {StatementLine(UUID.fromString(it.id),UUID.fromString(it.importId),StatementInputRow(it.sourceRow,LocalDate.parse(it.date),it.valueDate?.let(LocalDate::parse),it.description,it.reference,it.amount.toBigDecimal(),StatementDirection.valueOf(it.direction)))},
        dao.reviews().map {StatementReview(UUID.fromString(it.id),UUID.fromString(it.importId),it.revision,it.voided,it.coverageConfirmed,Instant.parse(it.recordedAt))},
        dao.decisions().map {StatementDecision(UUID.fromString(it.reviewId),UUID.fromString(it.lineId),SpendingCategory.valueOf(it.category),it.overlapKept)})
}

class RoomStatementRepository(private val db:BankDatabase,private val clock:Clock=Clock.systemDefaultZone()) {
    fun observe()=db.invalidationTracker.createFlow("statement_imports","statement_lines","statement_reviews","statement_decisions").map {db.withTransaction {db.readStatements()}}
    suspend fun save(parsed:ParsedStatement,sourceName:String,month:YearMonth,categories:Map<Int,SpendingCategory>,keepOverlaps:Set<Int>,coverageConfirmed:Boolean):UUID=db.withTransaction {
        val current=db.readStatements()
        require(current.imports.none {it.fileHash==parsed.fileHash && it.month==month}){"This file and month are already imported. Open the saved statement to revise it."}
        val batch=StatementImport(UUID.randomUUID(),parsed.kind,sourceName.trim(),parsed.fileHash,month,parsed.firstCovered,parsed.lastCovered,parsed.parserVersion,clock.instant(),parsed.periodStart,parsed.periodEnd,parsed.currency)
        val rows=parsed.rows.filter {YearMonth.from(it.date)==month};require(rows.isNotEmpty() && rows.all {it.date<=LocalDate.now(clock)}){"Choose a month with actual transactions through today."}
        val lines=rows.map {StatementLine(UUID.randomUUID(),batch.id,it)}
        append(current,batch,lines,categories,keepOverlaps,coverageConfirmed,false,0)
        batch.id
    }
    suspend fun revise(id:UUID,expectedRevision:Int,categories:Map<Int,SpendingCategory>,keepOverlaps:Set<Int>,coverageConfirmed:Boolean,voided:Boolean=false)=db.withTransaction {
        val current=db.readStatements();val batch=current.imports.single {it.id==id};val revision=current.latest(id)!!.revision
        require(revision==expectedRevision){"This statement changed. Reopen it before saving."}
        append(current,batch,current.lines.filter {it.importId==id},categories,keepOverlaps,coverageConfirmed,voided,revision)
    }
    private suspend fun append(current:StatementBook,batch:StatementImport,lines:List<StatementLine>,categories:Map<Int,SpendingCategory>,keepOverlaps:Set<Int>,complete:Boolean,voided:Boolean,previous:Int) {
        require(categories.keys==lines.map {it.row.index}.toSet()){"Review decisions are incomplete."}
        val overlaps=current.overlaps(batch.kind,batch.sourceName,lines.map {it.row},batch.id)
        require(voided || overlaps.all {categories[it]==SpendingCategory.DUPLICATE || categories[it]==SpendingCategory.EXCLUDED || it in keepOverlaps}){"Review overlapping rows before saving. Equal transactions may be legitimate repeats."}
        val review=StatementReview(UUID.randomUUID(),batch.id,previous+1,voided,complete,clock.instant())
        val decisions=lines.map {StatementDecision(review.id,it.id,categories.getValue(it.row.index),it.row.index in keepOverlaps)}
        val combined=StatementBook(if(previous==0)current.imports+batch else current.imports,if(previous==0)current.lines+lines else current.lines,current.reviews+review,current.decisions+decisions)
        StatementRules.validate(combined)
        val dao=db.statementDao()
        if(previous==0) {
            dao.insert(StatementImportEntity(batch.id.toString(),batch.kind.name,batch.sourceName,batch.fileHash,batch.month.toString(),batch.firstCovered.toString(),batch.lastCovered.toString(),batch.parserVersion,batch.createdAt.toString(),batch.periodStart?.toString(),batch.periodEnd?.toString(),batch.currency.name))
            dao.insertLines(lines.map {val r=it.row;StatementLineEntity(it.id.toString(),batch.id.toString(),r.index,r.date.toString(),r.valueDate?.toString(),r.description,r.reference,r.amount.toPlainString(),r.direction.name)})
        }
        dao.insert(StatementReviewEntity(review.id.toString(),batch.id.toString(),review.revision,review.voided,review.coverageConfirmed,review.recordedAt.toString()))
        dao.insertDecisions(decisions.map {StatementDecisionEntity(it.reviewId.toString(),it.lineId.toString(),it.category.name,it.overlapKept)})
        db.bankDao().insertAudit(AuditEntity(UUID.randomUUID().toString(),batch.id.toString(),if(voided)"VOID_STATEMENT" else "REVIEW_STATEMENT",clock.instant().toString(),previous.toString(),"Revision ${review.revision}; ${lines.size} rows; raw file not retained"))
    }
}

object StatementMigrations {
    val FROM_7_TO_8=object:Migration(7,8) {
        override fun migrate(db:SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `statement_imports` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `sourceName` TEXT NOT NULL, `fileHash` TEXT NOT NULL, `month` TEXT NOT NULL, `firstCovered` TEXT NOT NULL, `lastCovered` TEXT NOT NULL, `parserVersion` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, `periodStart` TEXT, `periodEnd` TEXT, `currency` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_statement_imports_fileHash_month` ON `statement_imports` (`fileHash`, `month`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `statement_lines` (`id` TEXT NOT NULL, `importId` TEXT NOT NULL, `sourceRow` INTEGER NOT NULL, `date` TEXT NOT NULL, `valueDate` TEXT, `description` TEXT NOT NULL, `reference` TEXT NOT NULL, `amount` TEXT NOT NULL, `direction` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`importId`) REFERENCES `statement_imports`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_statement_lines_importId_sourceRow` ON `statement_lines` (`importId`, `sourceRow`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `statement_reviews` (`id` TEXT NOT NULL, `importId` TEXT NOT NULL, `revision` INTEGER NOT NULL, `voided` INTEGER NOT NULL, `coverageConfirmed` INTEGER NOT NULL, `recordedAt` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`importId`) REFERENCES `statement_imports`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_statement_reviews_importId_revision` ON `statement_reviews` (`importId`, `revision`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `statement_decisions` (`reviewId` TEXT NOT NULL, `lineId` TEXT NOT NULL, `category` TEXT NOT NULL, `overlapKept` INTEGER NOT NULL, PRIMARY KEY(`reviewId`, `lineId`), FOREIGN KEY(`reviewId`) REFERENCES `statement_reviews`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`lineId`) REFERENCES `statement_lines`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_statement_decisions_lineId` ON `statement_decisions` (`lineId`)")
        }
    }
}
