package com.wealthos.database

import androidx.room.withTransaction
import com.wealthos.finance.*
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class RoomPlanningRepository(private val db:BankDatabase,private val clock:Clock=Clock.systemDefaultZone()):PlanningRepository {
    private val dao=db.planningDao()
    override suspend fun saveScenario(scenario:ForecastScenario,assets:List<AssetAssumption>,fx:List<FxAssumption>,withdrawals:List<PlannedWithdrawal>):Unit=db.withTransaction {
        Forecasts.validate(scenario);require(scenario.asOf<=LocalDate.now(clock)) {"Use today or an earlier date with known starting valuations."}
        listOf(scenario.monthlyContribution,scenario.salary,scenario.essential,scenario.discretionary,scenario.premiums).forEach {Amounts.parse(it.toPlainString(),CurrencyCode.INR)}
        listOf(scenario.inflation,scenario.cashReturn,scenario.investmentReturn,scenario.stepUp,scenario.salaryGrowth,scenario.expenseInflation).forEach {rate(it)}
        val revision=(dao.scenarios().filter {it.scenarioKey==scenario.key.toString()}.maxOfOrNull {it.revision} ?: 0)+1
        explanation(scenario.note,revision)
        require(assets.map {it.instrumentId}.distinct().size==assets.size && fx.map {it.currency}.distinct().size==fx.size) {"Each asset/currency needs one assumption per scenario revision."}
        val instruments=db.portfolioDao().instruments().map {it.domain()}.associateBy {it.id}
        assets.forEach {a->require(a.scenarioId==scenario.id);rate(a.annualReturn);val i=requireNotNull(instruments[a.instrumentId]);FinancialMath.nonnegative(a.monthlyAmount);Amounts.parse(a.monthlyAmount.toPlainString(),i.currency)
            require(i.category!=AssetCategory.FD || a.monthlyAmount.signum()==0) {"FD contracts do not accept recurring planned installments."}}
        fx.forEach {require(it.scenarioId==scenario.id && it.currency!=CurrencyCode.INR);rate(it.annualChange)}
        require(withdrawals.size<=200)
        withdrawals.forEach {w->require(w.scenarioId==scenario.id && w.month in 1..scenario.months && w.label.isNotBlank() && w.label.length<=80)
            val i=w.instrumentId?.let {requireNotNull(instruments[it])};require(i==null || i.tracking !in listOf(TrackingMode.LOAN,TrackingMode.DEPOSIT)) {"Withdraw from cash or a non-contract asset."}
            FinancialMath.nonnegative(w.amount);Amounts.parse(w.amount.toPlainString(),i?.currency ?: CurrencyCode.INR);require(w.amount>BigDecimal.ZERO)}
        val actual=scenario.copy(name=scenario.name.trim(),revision=revision,recordedAt=clock.instant());dao.insert(actual.entity())
        assets.forEach {dao.insert(ForecastAssetEntity(actual.id.toString(),it.instrumentId.toString(),it.annualReturn.toPlainString(),it.monthlyAmount.toPlainString()))}
        fx.forEach {dao.insert(ForecastFxEntity(actual.id.toString(),it.currency.name,it.annualChange.toPlainString()))}
        withdrawals.forEach {dao.insert(WithdrawalEntity(it.id.toString(),actual.id.toString(),it.month,it.instrumentId?.toString(),it.amount.toPlainString(),it.label.trim()))}
        audit(actual.id,"FORECAST_SCENARIO",actual.toString()+assets.toString()+fx.toString()+withdrawals.toString())
    }
    override suspend fun saveGoal(goal:FinancialGoal):Unit=db.withTransaction {
        require(goal.name.isNotBlank() && goal.name.length<=80 && goal.due.year>=1900 && goal.due<=LocalDate.now(clock).plusYears(100))
        require(goal.basisDate.year>=1900 && goal.basisDate<=LocalDate.now(clock) && goal.due<=goal.basisDate.plusYears(100)) {"Use a known reference date and a target within its 100-year horizon."}
        FinancialMath.nonnegative(goal.target);require(goal.target>BigDecimal.ZERO);FinancialMath.nonnegative(goal.allocated)
        Amounts.parse(goal.target.toPlainString(),goal.currency);Amounts.parse(goal.allocated.toPlainString(),goal.currency);rate(goal.annualReturn);rate(goal.inflation)
        val old=dao.goals().filter {it.goalKey==goal.key.toString()};require(old.all {it.currency==goal.currency.name}) {"Goal currency is immutable; create a separate goal to change currency."}
        val revision=(old.maxOfOrNull {it.revision} ?: 0)+1;explanation(goal.note,revision)
        val actual=goal.copy(name=goal.name.trim(),revision=revision,recordedAt=clock.instant());dao.insert(actual.entity());audit(actual.id,"FINANCIAL_GOAL",actual.toString())
    }
    private fun rate(value:BigDecimal){DecimalInput.parse(value.toPlainString());DecimalMath.rate(value)}
    private fun explanation(note:String,revision:Int){require(note.length<=500 && (revision==1 || note.isNotBlank())) {"Explain changes to this saved plan (up to 500 characters)."}}
    private suspend fun audit(id:UUID,action:String,after:String)=db.bankDao().insertAudit(AuditEntity(UUID.randomUUID().toString(),id.toString(),action,clock.instant().toString(),null,after))
}
