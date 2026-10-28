package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test

class HouseholdPrivacyTest {
    @get:Rule val rule=createComposeRule()
    @Test fun reserveAndInsuranceReadViewsObscureAllCurrencyAmounts() {
        val state=BankUiState(loading=false,book=demoBook(true),demo=true)
        rule.setContent {WealthTheme {ProtectScreen(state,true,{},{},{},{})}}
        rule.onNodeWithText("Emergency fund").assertExists()
        rule.onNodeWithText("How this fund is counted").performClick()
        rule.onAllNodes(hasText("₹",substring=true)).assertCountEquals(0)
        rule.onNodeWithTag("protect-list").performScrollToNode(hasText("Selected funds"));rule.onNodeWithText("Selected funds").performClick()
        rule.onAllNodes(hasText("₹",substring=true)).assertCountEquals(0)
        rule.onNodeWithTag("protect-list").performScrollToNode(hasText("Example family health"))
        rule.onNodeWithText("Example family health").assertExists()
        rule.onAllNodes(hasText("₹",substring=true)).assertCountEquals(0)
    }
}
