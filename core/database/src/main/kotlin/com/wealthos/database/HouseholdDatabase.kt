package com.wealthos.database

import androidx.room.*
import com.wealthos.finance.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Entity(tableName="income_sources")
data class IncomeSourceEntity(@PrimaryKey val id:String,val name:String,val kind:String,val currency:String,val startedOn:String,val endedOn:String?,val createdAt:String,val updatedAt:String)
@Entity(tableName="income_rates",foreignKeys=[ForeignKey(entity=IncomeSourceEntity::class,parentColumns=["id"],childColumns=["sourceId"],onDelete=ForeignKey.RESTRICT)],indices=[Index(value=["sourceId","effectiveFrom","revision"],unique=true)])
data class IncomeRateEntity(@PrimaryKey val id:String,val sourceId:String,val effectiveFrom:String,val monthlyNet:String,val revision:Int,val recordedAt:String,val note:String)
@Entity(tableName="income_records",foreignKeys=[ForeignKey(entity=IncomeSourceEntity::class,parentColumns=["id"],childColumns=["sourceId"],onDelete=ForeignKey.RESTRICT)],indices=[Index(value=["sourceId","month","revision"],unique=true)])
data class IncomeRecordEntity(@PrimaryKey val id:String,val sourceId:String,val month:String,val net:String,val gross:String?,val deductions:String?,val revision:Int,val recordedAt:String,val note:String)
@Entity(tableName="monthly_cashflows",indices=[Index(value=["month","currency","revision"],unique=true)])
data class CashFlowEntity(@PrimaryKey val id:String,val month:String,val currency:String,val incomeMode:String,val manualIncome:String?,val essential:String,
    val discretionary:String,val premiums:String,val loanPrincipal:String,val loanInterest:String,val otherOutflows:String,val investments:String,
    val incomeConfirmed:Boolean,val expensesConfirmed:Boolean,val revision:Int,val recordedAt:String,val note:String)
@Entity(tableName="emergency_targets",indices=[Index(value=["date","revision"],unique=true)])
data class EmergencyTargetEntity(@PrimaryKey val id:String,val date:String,val targetInr:String,val revision:Int,val recordedAt:String,val note:String)
@Entity(tableName="emergency_allocations",foreignKeys=[
    ForeignKey(entity=AccountEntity::class,parentColumns=["id"],childColumns=["accountId"],onDelete=ForeignKey.RESTRICT),
    ForeignKey(entity=InstrumentEntity::class,parentColumns=["id"],childColumns=["instrumentId"],onDelete=ForeignKey.RESTRICT)
],indices=[Index(value=["accountId","date","revision"],unique=true),Index(value=["instrumentId","date","revision"],unique=true)])
data class AllocationEntity(@PrimaryKey val id:String,val accountId:String?,val instrumentId:String?,val date:String,val nativeAmount:String,val revision:Int,val recordedAt:String,val note:String) {
    init {require((accountId==null)!=(instrumentId==null))}
}
@Entity(tableName="insurance_policies")
data class PolicyEntity(@PrimaryKey val id:String,val name:String,val insurer:String,val kind:String,val currency:String,val createdAt:String,val updatedAt:String)
@Entity(tableName="insurance_terms",foreignKeys=[ForeignKey(entity=PolicyEntity::class,parentColumns=["id"],childColumns=["policyId"],onDelete=ForeignKey.RESTRICT)],indices=[Index(value=["policyId","effectiveFrom","revision"],unique=true)])
data class TermsEntity(@PrimaryKey val id:String,val policyId:String,val effectiveFrom:String,val validFrom:String,val validUntil:String,val cover:String?,val premium:String?,
    val frequency:String,val anchorDue:String?,val scope:String,val active:Boolean,val revision:Int,val recordedAt:String,val note:String,val lastPremiumDue:String?)
@Entity(tableName="premium_settlements",foreignKeys=[ForeignKey(entity=PolicyEntity::class,parentColumns=["id"],childColumns=["policyId"],onDelete=ForeignKey.RESTRICT)],indices=[Index(value=["policyId","dueOn","revision"],unique=true)])
data class SettlementEntity(@PrimaryKey val id:String,val policyId:String,val dueOn:String,val paidOn:String,val amount:String,val revision:Int,val recordedAt:String,val note:String)

