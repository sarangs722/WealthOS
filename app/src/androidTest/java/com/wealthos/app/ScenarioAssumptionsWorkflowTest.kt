package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModelProvider
import com.wealthos.finance.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.Instant
import java.util.UUID

class ScenarioAssumptionsWorkflowTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun state()=ViewModelProvider(rule.activity)[BankViewModel::class.java].state.value
    private fun write(label:String,value:String){rule.activity.dismissKeyboardForTest();rule.onNodeWithText(label).performScrollTo().performTextReplacement(value);rule.activity.dismissKeyboardForTest()}
    private fun value(label:String,value:String)=rule.onNodeWithText(label).performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(value)))
    @Test fun bulkReturnIsExplicitAndPreservesOtherAssetAssumptions(){
        rule.waitForHome();val day=LocalDate.now();val suffix=System.nanoTime()
        val fund=Instrument(UUID.randomUUID(),"Fictional batch fund $suffix",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.OBSERVED,day,Instant.now())
        val property=Instrument(UUID.randomUUID(),"Fictional batch property $suffix",AssetCategory.PROPERTY,CurrencyCode.INR,TrackingMode.OBSERVED,day,Instant.now())
        val deposit=Instrument(UUID.randomUUID(),"Fictional batch FD $suffix",AssetCategory.FD,CurrencyCode.INR,TrackingMode.DEPOSIT,day,Instant.now())
        rule.activityRule.scenario.onActivity {activity->ViewModelProvider(activity)[BankViewModel::class.java].portfolio({}) {repo->
            repo.addInstrument(NewInstrument(fund,initialValue="1000".toBigDecimal()))
            repo.addInstrument(NewInstrument(property,initialValue="10000".toBigDecimal()))
            repo.addInstrument(NewInstrument(deposit,contract=FinancialContract(deposit.id,"1000".toBigDecimal(),"0.07".toBigDecimal(),day,day.plusYears(1))))
        }}
        rule.waitUntil(30_000){state().book.portfolio.instruments.any {it.id==deposit.id}}
        val before=WealthValuation.at(state().book,day)
        rule.onNodeWithTag("nav-2").performClick()
        rule.waitUntil(30_000){rule.onAllNodesWithTag("forecast-list").fetchSemanticsNodes().isNotEmpty()}
        val add=if(state().book.planning.scenarios.isEmpty())"Create a scenario" else "New scenario"
        rule.onNodeWithTag("forecast-list").performScrollToNode(hasText(add));rule.onNodeWithText(add).performClick()
        val name="Fictional batch scenario $suffix";write("Scenario name",name);write("Planned investment return % per year","5")
        rule.onNodeWithText("Asset-specific assumptions").performScrollTo().performClick()
        value("${fund.name}: annual return %","0")
        write("${property.name}: annual return %","3");write("${deposit.name}: annual return %","4")
        rule.onNodeWithText("Apply this return to current investments").performScrollTo().performClick();value("${fund.name}: annual return %","5")
        write("Planned investment return % per year","12");value("${fund.name}: annual return %","5")
        rule.onNodeWithText("Apply this return to current investments").performScrollTo().performClick()
        value("${property.name}: annual return %","3");value("${deposit.name}: annual return %","4")
        rule.onNodeWithText("Save scenario").performClick();rule.waitUntil(30_000){state().book.planning.scenarios.any {it.name==name}}
        rule.activityRule.scenario.recreate();rule.waitUntil(30_000){state().book.planning.scenarios.any {it.name==name}}
        val book=state().book;val saved=book.planning.scenarios.single {it.name==name};val assumptions=book.planning.assets.filter {it.scenarioId==saved.id}
        assertEquals(0,"0.12".toBigDecimal().compareTo(assumptions.single {it.instrumentId==fund.id}.annualReturn))
        assertEquals(0,"0.03".toBigDecimal().compareTo(assumptions.single {it.instrumentId==property.id}.annualReturn))
        assertEquals(0,"0.04".toBigDecimal().compareTo(assumptions.single {it.instrumentId==deposit.id}.annualReturn))
        assertEquals(0,"0.07".toBigDecimal().compareTo(book.portfolio.contracts.single {it.instrumentId==deposit.id}.annualRate))
        val after=WealthValuation.at(book,day);assertEquals(before.netWorthInr,after.netWorthInr);assertEquals(0,before.knownInrSubtotal.compareTo(after.knownInrSubtotal))
    }
}
