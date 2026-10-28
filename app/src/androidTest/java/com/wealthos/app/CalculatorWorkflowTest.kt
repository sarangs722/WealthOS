package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import org.junit.Rule
import org.junit.Test

class CalculatorWorkflowTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun waitFor(text:String){try{rule.waitUntil(45_000){rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}catch(error:Throwable){throw AssertionError("Expected: $text\n"+runCatching{rule.onRoot(useUnmergedTree=true).printToString()}.getOrDefault("Semantics unavailable"),error)}}
    private fun write(label:String,value:String){
        rule.activity.dismissKeyboardForTest()
        rule.onNodeWithText(label).performScrollTo().performTextReplacement(value)
        rule.onNodeWithText(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(value)))
        rule.activity.dismissKeyboardForTest()
        rule.onNodeWithText(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(value)))
    }
    @Test fun sipZeroInterestAndChangedInputsDoNotDisplayStaleOutput(){
        rule.waitForHome();rule.onNodeWithText("Tools",useUnmergedTree=true).performClick()
        rule.onNodeWithTag("tools-list").performScrollToNode(hasText("SIP"));rule.onNodeWithText("SIP").performClick()
        write("Monthly contribution in INR","1000")
        write("Effective annual return %","0")
        write("Duration in months","12")
        rule.onNodeWithText("Calculate").assertIsDisplayed().performClick();waitFor("Estimated maturity: ₹12,000.00")
        rule.onNodeWithText("Estimated gain: ₹0.00").assertExists()
        write("Monthly contribution in INR","2000")
        rule.onNodeWithText("Estimated maturity: ₹12,000.00").assertDoesNotExist()
        rule.onNodeWithText("Calculate").assertIsDisplayed().performClick();waitFor("Estimated maturity: ₹24,000.00")
        rule.activityRule.scenario.recreate();rule.onNodeWithText("Monthly contribution in INR").performScrollTo().assertTextContains("2000")
    }
}
