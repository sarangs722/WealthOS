package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule

internal fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>,MainActivity>.waitForHome() {
    waitUntil(30_000){onAllNodesWithTag("home-list").fetchSemanticsNodes().isNotEmpty()}
}
internal fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>,MainActivity>.openAddRecord() {
    if(onAllNodesWithContentDescription("Add record").fetchSemanticsNodes().isNotEmpty())onNodeWithContentDescription("Add record").performClick()
    else if(onAllNodesWithText("Add your first record").fetchSemanticsNodes().isNotEmpty())onNodeWithText("Add your first record").performClick()
    else {onNodeWithTag("portfolio-list").performScrollToNode(hasText("Add a record"));onNodeWithText("Add a record").performClick()}
}
internal fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>,MainActivity>.addBank() {
    openAddRecord();onNode(hasText("Bank account") and hasClickAction()).performClick()
}
internal fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>,MainActivity>.addInvestment() {
    openAddRecord();onNode(hasText("Investment") and hasClickAction()).performClick();onNode(hasText("Mutual funds") and hasClickAction()).performClick()
}
