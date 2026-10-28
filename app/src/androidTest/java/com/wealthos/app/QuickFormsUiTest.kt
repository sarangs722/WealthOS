package com.wealthos.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
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

/** Fictional native form captures; real MainActivity screenshot protection stays enabled. */
@RunWith(Parameterized::class)
class QuickFormsUiTest(private val dark:Boolean,private val scale:Float) {
    companion object {@JvmStatic @Parameterized.Parameters(name="dark={0},font={1}") fun cases()=listOf(arrayOf<Any>(false,1f),arrayOf<Any>(true,1f),arrayOf<Any>(false,1.5f))}
    @get:Rule val rule=createComposeRule()
    private fun capture(name:String) {
        rule.waitForIdle();val c=ApplicationProvider.getApplicationContext<android.content.Context>()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        rule.waitUntil(10_000){automation.rootInActiveWindow?.packageName?.toString()==c.packageName}
        val bitmap=checkNotNull(automation.takeScreenshot());File(c.filesDir,"ux-quick-$name-${if(dark)"dark" else "light"}-$scale.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun essentialFormsShowConciseInputsAndReachableSave(){
        var screen by mutableStateOf("fund");val state=BankUiState(loading=false)
        rule.setContent {val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
            WealthTheme(dark){val model:BankViewModel=viewModel();WealthFrame(1,"Portfolio",false,true,false,{}, {},{}, {},{}){key(screen){when(screen){
                "fund"->InstrumentForm(state,model,initialCategory=AssetCategory.MUTUAL_FUND){}
                "stock"->InstrumentForm(state,model,initialCategory=AssetCategory.STOCK){}
                "bank"->AccountEditor("add",null,state,model,{})
                "retirement"->InstrumentForm(state,model,initialCategory=AssetCategory.EPF){}
                "deposit"->InstrumentForm(state,model,initialCategory=AssetCategory.FD){}
                "income"->IncomeSourceForm(state,model,null){}
                else->PolicyForm(state,model,null){}
            }}}}
        }}
        rule.waitUntil(30_000){runCatching {rule.onAllNodesWithText("Fund name").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
        rule.onNodeWithText("Fund name").assertIsDisplayed();rule.onNodeWithText("Current value in INR").assertIsDisplayed();rule.onNodeWithText("Save holding").assertIsDisplayed()
        rule.onNodeWithText("Units held (optional)").assertDoesNotExist();capture("fund")
        rule.onNodeWithText("Units & price").performClick();rule.onNodeWithText("Units held").assertIsDisplayed();rule.onNodeWithText("NAV in INR").assertIsDisplayed();capture("nav")
        rule.onNodeWithText("As of: Today").performScrollTo().performClick()
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir,"quick-calendar-semantics.txt").writeText(rule.onNode(isDialog()).printToString())
        capture("calendar")
        val first=java.time.LocalDate.now().withDayOfMonth(1).format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu",java.util.Locale.ENGLISH))
        rule.onNode(hasText(first,substring=true) and hasClickAction()).performClick()
        rule.onNodeWithText("Use date").performClick()
        val chosen=java.time.LocalDate.now().withDayOfMonth(1)
        rule.onNodeWithText("As of: ${if(chosen==java.time.LocalDate.now())"Today" else dateLabel(chosen)}").assertExists()
        for((name,save) in listOf("stock" to "Save holding","bank" to "Save balance","retirement" to "Save record","deposit" to "Save record","income" to "Save income source","policy" to "Save policy terms")) {
            rule.runOnIdle {screen=name};rule.onNodeWithText(save).assertIsDisplayed();capture(name)
        }
    }
}
