package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.wealthos.finance.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuickEntryWorkflowTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun state()=ViewModelProvider(rule.activity)[BankViewModel::class.java].state.value
    private fun write(label:String,value:String){rule.activity.dismissKeyboardForTest();rule.onNodeWithText(label).performScrollTo().performTextReplacement(value);rule.activity.dismissKeyboardForTest()}
    private fun start(name:String){rule.waitForHome();rule.onNodeWithTag("nav-1").performClick();rule.addInvestment();write("Fund name",name)}
    private fun detailClick(text:String){rule.onNode(hasScrollAction(),useUnmergedTree=true).performScrollToNode(hasText(text));rule.onNodeWithText(text).performClick()}
    @Test fun twoFieldFundEntryPersistsAndCorrectionKeepsBothVersions(){
        val name="Fictional quick fund ${System.nanoTime()}";start(name)
        rule.onNodeWithText("Opening units").assertDoesNotExist();write("Current value in INR","1234.56")
        rule.onNodeWithText("Save holding").assertIsDisplayed().performClick()
        rule.waitUntil(30_000){state().book.portfolio.instruments.any {it.name==name}}
        val id=state().book.portfolio.instruments.single {it.name==name}.id
        rule.activityRule.scenario.recreate();rule.waitUntil(30_000){rule.onAllNodesWithText("Current recorded value").fetchSemanticsNodes().isNotEmpty()}
        assertTrue(state().book.portfolio.entries.none {it.instrumentId==id});assertNull(state().book.portfolio.valuations.single {it.instrumentId==id}.reportedInvested)
        detailClick("Update value");write("Current value in INR","1300.00")
        rule.onNodeWithText("Save value").performClick();rule.waitUntil(30_000){state().formError!=null}
        write("Correction reason","Fictional corrected broker value");rule.onNodeWithText("Save value").performClick()
        rule.waitUntil(30_000){state().book.portfolio.valuations.count {it.instrumentId==id}==2}
        val versions=state().book.portfolio.valuations.filter {it.instrumentId==id}.sortedBy {it.revision}
        assertEquals(listOf("1234.56","1300.00"),versions.map {it.value.toPlainString()})
    }
    @Test fun unitsAndNavAlternativeUsesExactDecimalAndOptionalInvestment(){
        val name="Fictional NAV entry ${System.nanoTime()}";start(name)
        rule.onNodeWithText("Units & price").performClick();write("Units held","12.345678");write("NAV in INR","123.456789")
        write("Invested amount (optional)","1000.00");rule.onNodeWithText("Save holding").performClick()
        rule.waitUntil(30_000){state().book.portfolio.instruments.any {it.name==name}}
        val i=state().book.portfolio.instruments.single {it.name==name};val snapshot=state().book.portfolio.valuations.single {it.instrumentId==i.id}
        assertEquals("1524.16",snapshot.value.toPlainString());assertEquals("12.345678",snapshot.reportedUnits!!.toPlainString());assertTrue(snapshot.calculatedTotal)
        assertEquals("1000.00",snapshot.reportedInvested!!.toPlainString());assertEquals(TrackingMode.OBSERVED,i.tracking)
    }
    @Test fun detailedTrackingUpgradesSameHoldingAndPreservesUnknownCost(){
        val name="Fictional upgrade ${System.nanoTime()}";start(name);write("Current value in INR","1500.00")
        rule.onNodeWithText("Save holding").performClick();rule.waitUntil(30_000){state().book.portfolio.instruments.any {it.name==name}}
        val id=state().book.portfolio.instruments.single {it.name==name}.id
        detailClick("Track purchases & sales");write("Units held","10");write("NAV / unit price (optional)","150")
        rule.onNodeWithText("Start unit tracking").performClick();rule.waitUntil(30_000){state().book.portfolio.instruments.single {it.id==id}.tracking==TrackingMode.TRANSACTIONS}
        rule.activityRule.scenario.recreate();rule.waitUntil(30_000){state().book.portfolio.instruments.any {it.id==id}}
        val p=state().book.portfolio;val i=p.instruments.single {it.id==id};assertEquals(1,p.instruments.count {it.name==name})
        assertEquals(1,p.entries.count {it.instrumentId==id});assertNull(p.entries.single {it.instrumentId==id}.gross)
        assertTrue(p.cashMovements.none {it.sourceId==p.entries.single {e->e.instrumentId==id}.id})
        val value=WealthValuation.asset(i,p,java.time.LocalDate.now());assertEquals(0,"1500".toBigDecimal().compareTo(value.nativeValue!!));assertNull(value.unrealizedGain)
    }
}
