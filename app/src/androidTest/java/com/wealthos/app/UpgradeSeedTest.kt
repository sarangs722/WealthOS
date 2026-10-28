package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Direct-only fixture: uses only UI available in APK 0.5.0. */
@RunWith(Parameterized::class)
class UpgradeSeedTest(@Suppress("UNUSED_PARAMETER") marker:String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="Old APK fixture")
        fun parameters():List<Array<String>> = if(InstrumentationRegistry.getArguments().getString("seedOldApk")=="true")listOf(arrayOf("seed")) else emptyList()
    }
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun seedOldApkWithDatedBankHistory() {
        val suffix=System.currentTimeMillis();val name="Upgrade bank $suffix";val holding="Upgrade holding $suffix"
        rule.waitUntil(30_000) {rule.onAllNodesWithTag("home-list").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Portfolio",useUnmergedTree=true).performClick()
        rule.addBank()
        rule.onNodeWithText("Account name").performTextInput(name)
        rule.onNodeWithText("Balance in INR").performScrollTo()
        rule.onNodeWithText("Balance in INR").performTextInput("100.10")
        rule.onNodeWithText("Save balance").performClick()
        rule.waitUntil(10_000) {rule.onAllNodesWithTag("portfolio-list").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText(name).performScrollTo().performClick()
        rule.onNodeWithText("Update balance").performClick()
        rule.onNodeWithText("Balance in INR").performScrollTo()
        rule.onNodeWithText("Balance in INR").performTextInput("200.20")
        rule.onNodeWithText("Note / correction explanation").performScrollTo().performTextInput("Upgrade fixture correction")
        rule.onNodeWithText("Save balance").performClick()
        rule.waitUntil(10_000) {rule.onAllNodesWithText("Balance history").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("Back").performClick()
        rule.addInvestment();rule.onNodeWithText("More details (optional)").performScrollTo().performClick();rule.onNodeWithText("Track purchases & sales instead").performScrollTo().performClick()
        rule.onNodeWithText("Asset name").performTextInput(holding)
        rule.onNodeWithText("Units held").performScrollTo().performTextInput("5.00")
        rule.onNodeWithText("Cost of these units (optional)").performScrollTo().performTextInput("400.00")
        rule.onNodeWithText("NAV / unit price (optional)").performScrollTo().performTextInput("100.50")
        rule.onNodeWithText("Save record").performClick()
        rule.waitUntil(20_000) {rule.onAllNodesWithText("Current recorded value").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("5 units").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Home",useUnmergedTree=true).performClick()
        rule.onNodeWithText("Cash flow").performClick();rule.onNodeWithText("Income sources & salary").performScrollTo().performClick()
        rule.onNodeWithText("Add income source").performClick()
        rule.onNodeWithText("Income source name").performTextInput("Upgrade income $suffix")
        rule.onNodeWithText("Expected monthly net (optional)").performScrollTo().performTextInput("1000")
        rule.onNodeWithText("Save income source").performClick()
        rule.waitUntil(20_000) {rule.onAllNodesWithText("Received income history").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Record received income").performClick()
        rule.onNodeWithText("Actual net received in INR").performScrollTo().performTextInput("1000")
        rule.onNodeWithText("Save received income").performClick()
        rule.waitUntil(20_000) {rule.onAllNodesWithText("Net received ₹1,000.00").fetchSemanticsNodes().isNotEmpty()}
        repeat(3){rule.onNodeWithContentDescription("Back").performClick()}
        rule.onNodeWithText("Forecast",useUnmergedTree=true).performClick();rule.onNodeWithText("Create a scenario").performClick()
        rule.onNodeWithText("Scenario name").performTextInput("Upgrade plan $suffix")
        rule.onNodeWithText("Monthly new investment in INR").performScrollTo().performTextReplacement("100")
        rule.onNodeWithText("Save scenario").performClick()
        rule.waitUntil(45_000){rule.onAllNodesWithText("Estimated outcome").fetchSemanticsNodes().isNotEmpty()}
        ApplicationProvider.getApplicationContext<android.content.Context>().openFileOutput("upgrade-account.txt",0).use {it.write(name.toByteArray())}
    }
}