@Dao interface HouseholdDao {
    @Query("SELECT * FROM income_sources ORDER BY name COLLATE NOCASE, id") suspend fun sources():List<IncomeSourceEntity>
    @Query("SELECT * FROM income_rates") suspend fun rates():List<IncomeRateEntity>
    @Query("SELECT * FROM income_records") suspend fun income():List<IncomeRecordEntity>
    @Query("SELECT * FROM monthly_cashflows") suspend fun cashFlows():List<CashFlowEntity>
    @Query("SELECT * FROM emergency_targets") suspend fun targets():List<EmergencyTargetEntity>
    @Query("SELECT * FROM emergency_allocations") suspend fun allocations():List<AllocationEntity>
    @Query("SELECT * FROM insurance_policies ORDER BY name COLLATE NOCASE, id") suspend fun policies():List<PolicyEntity>
    @Query("SELECT * FROM insurance_terms") suspend fun terms():List<TermsEntity>
    @Query("SELECT * FROM premium_settlements") suspend fun settlements():List<SettlementEntity>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:IncomeSourceEntity)
    @Update suspend fun update(value:IncomeSourceEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:IncomeRateEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:IncomeRecordEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:CashFlowEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:EmergencyTargetEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:AllocationEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:PolicyEntity)
    @Update suspend fun update(value:PolicyEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:TermsEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:SettlementEntity)
}
internal fun IncomeSourceEntity.domain()=IncomeSource(UUID.fromString(id),name,IncomeKind.valueOf(kind),CurrencyCode.valueOf(currency),LocalDate.parse(startedOn),endedOn?.let(LocalDate::parse),Instant.parse(createdAt),Instant.parse(updatedAt))
internal fun IncomeSource.entity()=IncomeSourceEntity(id.toString(),name,kind.name,currency.name,startedOn.toString(),endedOn?.toString(),createdAt.toString(),updatedAt.toString())
internal fun IncomeRateEntity.domain()=IncomeRate(UUID.fromString(id),UUID.fromString(sourceId),LocalDate.parse(effectiveFrom),monthlyNet.toBigDecimal(),revision,Instant.parse(recordedAt),note)
internal fun IncomeRate.entity()=IncomeRateEntity(id.toString(),sourceId.toString(),effectiveFrom.toString(),monthlyNet.toPlainString(),revision,recordedAt.toString(),note)
internal fun IncomeRecordEntity.domain()=IncomeRecord(UUID.fromString(id),UUID.fromString(sourceId),YearMonth.parse(month),net.toBigDecimal(),gross?.toBigDecimal(),deductions?.toBigDecimal(),revision,Instant.parse(recordedAt),note)
internal fun IncomeRecord.entity()=IncomeRecordEntity(id.toString(),sourceId.toString(),month.toString(),net.toPlainString(),gross?.toPlainString(),deductions?.toPlainString(),revision,recordedAt.toString(),note)
internal fun CashFlowEntity.domain()=MonthlyCashFlow(UUID.fromString(id),YearMonth.parse(month),CurrencyCode.valueOf(currency),IncomeMode.valueOf(incomeMode),manualIncome?.toBigDecimal(),essential.toBigDecimal(),discretionary.toBigDecimal(),premiums.toBigDecimal(),loanPrincipal.toBigDecimal(),loanInterest.toBigDecimal(),otherOutflows.toBigDecimal(),investments.toBigDecimal(),incomeConfirmed,expensesConfirmed,revision,Instant.parse(recordedAt),note)
internal fun MonthlyCashFlow.entity()=CashFlowEntity(id.toString(),month.toString(),currency.name,incomeMode.name,manualIncome?.toPlainString(),essential.toPlainString(),discretionary.toPlainString(),premiums.toPlainString(),loanPrincipal.toPlainString(),loanInterest.toPlainString(),otherOutflows.toPlainString(),investments.toPlainString(),incomeConfirmed,expensesConfirmed,revision,recordedAt.toString(),note)
internal fun EmergencyTargetEntity.domain()=EmergencyTarget(UUID.fromString(id),LocalDate.parse(date),targetInr.toBigDecimal(),revision,Instant.parse(recordedAt),note)
internal fun EmergencyTarget.entity()=EmergencyTargetEntity(id.toString(),date.toString(),targetInr.toPlainString(),revision,recordedAt.toString(),note)
internal fun AllocationEntity.domain()=EmergencyAllocation(UUID.fromString(id),accountId?.let(UUID::fromString),instrumentId?.let(UUID::fromString),LocalDate.parse(date),nativeAmount.toBigDecimal(),revision,Instant.parse(recordedAt),note)
internal fun EmergencyAllocation.entity()=AllocationEntity(id.toString(),accountId?.toString(),instrumentId?.toString(),date.toString(),nativeAmount.toPlainString(),revision,recordedAt.toString(),note)
internal fun PolicyEntity.domain()=InsurancePolicy(UUID.fromString(id),name,insurer,PolicyKind.valueOf(kind),CurrencyCode.valueOf(currency),Instant.parse(createdAt),Instant.parse(updatedAt))
internal fun InsurancePolicy.entity()=PolicyEntity(id.toString(),name,insurer,kind.name,currency.name,createdAt.toString(),updatedAt.toString())
internal fun TermsEntity.domain()=InsuranceTerms(UUID.fromString(id),UUID.fromString(policyId),LocalDate.parse(effectiveFrom),LocalDate.parse(validFrom),LocalDate.parse(validUntil),cover?.toBigDecimal(),premium?.toBigDecimal(),PremiumFrequency.valueOf(frequency),anchorDue?.let(LocalDate::parse),CoverageScope.valueOf(scope),active,revision,Instant.parse(recordedAt),note,lastPremiumDue?.let(LocalDate::parse))
internal fun InsuranceTerms.entity()=TermsEntity(id.toString(),policyId.toString(),effectiveFrom.toString(),validFrom.toString(),validUntil.toString(),cover?.toPlainString(),premium?.toPlainString(),frequency.name,anchorDue?.toString(),scope.name,active,revision,recordedAt.toString(),note,lastPremiumDue?.toString())
internal fun SettlementEntity.domain()=PremiumSettlement(UUID.fromString(id),UUID.fromString(policyId),LocalDate.parse(dueOn),LocalDate.parse(paidOn),amount.toBigDecimal(),revision,Instant.parse(recordedAt),note)
internal fun PremiumSettlement.entity()=SettlementEntity(id.toString(),policyId.toString(),dueOn.toString(),paidOn.toString(),amount.toPlainString(),revision,recordedAt.toString(),note)
internal suspend fun HouseholdDao.readHousehold()=HouseholdBook(sources().map {it.domain()},rates().map {it.domain()},income().map {it.domain()},cashFlows().map {it.domain()},targets().map {it.domain()},allocations().map {it.domain()},policies().map {it.domain()},terms().map {it.domain()},settlements().map {it.domain()})
