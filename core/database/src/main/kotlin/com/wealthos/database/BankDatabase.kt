package com.wealthos.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wealthos.finance.*
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Entity(tableName = "bank_accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val institution: String,
    val currency: String,
    val kind: String,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(tableName = "balance_observations", foreignKeys = [ForeignKey(
    entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT
)], indices = [Index(value = ["accountId", "effectiveDate", "revision"], unique = true)])
data class ObservationEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val amount: String,
    val currency: String,
    val effectiveDate: String,
    val recordedAt: String,
    val revision: Int,
    @ColumnInfo(defaultValue = "''") val note: String = "",
)

@Entity(tableName = "audit_events", indices = [Index("entityId")])
data class AuditEntity(
    @PrimaryKey val id: String,
    val entityId: String,
    val action: String,
    val recordedAt: String,
    val beforeValue: String?,
    val afterValue: String,
)

data class AccountWithBalances(
    @Embedded val account: AccountEntity,
    @Relation(parentColumn = "id", entityColumn = "accountId") val observations: List<ObservationEntity>,
)

@Dao
interface BankDao {
    @Transaction @Query("SELECT * FROM bank_accounts ORDER BY name COLLATE NOCASE, id")
    fun observe(): Flow<List<AccountWithBalances>>
    @Transaction @Query("SELECT * FROM bank_accounts ORDER BY id")
    suspend fun read(): List<AccountWithBalances>
    @Query("SELECT * FROM bank_accounts WHERE id = :id") suspend fun account(id: String): AccountEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAccount(account: AccountEntity)
    @Update suspend fun updateAccount(account: AccountEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertObservation(observation: ObservationEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAudit(event: AuditEntity)
    @Query("SELECT COALESCE(MAX(revision), 0) FROM balance_observations WHERE accountId = :id AND effectiveDate = :date")
    suspend fun revision(id: String, date: String): Int
    @Query("SELECT * FROM audit_events ORDER BY recordedAt, id") suspend fun audits(): List<AuditEntity>
}

@Database(entities = [AccountEntity::class, ObservationEntity::class, AuditEntity::class, InstrumentEntity::class,
    EntryEntity::class, ValuationEntity::class, ContractEntity::class, ContractEventEntity::class, CashEntity::class,
    TransferEntity::class, FxEntity::class, ReferenceEntity::class,IncomeSourceEntity::class,IncomeRateEntity::class,IncomeRecordEntity::class,
    CashFlowEntity::class,EmergencyTargetEntity::class,AllocationEntity::class,PolicyEntity::class,TermsEntity::class,SettlementEntity::class,
    ScenarioEntity::class,ForecastAssetEntity::class,ForecastFxEntity::class,WithdrawalEntity::class,GoalEntity::class,
    PublicSettingsEntity::class,PublicObservationEntity::class,NavBindingEntity::class,PublicRunEntity::class,PublicApplicationEntity::class], version = 7, exportSchema = true)
abstract class BankDatabase : RoomDatabase() {
    abstract fun bankDao(): BankDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun householdDao():HouseholdDao
    abstract fun planningDao():PlanningDao
    abstract fun publicDao():PublicDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE balance_observations ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }
        fun open(context: Context, passphrase: ByteArray, name: String = "wealthos.db"): BankDatabase {
            require(passphrase.size >= 32) { "A strong database key is required." }
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, BankDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(MIGRATION_1_2, PortfolioMigrations.FROM_2_TO_3, HouseholdMigrations.FROM_3_TO_4,PlanningMigrations.FROM_4_TO_5,PublicMigrations.FROM_5_TO_6,SnapshotMigrations.FROM_6_TO_7)
                .build()
        }
    }
}

class RoomBankRepository(private val db: BankDatabase, private val clock: Clock = Clock.systemDefaultZone()) : BankRepository {
    private val dao = db.bankDao()
    override fun observe(): Flow<BankBook> = db.invalidationTracker.createFlow("bank_accounts", "balance_observations",
        "instruments", "investment_entries", "asset_valuations", "financial_contracts", "contract_events", "cash_movements",
        "fx_observations", "statement_references","income_sources","income_rates","income_records","monthly_cashflows","emergency_targets",
        "emergency_allocations","insurance_policies","insurance_terms","premium_settlements","forecast_scenarios","forecast_assets","forecast_fx","forecast_withdrawals","financial_goals",
        "public_settings","public_observations","nav_bindings","public_refresh_runs","public_applications").map { db.withTransaction { db.readBook() } }

