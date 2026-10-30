package com.wealthos.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import com.wealthos.finance.*
import java.io.File
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class StatementUiTest(private val dark:Boolean,private val scale:Float) {
    companion object {@JvmStatic @Parameterized.Parameters(name="dark={0},font={1}") fun cases()=listOf(arrayOf<Any>(false,1f),arrayOf<Any>(true,1f),arrayOf<Any>(false,1.5f))}
    @get:Rule val rule=createComposeRule()
    @Test fun reviewAndEmptyScreensHaveAccessibleFixedActionAndRespectMasking() {
        val day=LocalDate.parse("2026-10-02")
        val parsed=ParsedStatement(StatementKind.HDFC_NEU_CARD,"f".repeat(64),day,day,listOf(
            StatementInputRow(1,day,null,"Fictional neighbourhood groceries","","850.25".toBigDecimal(),StatementDirection.DEBIT),
            StatementInputRow(2,day,null,"Fictional card repayment","","5000.00".toBigDecimal(),StatementDirection.CREDIT)))
        var screen by mutableStateOf("review");var hidden by mutableStateOf(false)
        rule.setContent {val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,scale)){
            WealthTheme(dark) {val model:BankViewModel=viewModel();WealthFrame(0,"Statement spending",false,false,false,{}, {},{}, {},{}){
                if(screen=="review")StatementReviewScreen(BankUiState(loading=false,statementImport=StatementImportState(parsed=parsed)),hidden,model,null){}
                else StatementsScreen(BankUiState(loading=false),hidden,{},{})
            }}
        }}
        rule.onNodeWithTag("save-statement").assertIsDisplayed();capture("review")
        rule.runOnIdle {hidden=true}
        rule.onAllNodesWithText("Fictional neighbourhood groceries").assertCountEquals(0)
        capture("hidden")
        rule.runOnIdle {screen="empty"};rule.onNodeWithTag("choose-statement").assertIsDisplayed();capture("empty")
    }
    private fun capture(name:String) {
        rule.waitForIdle();val context=ApplicationProvider.getApplicationContext<android.content.Context>();check(context.packageName=="com.wealthos.app.uxlab")
        val bitmap=rule.onRoot().captureToImage().asAndroidBitmap()
        File(context.filesDir,"ux-statements-$name-${if(dark)"dark" else "light"}-$scale.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
