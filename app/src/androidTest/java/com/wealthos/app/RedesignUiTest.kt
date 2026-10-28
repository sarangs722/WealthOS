package com.wealthos.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import com.wealthos.finance.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Native rendered fixtures only. MainActivity's screenshot protection stays enabled. */
@RunWith(Parameterized::class)
class RedesignUiTest(private val dark:Boolean,private val textScale:Float) {
    companion object {@JvmStatic @Parameterized.Parameters(name="dark={0}, font={1}") fun cases()=listOf(arrayOf<Any>(false,1f),arrayOf<Any>(true,1f),arrayOf<Any>(false,1.5f))}
    @get:Rule val rule=createComposeRule()
    private fun capture(name:String){
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        rule.waitForIdle()
        // A passing Compose assertion must not conceal a system error overlay.
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        rule.waitUntil(10_000){automation.rootInActiveWindow?.packageName?.toString()==context.packageName}
        // Android's native capture includes the sheet's separate window. Compose's
        // multi-window forceRedraw times out on this API 36 emulator's hidden base window.
        val bitmap=checkNotNull(automation.takeScreenshot())
        File(context.filesDir,"ux-$name-${if(dark)"dark" else "light"}-$textScale.png").outputStream().use{
            bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
        }
        bitmap.recycle()
    }
    @Test fun fiveDestinationsAndDetailsStayReachable(){
        val book=demoBook(true);val state=BankUiState(loading=false,book=book,demo=true)
        var tab by mutableIntStateOf(0);var settings by mutableStateOf(false)
        var action by mutableStateOf("")
        rule.setContent{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,textScale)){
                WealthTheme(dark){WealthFrame(tab,if(settings)"Settings" else listOf("WealthOS","Portfolio","Forecast","Tools","Protect")[tab],!settings,true,false,
                    onTab={tab=it},onBack={settings=false},onSettings={settings=true},onExitDemo={},onAdd={}){
                    if(settings)SettingsScreen(false,false,null,{}, {},{},true,{}, {}) else when(tab){
                        0->HomeScreen(book,false,true,{}, {},{},{},onCashFlow={action="cash"},onProtect={tab=4})
                        1->PortfolioScreen(book,false,true,{}, {},{}, {},{}, {})
                        2->ForecastScreen(state,false,{}, {},{}, {})
                        3->ToolsScreen{action=it.name}
                        else->ProtectScreen(state,false,{}, {},{}, {})
                    }
                }}
            }
        }
        rule.onNodeWithTag("home-list").assertExists();capture("home")
        if(textScale>1.2f)rule.onNodeWithTag("metric-pair-stacked").assertExists()
        rule.onNodeWithTag("home-list").performScrollToNode(hasText("Cash flow"));rule.onNodeWithText("Cash flow").performClick();rule.runOnIdle{assertEquals("cash",action)}
        for((i,tag) in listOf(1 to "portfolio-list",2 to "forecast-list",3 to "tools-list",4 to "protect-list")){
            rule.onNodeWithTag("nav-$i").performClick();rule.onNodeWithTag(tag).assertExists()
            if(i==2)rule.waitUntil(60_000){rule.onAllNodes(hasText("Today's value",substring=true)).fetchSemanticsNodes().isNotEmpty()}
            capture(listOf("home","portfolio","forecast","tools","protect")[i])
            if(i==3){rule.onNodeWithText("Search calculators").performTextReplacement("emi");rule.onNodeWithText("EMI").performClick();rule.runOnIdle{assertEquals("EMI",action)}}
        }
        rule.onNodeWithContentDescription("Settings").performClick();rule.onNodeWithText("Manage backups").assertExists();capture("settings")
        rule.onNodeWithContentDescription("Back").performClick();rule.onNodeWithTag("nav-0").performClick()
        rule.onNodeWithTag("home-list").performScrollToNode(hasText("Net worth details"));rule.onNodeWithText("Net worth details").performClick()
        rule.onNodeWithTag("home-list").performScrollToNode(hasText("Liquid net worth",substring=true));rule.onNode(hasText("Liquid net worth",substring=true)).assertExists()
    }
    @Test fun categorizedAddMenuPreservesExplicitChoice(){
        var selected:AssetCategory?=null;var bank=false
        rule.setContent{val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,textScale)){WealthTheme(dark){AddRecordSheet({}, {bank=true},{selected=it},{})}}}
        rule.onNodeWithText("Bank account").assertExists();capture("add")
        rule.onNodeWithText("Fixed or recurring deposit").performClick();rule.onNodeWithText("Recurring deposits").performClick()
        rule.runOnIdle{assertEquals(AssetCategory.RD,selected)}
        rule.onNodeWithContentDescription("Back to record types").performClick();rule.onNodeWithText("Bank account").performClick();rule.runOnIdle{assertTrue(bank)}
    }
    @Test fun largeExactAmountsFitWithoutSplittingDigits(){
        val base=demoBook();val account=base.accounts.first()
        val book=base.copy(accounts=listOf(account),observations=listOf(base.observations.last{it.accountId==account.id}.copy(amount="1234567890.12".toBigDecimal())))
        rule.setContent{val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,textScale)){
            WealthTheme(dark){HomeScreen(book,false,true,{}, {},{}, {})}
        }}
        fun checkValue(tag:String){
            val layouts=mutableListOf<TextLayoutResult>()
            rule.onNodeWithTag("home-list").performScrollToNode(hasTestTag(tag))
            rule.onNodeWithTag(tag).assertTextEquals("₹1,23,45,67,890.12").performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            assertEquals(1,layouts.single().lineCount);assertFalse("All digits must fit",layouts.single().hasVisualOverflow)
        }
        checkValue("net-worth-value");checkValue("assets-value")
        rule.onNodeWithTag("metric-pair-stacked").assertExists();capture("large-amount")
    }
    @Test fun bankEntryAndRetainedHistoryRemainUsable(){
        val book=demoBook();var details by mutableStateOf(false);var clearEntryFocus:()->Unit={}
        rule.setContent{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,textScale)){
                WealthTheme(dark){val model:BankViewModel=viewModel()
                    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
                    clearEntryFocus={focus.clearFocus();keyboard?.hide()}
                    WealthFrame(1,"Portfolio",false,true,false,{}, {},{}, {},{}){
                        if(details)AccountDetails(book.accounts.first(),book,false,true,{}, {})
                        else AccountEditor("add",null,BankUiState(loading=false),model,{})
                    }
                }
            }
        }
        rule.onNodeWithText("Account name").performTextReplacement("Fictional savings")
        rule.onNodeWithText("Balance in INR").performScrollTo();rule.onNodeWithText("Balance in INR").performTextReplacement("100.10")
        // Focus only; no save occurs and no fictional form data enters the vault.
        rule.runOnIdle{clearEntryFocus()};rule.onNodeWithText("Account name").performScrollTo();capture("bank-entry")
        rule.runOnIdle{details=true};rule.onNodeWithText("Current balance").assertExists();capture("bank-detail")
        rule.onNodeWithTag("account-detail").performScrollToNode(hasText("Balance history"));rule.onNodeWithText("Balance history").performClick()
        rule.onNodeWithTag("account-detail").performScrollToNode(hasText("${dateLabel(java.time.LocalDate.now().minusDays(60))} · Revision 1"))
        rule.onNodeWithText("₹84,500.00").assertExists()
    }
}