    override suspend fun addAccount(name: String, institution: String, currency: CurrencyCode, kind: AccountKind,
                                    amount: BigDecimal, date: LocalDate): UUID {
        val now = clock.instant()
        val account = BankAccount(UUID.randomUUID(), name.trim(), institution.trim(), currency, kind, now)
        validate(amount, currency, date, "")
        db.withTransaction {
            dao.insertAccount(account.entity())
            audit(account.id, "CREATE_ACCOUNT", null, account.entity().toString(), now)
            insertBalance(account, amount, date, "Opening balance observation", now)
        }
        return account.id
    }

    override suspend fun editAccount(id: UUID, name: String, institution: String, kind: AccountKind) {
        db.withTransaction {
            val old = requireNotNull(dao.account(id.toString())) { "Account no longer exists." }
            if(old.kind==AccountKind.BROKER_CASH.name && kind!=AccountKind.BROKER_CASH) {
                val portfolio=db.readBook().portfolio
                require(portfolio.instruments.none {it.custodyAccountId==id} && portfolio.references.none {it.accountId==id}) {"A custody account with linked holdings/references must remain brokerage cash."}
            }
            val updated = old.domain().copy(name = name.trim(), institution = institution.trim(), kind = kind, updatedAt = clock.instant())
            dao.updateAccount(updated.entity())
            audit(id, "EDIT_ACCOUNT", old.toString(), updated.entity().toString(), clock.instant())
        }
    }

    override suspend fun recordBalance(accountId: UUID, amount: BigDecimal, date: LocalDate, note: String) {
        db.withTransaction {
            val account = requireNotNull(dao.account(accountId.toString())) { "Account no longer exists." }.domain()
            validate(amount, account.currency, date, note)
            val revision = dao.revision(accountId.toString(), date.toString())
            require(revision == 0 || note.isNotBlank()) { "Explain the same-day correction; earlier revisions are retained." }
            insertBalance(account, amount, date, note.trim(), clock.instant())
        }
    }

    private fun validate(amount: BigDecimal, currency: CurrencyCode, date: LocalDate, note: String) {
        Amounts.parse(amount.toPlainString(), currency)
        require(!date.isAfter(LocalDate.now(clock))) { "Balance observations cannot be future-dated." }
        require(date.year in 1900..9999) { "Use a date from 1900 onwards." }
        require(note.length <= 500) { "Notes must be at most 500 characters." }
    }
    private suspend fun insertBalance(account: BankAccount, amount: BigDecimal, date: LocalDate, note: String, now: Instant) {
        val observation = ObservationEntity(UUID.randomUUID().toString(), account.id.toString(),
            Amounts.parse(amount.toPlainString(), account.currency).toPlainString(), account.currency.name,
            date.toString(), now.toString(), dao.revision(account.id.toString(), date.toString()) + 1, note)
        dao.insertObservation(observation)
        audit(UUID.fromString(observation.id), "OBSERVE_BALANCE", null, observation.toString(), now)
    }
    private suspend fun audit(id: UUID, action: String, before: String?, after: String, now: Instant) =
        dao.insertAudit(AuditEntity(UUID.randomUUID().toString(), id.toString(), action, now.toString(), before, after))
}

internal fun AccountEntity.domain() = BankAccount(UUID.fromString(id), name, institution, CurrencyCode.valueOf(currency),
    AccountKind.valueOf(kind), Instant.parse(createdAt), Instant.parse(updatedAt))
internal fun BankAccount.entity() = AccountEntity(id.toString(), name, institution, currency.name, kind.name, createdAt.toString(), updatedAt.toString())
internal fun ObservationEntity.domain() = BalanceObservation(UUID.fromString(id), UUID.fromString(accountId),
    amount.toBigDecimal(), CurrencyCode.valueOf(currency), LocalDate.parse(effectiveDate), Instant.parse(recordedAt), revision, note)
