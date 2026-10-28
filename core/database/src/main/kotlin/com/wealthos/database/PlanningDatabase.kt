package com.wealthos.database

import androidx.room.*
import com.wealthos.finance.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity(tableName="forecast_scenarios",indices=[Index(value=["scenarioKey","revision"],unique=true)])
data class ScenarioEntity(@PrimaryKey val id:String,val scenarioKey:String,val name:String,val kind:String,val mode:String,val asOf:String,val months:Int,
    val inflation:String,val cashReturn:String,val investmentReturn:String,val monthlyContribution:String,val stepUp:String,val salary:String,val salaryGrowth:String,
    val essential:String,val discretionary:String,val expenseInflation:String,val premiums:String,val reinvestDeposits:Boolean,val includeGoals:Boolean,val revision:Int,val recordedAt:String,val note:String)
@Entity(tableName="forecast_assets",primaryKeys=["scenarioId","instrumentId"],foreignKeys=[ForeignKey(entity=ScenarioEntity::class,parentColumns=["id"],childColumns=["scenarioId"],onDelete=ForeignKey.RESTRICT),ForeignKey(entity=InstrumentEntity::class,parentColumns=["id"],childColumns=["instrumentId"],onDelete=ForeignKey.RESTRICT)],indices=[Index("instrumentId")])
data class ForecastAssetEntity(val scenarioId:String,val instrumentId:String,val annualReturn:String,val monthlyAmount:String)
@Entity(tableName="forecast_fx",primaryKeys=["scenarioId","currency"],foreignKeys=[ForeignKey(entity=ScenarioEntity::class,parentColumns=["id"],childColumns=["scenarioId"],onDelete=ForeignKey.RESTRICT)])
data class ForecastFxEntity(val scenarioId:String,val currency:String,val annualChange:String)
@Entity(tableName="forecast_withdrawals",foreignKeys=[ForeignKey(entity=ScenarioEntity::class,parentColumns=["id"],childColumns=["scenarioId"],onDelete=ForeignKey.RESTRICT),ForeignKey(entity=InstrumentEntity::class,parentColumns=["id"],childColumns=["instrumentId"],onDelete=ForeignKey.RESTRICT)],indices=[Index("scenarioId"),Index("instrumentId")])
data class WithdrawalEntity(@PrimaryKey val id:String,val scenarioId:String,val month:Int,val instrumentId:String?,val amount:String,val label:String)
@Entity(tableName="financial_goals",indices=[Index(value=["goalKey","revision"],unique=true)])
data class GoalEntity(@PrimaryKey val id:String,val goalKey:String,val name:String,val currency:String,val target:String,val due:String,val targetInTodayMoney:Boolean,
    val allocated:String,val annualReturn:String,val inflation:String,val revision:Int,val recordedAt:String,val note:String,val basisDate:String,val active:Boolean)

@Dao interface PlanningDao {
    @Query("SELECT * FROM forecast_scenarios ORDER BY scenarioKey, revision") suspend fun scenarios():List<ScenarioEntity>
    @Query("SELECT * FROM forecast_assets") suspend fun assets():List<ForecastAssetEntity>
    @Query("SELECT * FROM forecast_fx") suspend fun fx():List<ForecastFxEntity>
    @Query("SELECT * FROM forecast_withdrawals ORDER BY month, id") suspend fun withdrawals():List<WithdrawalEntity>
    @Query("SELECT * FROM financial_goals ORDER BY goalKey, revision") suspend fun goals():List<GoalEntity>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:ScenarioEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:ForecastAssetEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:ForecastFxEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:WithdrawalEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:GoalEntity)
}
internal fun ForecastScenario.entity()=ScenarioEntity(id.toString(),key.toString(),name,kind.name,mode.name,asOf.toString(),months,inflation.toPlainString(),cashReturn.toPlainString(),investmentReturn.toPlainString(),monthlyContribution.toPlainString(),stepUp.toPlainString(),salary.toPlainString(),salaryGrowth.toPlainString(),essential.toPlainString(),discretionary.toPlainString(),expenseInflation.toPlainString(),premiums.toPlainString(),reinvestDeposits,includeGoals,revision,recordedAt.toString(),note)
internal fun ScenarioEntity.domain()=ForecastScenario(UUID.fromString(id),UUID.fromString(scenarioKey),name,ScenarioKind.valueOf(kind),ForecastMode.valueOf(mode),LocalDate.parse(asOf),months,inflation.toBigDecimal(),cashReturn.toBigDecimal(),investmentReturn.toBigDecimal(),monthlyContribution.toBigDecimal(),stepUp.toBigDecimal(),salary.toBigDecimal(),salaryGrowth.toBigDecimal(),essential.toBigDecimal(),discretionary.toBigDecimal(),expenseInflation.toBigDecimal(),premiums.toBigDecimal(),reinvestDeposits,includeGoals,revision,Instant.parse(recordedAt),note)
internal fun FinancialGoal.entity()=GoalEntity(id.toString(),key.toString(),name,currency.name,target.toPlainString(),due.toString(),targetInTodayMoney,allocated.toPlainString(),annualReturn.toPlainString(),inflation.toPlainString(),revision,recordedAt.toString(),note,basisDate.toString(),active)
internal fun GoalEntity.domain()=FinancialGoal(UUID.fromString(id),UUID.fromString(goalKey),name,CurrencyCode.valueOf(currency),target.toBigDecimal(),LocalDate.parse(due),targetInTodayMoney,allocated.toBigDecimal(),annualReturn.toBigDecimal(),inflation.toBigDecimal(),revision,Instant.parse(recordedAt),note,LocalDate.parse(basisDate),active)
internal suspend fun PlanningDao.readPlanning()=PlanningBook(scenarios().map {it.domain()},assets().map {AssetAssumption(UUID.fromString(it.scenarioId),UUID.fromString(it.instrumentId),it.annualReturn.toBigDecimal(),it.monthlyAmount.toBigDecimal())},
    fx().map {FxAssumption(UUID.fromString(it.scenarioId),CurrencyCode.valueOf(it.currency),it.annualChange.toBigDecimal())},withdrawals().map {PlannedWithdrawal(UUID.fromString(it.id),UUID.fromString(it.scenarioId),it.month,it.instrumentId?.let(UUID::fromString),it.amount.toBigDecimal(),it.label)},goals().map {it.domain()})
