package com.wealthos.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.wealthos.finance.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.time.YearMonth

@RunWith(Parameterized::class)
class RemainingFormsUiTest(private val dark:Boolean,private val scale:Float) {
    companion object {@JvmStatic @Parameterized.Parameters(name="dark={0},font={1}") fun cases()=listOf(arrayOf<Any>(false,1f),arrayOf<Any>(true,1f),arrayOf<Any>(false,1.5f))}
    @get:Rule val rule=createComposeRule()
    private fun capture(name:String) {
        rule.waitForIdle();val c=ApplicationProvider.getApplicationContext<android.content.Context>();val a=InstrumentationRegistry.getInstrumentation().uiAutomation
        rule.waitUntil(10_000){a.rootInActiveWindow?.packageName?.toString()==c.packageName}
        val bitmap=checkNotNull(a.takeScreenshot());File(c.filesDir,"ux-remaining-$name-${if(dark)"dark" else "light"}-$scale.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun compactPlanningAndCalculatorFormsRemainReachable(){
        var screen by mutableStateOf("scenario");var dismiss:()->Unit={};val state=BankUiState(loading=false)
        rule.setContent{val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
            WealthTheme(dark){val model:BankViewModel=viewModel();val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current;dismiss={focus.clearFocus();keyboard?.hide()};WealthFrame(2,"Planning",false,true,false,{}, {},{}, {},{}){key(screen){when(screen){
                "scenario"->ScenarioForm(state,model,null){}
                "goal"->GoalForm(state,model,null){}
                "cash"->CashFlowForm(YearMonth.now(),CurrencyCode.INR,state,model){}
                "sip"->CalculatorScreen(CalculatorKind.SIP,false)
                else->CalculatorScreen(CalculatorKind.EMI,false)
            }}}}
        }}
        rule.waitUntil(30_000){runCatching {rule.onAllNodesWithText("Scenario name").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
        rule.onNodeWithText("Save scenario").assertIsDisplayed();rule.onNodeWithText("Monthly new investment in INR").assertIsDisplayed();capture("scenario")
        rule.onNodeWithText("Annual contribution step-up %").assertDoesNotExist()
        for((name,save) in listOf("goal" to "Save goal","cash" to "Save draft","sip" to "Calculate","emi" to "Calculate")) {
            rule.runOnIdle {screen=name};rule.onNodeWithText(save).assertIsDisplayed();capture(name)
            if(name=="cash") {
                rule.onNodeWithText("Insurance premiums actually paid").assertDoesNotExist()
                rule.onNodeWithText("Confirm completeness").performScrollTo().performClick()
                rule.onNodeWithContentDescription("All received income is included").performScrollTo().performClick()
                rule.onNodeWithContentDescription("All outflow categories are included; zero means none").performScrollTo().performClick()
                rule.onNodeWithText("Save monthly summary").assertIsDisplayed()
            }
            if(name=="emi") {
                rule.onNodeWithText("Outstanding principal in INR").performScrollTo().performTextReplacement("1200")
                rule.onNodeWithText("Nominal annual loan interest %").performScrollTo().performTextReplacement("0")
                rule.onNodeWithText("Remaining term in months").performScrollTo().performTextReplacement("12")
                rule.runOnIdle {dismiss()}
                rule.onNodeWithText("Calculate").performClick()
                rule.waitUntil(30_000){rule.onAllNodesWithText("Monthly EMI: \u20b9100.00").fetchSemanticsNodes().isNotEmpty()}
                rule.onNodeWithText("Monthly EMI: \u20b9100.00").assertIsDisplayed();capture("emi-result")
                rule.onNodeWithText("Month 1",substring=true).assertDoesNotExist()
                rule.onNodeWithText("Repayment schedule").performScrollTo().performClick()
                rule.onNode(hasText("Month 1 ",substring=true)).performScrollTo().assertExists()
            }
        }
    }
}
