package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HouseholdWorkflowTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun waitFor(text:String) {rule.waitUntil(30_000) {rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun write(label:String,value:String) {
        rule.activity.dismissKeyboardForTest()
        if(rule.onAllNodesWithTag("account-form").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag("account-form").performScrollToNode(hasText(label))
            rule.onNodeWithText(label).performTextReplacement(value)
        } else rule.onNodeWithText(label).performScrollTo().performTextReplacement(value)
        rule.activity.dismissKeyboardForTest()
    }
    private fun choose(prefix:String,value:String) {
        rule.onNode(hasText(prefix,substring=true) and hasClickAction()).performScrollTo().performClick()
        rule.onNodeWithText(value).performScrollTo().performClick()
    }
    private fun saveBank() {rule.onNodeWithTag("account-form").performScrollToNode(hasText("Save balance"));rule.onNodeWithText("Save balance").performClick()}
    @Test fun salarySummaryEarmarksAndInsuranceRemainSeparateFromBankCash() {
        val suffix=System.currentTimeMillis();val reserve="UI reserve $suffix";val salary="UI salary $suffix";val policy="UI health $suffix"
        rule.waitForHome()
        rule.onNodeWithText("Portfolio",useUnmergedTree=true).performClick();rule.addBank()
        write("Account name",reserve)
        rule.onNodeWithTag("account-form").performScrollToNode(hasText("Balance in INR"))
        write("Balance in INR","20000");saveBank();rule.waitUntil(30_000){rule.onAllNodesWithTag("portfolio-list").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Home",useUnmergedTree=true).performClick()
        rule.onNodeWithText("Cash flow").performClick();rule.onNodeWithText("Income sources & salary").performScrollTo().performClick();waitFor("Income & salary")
        rule.onNodeWithText("Add income source").performClick();write("Income source name",salary);write("Expected monthly net (optional)","100000")
        rule.onNodeWithText("Save income source").performScrollTo().performClick();waitFor("Received income history")
        rule.onNodeWithText("Record received income").performClick()
        write("Actual net received in INR","90000");write("Payslip gross (optional)","100000");write("Payslip deductions (optional)","10000")
        rule.onNodeWithText("Save received income").performScrollTo().performClick();waitFor("Received income history")
        rule.onNodeWithContentDescription("Back").performClick();rule.onNodeWithContentDescription("Back").performClick();waitFor("Monthly cash flow")
        rule.onNodeWithText("Add monthly summary").performClick();choose("Income input:","Use recorded monthly income sources")
        rule.onNodeWithContentDescription("All received income is included").performScrollTo().performClick()
        write("Essential living expenses","20000");write("Discretionary expenses","5000");write("Insurance premiums actually paid","1000")
        write("Loan principal actually paid","3000");write("Loan interest actually paid","1000");write("Investment contributions paid","10000")
        rule.onNodeWithContentDescription("All outflow categories are included; zero means none").performScrollTo().performClick()
        write("Summary note / correction","UI confirmed monthly figures")
        rule.onNodeWithText("Save monthly summary").performScrollTo().performClick();waitFor("Surplus before investments ₹60,000.00")
        rule.onNodeWithText("Remaining after investments ₹50,000.00").assertExists()
        rule.onNodeWithContentDescription("Back").performClick();rule.onNodeWithText("Protect",useUnmergedTree=true).performClick()
        rule.waitUntil(30_000){rule.onAllNodesWithTag("protect-list").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithText("Set target").performClick();write("Target amount in INR","30000")
        rule.onNodeWithText("Save reserve target").performScrollTo().performClick();rule.waitUntil(30_000){rule.onAllNodesWithTag("protect-list").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Choose funds").performClick();choose("Existing reserve asset:","$reserve · INR")
        write("Earmarked amount in INR","10000");rule.onNodeWithText("Save earmark").performScrollTo().performClick();rule.waitUntil(30_000){rule.onAllNodesWithTag("protect-list").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("₹10,000.00").assertExists()
        rule.onNodeWithTag("protect-list").performScrollToNode(hasText("Add insurance policy"));rule.onNodeWithText("Add insurance policy").performClick()
        write("Policy nickname",policy);write("Insurer","UI insurer");choose("Insurance kind:","Health insurance")
        write("Recorded cover end (YYYY-MM-DD)",java.time.LocalDate.now().plusYears(1).toString());write("Cover limit in INR (optional)","1000000")
        choose("Health cover scope:","Family floater (shared limit)");write("Premium per installment (optional)","1000");choose("Premium frequency:","Monthly")
        write("First tracked premium due (optional)",java.time.LocalDate.now().plusDays(5).toString())
        rule.onNodeWithText("Save policy terms").performScrollTo().performClick();waitFor("Recorded protection")
        rule.onNodeWithText("Record premium settlement").performScrollTo().performClick();write("Total paid toward this installment","1000")
        rule.onNodeWithText("Save premium settlement").performScrollTo().performClick();waitFor("Recorded protection")
        rule.activityRule.scenario.recreate();waitFor("Recorded protection")
        rule.onNodeWithContentDescription("Back").performClick();rule.onNodeWithText("Portfolio",useUnmergedTree=true).performClick()
        rule.onNodeWithText("Search portfolio").performTextReplacement(reserve)
        rule.onNodeWithTag("portfolio-list").performScrollToNode(hasText(reserve) and !hasSetTextAction())
        rule.onNode(hasText(reserve) and !hasSetTextAction()).performClick()
        // The current balance and its retained observation both display this exact amount.
        rule.onAllNodesWithText("₹20,000.00").onFirst().assertExists()
    }
}
