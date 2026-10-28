package com.wealthos.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test

class PlanningPrivacyTest {
    @get:Rule val rule=createComposeRule()
    @Test fun forecastAndGoalOutputsRespectObscuredAmounts(){
        val book=demoBook(true);var detail by mutableStateOf(false)
        rule.setContent {WealthTheme {if(detail)ScenarioDetails(book.planning.latestScenarios().first {it.kind==com.wealthos.finance.ScenarioKind.BASE},BankUiState(loading=false,book=book,demo=true),true,{}) else ForecastScreen(BankUiState(loading=false,book=book,demo=true),true,{}, {detail=true},{},{})}}
        rule.onNodeWithTag("forecast-list").performScrollToNode(hasText("Fictional base scenario"));rule.onNodeWithText("Fictional base scenario").performClick()
        rule.waitUntil(45_000){rule.onAllNodesWithText("Estimated outcome").fetchSemanticsNodes().isNotEmpty()}
        rule.onAllNodes(hasText("₹",substring=true)).assertCountEquals(0)
        rule.onNodeWithTag("scenario-detail").performScrollToNode(hasText("Forecast chart hidden"));rule.onNodeWithText("Forecast chart hidden").assertExists()
        rule.onNodeWithTag("scenario-detail").performScrollToNode(hasText("Saved assumptions"));rule.onNodeWithText("Saved assumptions").performClick();rule.onAllNodes(hasText("₹",substring=true)).assertCountEquals(0)
    }
}
