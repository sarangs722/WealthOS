package com.wealthos.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.AnnotatedString
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
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Fictional host captures only; MainActivity keeps FLAG_SECURE. */
@RunWith(Parameterized::class)
class DailyUpdatesUiTest(private val dark:Boolean,private val scale:Float) {
    companion object {@JvmStatic @Parameterized.Parameters(name="dark={0},font={1}") fun cases()=listOf(arrayOf<Any>(false,1f),arrayOf<Any>(true,1f),arrayOf<Any>(false,1.5f))}
    @get:Rule val rule=createComposeRule()
    private fun capture(name:String){
        rule.waitForIdle();val c=ApplicationProvider.getApplicationContext<android.content.Context>();val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        rule.waitUntil(10_000){automation.rootInActiveWindow?.packageName?.toString()==c.packageName}
        // Capture the synchronized Compose root: UiAutomation can return the previous
        // screen's surface immediately after a route change, despite current semantics.
        val root=if(rule.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty())rule.onNode(isDialog()) else rule.onRoot()
        val bitmap=root.captureToImage().asAndroidBitmap();File(c.filesDir,"ux-daily-$name-${if(dark)"dark" else "light"}-$scale.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun conciseFollowUpFormsRetainOptionsAndRequiredExplanations(){
        val day=LocalDate.now();val now=Instant.now()
        val fund=Instrument(UUID.randomUUID(),"Fictional index fund",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.TRANSACTIONS,day.minusDays(1),now)
        val loan=Instrument(UUID.randomUUID(),"Fictional home loan",AssetCategory.LOAN,CurrencyCode.INR,TrackingMode.LOAN,day.minusDays(1),now)
        val fd=Instrument(UUID.randomUUID(),"Fictional deposit",AssetCategory.FD,CurrencyCode.INR,TrackingMode.DEPOSIT,day.minusDays(1),now)
        val book=BankBook(portfolio=PortfolioBook(instruments=listOf(fund,loan,fd),contracts=listOf(
            FinancialContract(loan.id,"10000".toBigDecimal(),java.math.BigDecimal.ZERO,loan.openedOn,day.plusMonths(12),termMonths=12),
            FinancialContract(fd.id,"1000".toBigDecimal(),java.math.BigDecimal.ZERO,fd.openedOn,day.plusYears(1)))))
        var screen by mutableStateOf("investment");var dismiss:()->Unit={}
        rule.setContent{val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,scale)){
            WealthTheme(dark){val model:BankViewModel=viewModel();val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current;dismiss={focus.clearFocus();keyboard?.hide()}
                WealthFrame(1,"Portfolio",false,true,false,{}, {},{}, {},{}){key(screen){
                    val correction=AssetValuation(UUID.randomUUID(),fund.id,ValuationKind.UNIT_PRICE,"100".toBigDecimal(),day,1,now)
                    val state=BankUiState(loading=false,book=if(screen=="correction")book.copy(portfolio=book.portfolio.copy(valuations=listOf(correction))) else book)
                    when(screen){
                        "investment"->InvestmentEntryForm(fund,state,model){}
                        "loan"->ContractEventForm(loan,state,model){}
                        "deposit"->ContractEventForm(fd,state,model){}
                        "maturity"->InstrumentForm(state,model,initialCategory=AssetCategory.FD){}
                        else->ValuationForm(fund,state,model){}
                    }
                }}
            }
        }}
        rule.onNodeWithText("Save event").assertIsDisplayed();rule.onNodeWithText("Fees / withholding in INR").assertDoesNotExist();capture("investment")
        rule.onNodeWithText("Fees & note (optional)").performScrollTo().performClick()
        rule.onNodeWithText("Fees / withholding in INR").performScrollTo().performTextReplacement("12.34")
        rule.runOnIdle {dismiss()};rule.onNodeWithText("Fees & note (optional)").performScrollTo().performClick()
        rule.onNodeWithText("Fees: 12.34 INR").assertExists()
        rule.onNodeWithText("Fees & note (optional)").performScrollTo().performClick()
        rule.onNodeWithText("Fees / withholding in INR").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("12.34")))
        rule.onNode(hasText("Event:",substring=true) and hasClickAction()).performScrollTo().performClick();rule.onNodeWithText("Manual unit adjustment").performScrollTo().performClick()
        rule.onNodeWithText("Explanation / note").performScrollTo().assertIsDisplayed();rule.onNodeWithText("Note (optional)").assertDoesNotExist();capture("adjustment")
        for(name in listOf("loan","deposit","price","correction","maturity")){
            rule.runOnIdle {screen=name}
            rule.onNodeWithText(when(name){"loan"->"Save payment";"deposit"->"Save event";"maturity"->"Save record";else->"Save valuation"}).assertIsDisplayed()
            if(name=="loan" || name=="deposit")rule.onNodeWithText("Fees / withholding").assertDoesNotExist()
            if(name=="deposit")rule.onNodeWithText("Record maturity payout").assertExists()
            if(name=="price")rule.onNodeWithText("Source / correction explanation").assertDoesNotExist()
            if(name=="correction")rule.onNodeWithText("Source / correction explanation").performScrollTo().assertIsDisplayed()
            capture(name)
            if(name=="maturity"){
                rule.onNodeWithText("Maturity date: Choose date").performScrollTo().performClick();rule.onNode(isDialog()).assertExists();capture("calendar");rule.onNodeWithText("Cancel").performClick()
            }
        }
    }
}
