package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.wealthos.finance.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/** Fictional UI Lab only. Never invokes the phone or copies user documents. */
class StatementImportTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun model()=ViewModelProvider(rule.activity)[BankViewModel::class.java]
    private fun fixture(name:String)=InstrumentationRegistry.getInstrumentation().context.assets.open("statements/$name").use {it.readBytes()}
    private fun ready(){check(rule.activity.packageName=="com.wealthos.app.uxlab");rule.waitUntil(15000){!model().state.value.loading};assertFalse(model().state.value.storageError)}
    @Test fun isolatedXlsReaderWorksOnAndroid10WithoutInternetOrVaultAccess() {
        ready()
        val bank=runBlocking {StatementReaderClient.parse(rule.activity,fixture("fictional-bank.xls"))}
        assertEquals(StatementKind.HDFC_BANK,bank.kind);assertEquals("125.25",bank.rows.first().amount.toPlainString());assertEquals(3,bank.rows.size)
        val card=runBlocking {StatementReaderClient.parse(rule.activity,fixture("fictional-card.xls"))}
        assertEquals(StatementKind.HDFC_NEU_CARD,card.kind);assertEquals(3,card.rows.size);assertEquals(StatementDirection.CREDIT,card.rows.last().direction)
    }
    @Test fun malformedFileFailsSafelyAndBankRecordsRemainUnchanged() {
        ready();val before=model().state.value.book
        try{runBlocking {StatementReaderClient.parse(rule.activity,"not a supported workbook".toByteArray())};fail()}catch(_:Exception){}
        assertEquals(before,model().state.value.book)
    }
    @Test fun documentResultParsesOnlyAfterAnExplicitPickerRequest() {
        ready();val vm=model();val file=java.io.File(rule.activity.cacheDir,"fictional-statement.xls").apply {writeBytes(fixture("fictional-bank.xls"))}
        try {
            rule.runOnUiThread {vm.statementFileReturned(android.net.Uri.fromFile(file))}
            assertNull(vm.state.value.statementImport.parsed)
            rule.runOnUiThread {assertTrue(vm.prepareStatementPicker());vm.statementFileReturned(android.net.Uri.fromFile(file))}
            rule.waitUntil(20000){!vm.state.value.statementImport.busy}
            assertNotNull(vm.state.value.statementImport.parsed);assertEquals(3,vm.state.value.statementImport.parsed!!.rows.size)
            rule.onNodeWithText("Review statement").assertExists()
            rule.runOnUiThread {vm.cancelStatement()}
        }finally{file.delete()}
    }
    @Test fun cardReviewSaveMonthAndReopenKeepSettlementOutOfSpending() {
        ready()
        val parsed=runBlocking {StatementReaderClient.parse(rule.activity,fixture("fictional-card.xls"))}
        val vm=model()
        rule.runOnUiThread {rule.activity.setContentForStatementTest(parsed,vm)}
        rule.onNodeWithText("Review statement").assertExists()
        rule.onNodeWithText("Confirm card spending").performScrollTo().performClick()
        // The remaining credit is deliberately classified by the user, not inferred from its sign.
        rule.onNodeWithTag("statement-category-${parsed.rows.last().index}").performScrollTo().performClick()
        rule.onNodeWithText("Card repayment").performClick()
        rule.onNodeWithTag("save-statement").performClick()
        rule.waitUntil(15000){!vm.state.value.saving && vm.state.value.statements.imports.isNotEmpty()}
        assertNull(vm.state.value.formError)
        val total=vm.state.value.statements.totals(YearMonth.of(2026,10))
        assertEquals("100.00",total.netSpending.toPlainString());assertEquals(0,total.bankCashChange.signum())
        assertEquals(2,vm.state.value.statements.lines.size)
        val batch=vm.state.value.statements.imports.single()
        rule.runOnUiThread {rule.activity.setContentForSavedStatementTest(vm,batch.id)}
        rule.onNodeWithText("Revise statement").assertExists()
    }
}

// No SavedState carries statement rows; this test composition is restricted to the disposable UI Lab.
private fun MainActivity.setContentForStatementTest(parsed:ParsedStatement,vm:BankViewModel) {
    setContent {val state by vm.state.collectAsState();WealthTheme {StatementReviewScreen(state.copy(statementImport=StatementImportState(parsed=parsed)),false,vm,null){}}}
}
private fun MainActivity.setContentForSavedStatementTest(vm:BankViewModel,id:java.util.UUID) {
    setContent {val state by vm.state.collectAsState();WealthTheme {StatementReviewScreen(state,false,vm,id){}}}
}
