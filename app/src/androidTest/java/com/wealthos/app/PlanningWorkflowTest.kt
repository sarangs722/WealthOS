package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PlanningWorkflowTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun waitFor(text:String){rule.waitUntil(45_000){
        if(text=="Estimated outcome" && rule.onAllNodesWithTag("scenario-detail").fetchSemanticsNodes().isNotEmpty()) {
            // Await the exact calculated result while bringing its lazy item into view.
            // A missing result still fails at the existing deadline.
            try{rule.onNodeWithTag("scenario-detail").performScrollToNode(hasText(text))}catch(_:AssertionError){}
        }
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }}
    private fun write(label:String,value:String){rule.activity.dismissKeyboardForTest();rule.onNodeWithText(label).performScrollTo().performTextReplacement(value);rule.activity.dismissKeyboardForTest()}
    @Test fun independentScenarioAndGoalRevisionsPersist(){
        val suffix=System.currentTimeMillis();val name="UI base plan $suffix";val goal="UI goal $suffix"
        rule.waitForHome();rule.onNodeWithText("Forecast",useUnmergedTree=true).performClick();rule.onNodeWithText("Create a scenario").performClick()
        write("Scenario name",name);write("Monthly new investment in INR","100")
        rule.onNodeWithText("Horizon: 10 years").performScrollTo().performClick();rule.onNodeWithText("5 years").performClick()
        rule.onNodeWithText("Save scenario").performClick();waitFor("Estimated outcome")
        rule.onNodeWithTag("scenario-detail").performScrollToNode(hasText("Saved assumptions"));rule.onNodeWithText("Saved assumptions").performClick();rule.onNode(hasText("Monthly new investments ₹100.00",substring=true)).assertExists()
        rule.activityRule.scenario.recreate();waitFor("Estimated outcome")
        rule.onNodeWithTag("scenario-detail").performScrollToNode(hasText("Revise scenario"));rule.onNodeWithText("Revise scenario").performClick()
        write("Scenario name","$name revised");write("Scenario note / revision explanation","Confirm unchanged return and revise nickname")
        rule.onNodeWithText("Save scenario").performClick();waitFor("Estimated outcome")
        rule.onNodeWithTag("scenario-detail").performScrollToNode(hasText("Retained scenario revisions"));rule.onNodeWithText("Revision 1").assertExists()
        rule.onNodeWithContentDescription("Back").performClick();rule.onNodeWithText("Financial goals").performClick();rule.onNodeWithText("New goal").performClick()
        write("Goal name",goal);write("Target amount in INR","12000");rule.chooseEntryDate("Target date",LocalDate.now().plusYears(1))
        rule.onNodeWithContentDescription("Target is stated in today's money").performScrollTo().performClick()
        rule.onNodeWithText("Save goal").performClick();waitFor("Required monthly contribution ₹1,000.00")
        rule.onNodeWithText(goal).assertExists();rule.onNodeWithText("Revise goal").performScrollTo().performClick()
        write("Target amount in INR","24000");write("Goal note / revision explanation","Target changed with retained history")
        rule.onNodeWithText("Save goal").performClick();waitFor("Required monthly contribution ₹2,000.00")
    }
}
