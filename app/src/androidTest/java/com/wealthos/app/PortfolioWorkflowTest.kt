package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortfolioWorkflowTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun waitFor(text:String) {rule.waitUntil(30_000) {rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun write(label:String,value:String) {rule.activity.dismissKeyboardForTest();rule.onNodeWithText(label).performScrollTo().performTextReplacement(value);rule.activity.dismissKeyboardForTest()}
    private fun detailText(text:String) {
        rule.waitUntil(30_000) {
            try{rule.onNode(hasScrollAction(),useUnmergedTree=true).performScrollToNode(hasText(text))}catch(_:AssertionError){}
            rule.onAllNodesWithText(text,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(text,useUnmergedTree=true).assertExists()
    }
    private fun detailClick(text:String){detailText(text);rule.onNodeWithText(text,useUnmergedTree=true).performClick()}
    private fun choose(label:String,value:String) {
        rule.onNode(hasText(label,substring=true) and hasClickAction()).performScrollTo().performClick()
        rule.onNodeWithText(value).performScrollTo().performClick()
    }
    @Test fun openingPositionPurchaseSaleAndManualPricePersist() {
        val name="UI fund ${System.currentTimeMillis()}"
        rule.waitForHome()
        rule.onNodeWithText("Portfolio",useUnmergedTree=true).performClick()
        rule.addInvestment()
        rule.onNodeWithText("More details (optional)").performScrollTo().performClick()
        rule.onNodeWithText("Track purchases & sales instead").performScrollTo().performClick()
        write("Asset name",name)
        write("Units held","10")
        write("Cost of these units (optional)","1000")
        write("NAV / unit price (optional)","120")
        rule.onNodeWithText("Save record").performClick()
        waitFor("Current recorded value")
        detailClick("Add transaction")
        write("Units (units)","5")
        write("Gross consideration in INR","600")
        rule.onNodeWithText("Save event").performClick()
        waitFor("Holdings & book cost")
        detailText("15 units")
        detailClick("Add transaction")
        choose("Event:","Sale / redemption")
        write("Units (units)","3")
        write("Gross consideration in INR","450")
        rule.onNodeWithText("Save event").performClick()
        waitFor("Holdings & book cost")
        detailText("12 units")
        detailText("Realized gain: ₹150.00")
        detailClick("Update price")
        write("Unit price in INR","125.123456")
        write("Source / correction explanation","Statement quote correction")
        rule.onNodeWithText("Save valuation").performClick()
        waitFor("Holdings & book cost")
        rule.activityRule.scenario.recreate()
        waitFor(name)
        detailText("12 units")
        detailText("Unrealized gain: ₹201.48")
    }
    @Test fun propertyAndLoanCanBeAddedWithoutInventingCashFlows() {
        val suffix=System.currentTimeMillis()
        rule.waitForHome()
        rule.onNodeWithText("Portfolio",useUnmergedTree=true).performClick()
        rule.openAddRecord();rule.onNode(hasText("Property") and SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected)).performClick()
        write("Asset name","UI property $suffix")
        write("Your share of value in INR","5000000")
        rule.onNodeWithText("Save record").performClick()
        waitFor("Current recorded value")
        rule.onNodeWithContentDescription("Back").performClick()
        rule.openAddRecord();rule.onNodeWithText("Loan").performClick()
        write("Asset name","UI loan $suffix")
        write("Outstanding principal","100000")
        write("Contract annual rate (%)","12")
        write("Remaining loan months","12")
        rule.onNodeWithText("Save record").performClick()
        waitFor("Outstanding liability")
        rule.onNodeWithText("Outstanding liability").assertExists()
        detailClick("Record repayment")
        write("Actual principal component","5000")
        write("Actual interest component","1000")
        rule.onNodeWithText("Save contract event").performClick()
        waitFor("Outstanding liability")
        rule.onNodeWithText("₹95,000.00").assertExists()
    }
    @Test fun fixedDepositKeepsEstimateSeparateFromObservedValue() {
        val name="UI deposit ${System.currentTimeMillis()}"
        rule.waitForHome()
        rule.onNodeWithText("Portfolio",useUnmergedTree=true).performClick()
        rule.openAddRecord();rule.onNodeWithText("Fixed or recurring deposit").performClick();rule.onNodeWithText("Fixed deposits").performClick()
        write("Asset name",name)
        write("Amount deposited","10000")
        write("Contract annual rate (%)","0")
        write("Maturity date (YYYY-MM-DD)",java.time.LocalDate.now().plusYears(1).toString())
        rule.onNodeWithText("Save record").performClick()
        waitFor("Current recorded value")
        detailClick("Contract inputs");detailText("Gross contract estimate: ₹10,000.00")
        detailClick("Update value")
        write("Observed total in INR","10005")
        write("Source / correction explanation","Actual statement fixture")
        rule.onNodeWithText("Save valuation").performClick()
        waitFor("₹10,005.00")
        rule.onNodeWithText("₹10,005.00").assertExists()
        detailClick("Contract inputs");detailText("Gross contract estimate: ₹10,000.00")
    }
}
