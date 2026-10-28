package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class PublicReferenceWorkflowTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun manualEconomicReferencePersistsWithoutEnablingNetwork(){
        val label="Fictional rate ${System.nanoTime()}"
        rule.waitUntil(30_000){rule.onAllNodesWithTag("home-list").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("Settings").performClick();rule.onNodeWithText("Public data & sources").performScrollTo().performClick()
        rule.onNodeWithTag("public-data-list").performScrollToNode(hasText("Add economic reference"));rule.onNodeWithText("Add economic reference").performClick()
        rule.onNodeWithText("Reference label").performTextInput(label);rule.onNodeWithText("Reference value").performTextInput("6.125")
        rule.onNodeWithText("HTTPS source URL").performScrollTo().performTextInput("https://example.com/fictional-test-only")
        rule.onNodeWithText("Base / coverage / rate convention").performScrollTo().performTextInput("Fictional nominal annual rate")
        rule.onNodeWithText("Save reference").performScrollTo().performClick()
        rule.waitUntil(20_000){rule.onAllNodesWithTag("public-data-list").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("public-data-list").performScrollToNode(hasText(label));rule.onNodeWithText(label).assertExists()
        rule.onAllNodesWithText("6.125 percent").onFirst().assertExists()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(30_000){rule.onAllNodesWithTag("public-data-list").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("public-data-list").performScrollToNode(hasText(label));rule.onNodeWithText(label).assertExists()
        rule.onNodeWithTag("public-data-list").performScrollToNode(hasContentDescription("Allow public network refresh"))
        rule.onNodeWithContentDescription("Allow public network refresh").assertIsOff()
        rule.onNodeWithTag("public-data-list").performScrollToNode(hasText("Refresh ECB"));rule.onNodeWithText("Refresh ECB").assertIsNotEnabled()
    }
}
