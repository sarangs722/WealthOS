package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class AddNavigationTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun categorySelectionAndLiquidityDefaultsSurviveNavigation(){
        rule.waitForHome();rule.openAddRecord();rule.onNodeWithText("Property").performClick()
        rule.onNodeWithText("Add real estate").assertExists()
        rule.onNodeWithText("More options").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Include in liquid assets").performScrollTo().assertIsOff()
        rule.onNodeWithContentDescription("Back").performClick();rule.openAddRecord()
        rule.onNodeWithText("Investment").performClick();rule.onNodeWithText("Stocks").performClick()
        rule.onNodeWithText("Add stock").assertExists()
        rule.onNodeWithText("More details (optional)").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Include in liquid assets").performScrollTo().assertIsOn()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Add stock").assertExists()
    }
}
