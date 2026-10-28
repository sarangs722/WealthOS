package com.wealthos.finance

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class ForecastMode(val label:String){CONTRIBUTION("Contribution-based"),CASH_FLOW("Cash-flow-based")}
enum class ScenarioKind(val label:String){PESSIMISTIC("Pessimistic"),BASE("Base"),OPTIMISTIC("Optimistic")}
data class ForecastScenario(val id:UUID,val key:UUID,val name:String,val kind:ScenarioKind,val mode:ForecastMode,val asOf:LocalDate,val months:Int,
    val inflation:BigDecimal,val cashReturn:BigDecimal,val investmentReturn:BigDecimal,val monthlyContribution:BigDecimal,val stepUp:BigDecimal,
    val salary:BigDecimal,val salaryGrowth:BigDecimal,val essential:BigDecimal,val discretionary:BigDecimal,val expenseInflation:BigDecimal,
    val premiums:BigDecimal,val reinvestDeposits:Boolean,val includeGoals:Boolean,val revision:Int,val recordedAt:Instant,val note:String)
data class AssetAssumption(val scenarioId:UUID,val instrumentId:UUID,val annualReturn:BigDecimal,val monthlyAmount:BigDecimal)
data class FxAssumption(val scenarioId:UUID,val currency:CurrencyCode,val annualChange:BigDecimal)
/** Cash source is aggregate INR bank cash; a named instrument withdrawal is denominated in its native currency. */
data class PlannedWithdrawal(val id:UUID,val scenarioId:UUID,val month:Int,val instrumentId:UUID?,val amount:BigDecimal,val label:String)
data class FinancialGoal(val id:UUID,val key:UUID,val name:String,val currency:CurrencyCode,val target:BigDecimal,val due:LocalDate,
    val targetInTodayMoney:Boolean,val allocated:BigDecimal,val annualReturn:BigDecimal,val inflation:BigDecimal,val revision:Int,val recordedAt:Instant,val note:String,
    val basisDate:LocalDate=recordedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate(),val active:Boolean=true)
data class PlanningBook(val scenarios:List<ForecastScenario> = emptyList(),val assets:List<AssetAssumption> = emptyList(),val fx:List<FxAssumption> = emptyList(),
    val withdrawals:List<PlannedWithdrawal> = emptyList(),val goals:List<FinancialGoal> = emptyList()) {
    fun latestScenarios()=scenarios.groupBy {it.key}.values.map {it.maxBy {r->r.revision}}.sortedBy {it.kind.ordinal}
    fun latestGoals()=goals.groupBy {it.key}.values.map {it.maxBy {r->r.revision}}
}
interface PlanningRepository {
    suspend fun saveScenario(scenario:ForecastScenario,assets:List<AssetAssumption>,fx:List<FxAssumption>,withdrawals:List<PlannedWithdrawal>)
    suspend fun saveGoal(goal:FinancialGoal)
}
data class GoalResult(val targetFuture:BigDecimal,val existingFuture:BigDecimal,val monthlyRequired:BigDecimal?,val months:Int,val status:String)
object Goals {
    fun at(goal:FinancialGoal,date:LocalDate):GoalResult {
        val months=java.time.temporal.ChronoUnit.MONTHS.between(java.time.YearMonth.from(date),java.time.YearMonth.from(goal.due)).toInt()
        val inflationMonths=java.time.temporal.ChronoUnit.MONTHS.between(java.time.YearMonth.from(goal.basisDate),java.time.YearMonth.from(goal.due)).toInt().coerceAtLeast(0)
        val target=if(goal.targetInTodayMoney)FinancialMath.growth(goal.target,goal.inflation,inflationMonths) else goal.target
        if(!goal.active)return GoalResult(target,goal.allocated,null,months.coerceAtLeast(0),"Inactive / completed goal: no new contribution or forecast withdrawal is planned. Existing records and wealth are unchanged.")
        if(goal.due<=date || months<=0)return GoalResult(target,goal.allocated,null,0,"Due now / within this month: monthly annuity planning unavailable; compare target and declared earmark directly.")
        require(months<=1200)
        val existing=FinancialMath.growth(goal.allocated,goal.annualReturn,months)
        return GoalResult(target,existing,FinancialMath.requiredMonthly(target,goal.allocated,goal.annualReturn,months),months,"End-of-month contributions; full steps to target month. Earmark is a declared input as of ${goal.basisDate}, held unchanged until reviewed; no past investment progress is invented.")
    }
}
