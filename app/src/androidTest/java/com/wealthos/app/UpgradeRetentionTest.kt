package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Run directly after seeding 0.5.0 and installing the 0.6.0 update with adb install -r. */
@RunWith(Parameterized::class)
class UpgradeRetentionTest(private val expected:String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="Preserve populated APK")
        fun parameters():List<Array<String>> = InstrumentationRegistry.getArguments().getString("upgradeAccount")?.let {listOf(arrayOf(it))} ?: emptyList()
    }
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun populatedBankAndPortfolioAreVisibleAfterApkUpdate() {
        rule.waitUntil(30_000) {rule.onAllNodesWithTag("home-list").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Portfolio",useUnmergedTree=true).performClick()
        rule.onNodeWithText("Search portfolio").performTextInput(expected)
        rule.onNodeWithTag("portfolio-list").performScrollToNode(hasText(expected) and !hasSetTextAction())
        rule.onNode(hasText(expected) and !hasSetTextAction()).performScrollTo().performClick()
        rule.onAllNodesWithText("₹200.20").onFirst().assertExists()
        rule.onNodeWithText("Balance history").performScrollTo().performClick()
        rule.onNodeWithTag("account-detail").performScrollToNode(hasText("₹100.10"))
        rule.onAllNodesWithText("₹100.10").onFirst().assertExists()
        rule.onNodeWithText("Superseded · Retained for history").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        val holding=expected.replace("Upgrade bank ","Upgrade holding ")
        rule.onNodeWithText("Search portfolio").performTextReplacement(holding)
        rule.onNodeWithTag("portfolio-list").performScrollToNode(hasText(holding) and !hasSetTextAction())
        rule.onNode(hasText(holding) and !hasSetTextAction()).performScrollTo().performClick()
        rule.onNodeWithText("₹502.50").assertExists()
        rule.onNodeWithText("5 units").performScrollTo().assertExists()
        rule.onNodeWithText("Remaining FIFO cost: ₹400.00").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Home",useUnmergedTree=true).performClick()
        rule.onNodeWithText("Cash flow").performClick();rule.onNodeWithText("Income sources & salary").performScrollTo().performClick()
        rule.onNodeWithText(expected.replace("Upgrade bank ","Upgrade income ")).performScrollTo().performClick()
        rule.onNodeWithText("Net received ₹1,000.00").performScrollTo().assertExists()
        repeat(3){rule.onNodeWithContentDescription("Back").performClick()}
        rule.onNodeWithText("Forecast",useUnmergedTree=true).performClick()
        val plan=expected.replace("Upgrade bank ","Upgrade plan ")
        rule.onNodeWithTag("forecast-list").performScrollToNode(hasText(plan));rule.onNodeWithText(plan).performClick()
        rule.waitUntil(45_000){rule.onAllNodesWithText("Estimated outcome").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("scenario-detail").performScrollToNode(hasText("Saved assumptions"));rule.onNodeWithText("Saved assumptions").performClick();rule.onNode(hasText("Monthly new investments ₹100.00",substring=true)).assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Public data & sources").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Allow public network refresh").assertIsOff()
        rule.onNodeWithContentDescription("Back").performClick();rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Explore demo mode").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Manage backups").performScrollTo().performClick()
        rule.onNodeWithText("Demo · Fictional records").assertExists()
        // Only fictional demo records are rendered. Production screenshot protection is restored immediately.
        rule.activityRule.scenario.onActivity {it.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
        try {
            val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            java.io.File(context.filesDir,"m6-demo-backups.png").outputStream().use {
                rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
        } finally {
            rule.activityRule.scenario.onActivity {it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
        }
    }
}
