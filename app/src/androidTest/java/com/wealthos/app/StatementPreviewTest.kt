package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.wealthos.finance.ParsedStatement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Actual parser-to-preview checks with independently fictional XLS assets; no financial saves. */
class StatementPreviewTest {
    @get:Rule val rule = createComposeRule()

    private fun reveal(matcher: SemanticsMatcher) {
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
    }

    private fun revealRow(index: Int) = reveal(hasTestTag("statement-category-$index"))

    private fun chooseSeptember() {
        reveal(hasText("Month: 2026-10"))
        rule.onNodeWithText("Month: 2026-10").performClick()
        rule.onNodeWithText("2026-09").performClick()
    }

    private fun showFixture(name: String): ParsedStatement {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        check(context.packageName == "com.wealthos.app.uxlab")
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("statements/$name").use { it.readBytes() }
        val parsed = try { runBlocking { StatementReaderClient.parse(context, bytes) } }
            finally { bytes.fill(0) }
        rule.setContent {
            WealthTheme {
                val model: BankViewModel = viewModel()
                StatementReviewScreen(
                    BankUiState(loading = false, statementImport = StatementImportState(parsed = parsed)),
                    false, model, null
                ) {}
            }
        }
        return parsed
    }

    @Test fun bankPreviewKeepsAllRowsReachableByTransactionMonthAndPreservesValueDate() {
        assertEquals(3, showFixture("fictional-bank.xls").rows.size)
        rule.onNodeWithText("Month: 2026-10").assertExists()
        revealRow(6)
        rule.onNodeWithText("Fictional salary").assertExists()
        rule.onNodeWithText("1 Oct 2026 · Credit ₹500.00").assertExists()
        revealRow(7)
        rule.onNodeWithText("Fictional bill").assertExists()
        rule.onNodeWithText("2 Oct 2026 · Debit ₹74.75").assertExists()
        rule.onNodeWithTag("statement-category-5").assertDoesNotExist()

        chooseSeptember()
        revealRow(5)
        rule.onNodeWithText("Fictional shop").assertExists()
        rule.onNodeWithText("30 Sep 2026 · Debit ₹125.25").assertExists()
        rule.onNodeWithText("Value date 1 Oct 2026").assertExists()
        rule.onNodeWithTag("statement-category-6").assertDoesNotExist()
        rule.onNodeWithTag("statement-category-7").assertDoesNotExist()
    }

    @Test fun cardPreviewCanRevealReviewedPurchasesAndOtherMonthWithoutDroppingSourceRows() {
        assertEquals(3, showFixture("fictional-card.xls").rows.size)
        revealRow(4)
        rule.onNodeWithText("Fictional shop").assertExists()
        rule.onNodeWithText("1 Oct 2026 · Debit ₹100.00").assertExists()
        revealRow(5)
        rule.onNodeWithText("Fictional card payment").assertExists()
        rule.onNodeWithText("2 Oct 2026 · Credit ₹1,000.00").assertExists()

        reveal(hasText("Confirm card spending"))
        rule.onNodeWithText("Confirm card spending").performClick()
        rule.onNodeWithTag("statement-category-4").assertDoesNotExist()
        reveal(hasContentDescription("Only items needing review"))
        rule.onNodeWithContentDescription("Only items needing review").assertIsOn().performClick()
        revealRow(4)
        rule.onNodeWithText("Fictional shop").assertExists()
        rule.onNodeWithText("1 Oct 2026 · Debit ₹100.00").assertExists()

        chooseSeptember()
        revealRow(3)
        rule.onNodeWithText("Fictional groceries").assertExists()
        rule.onNodeWithText("30 Sep 2026 · Debit ₹123.45").assertExists()
        rule.onNodeWithTag("statement-category-4").assertDoesNotExist()
        rule.onNodeWithTag("statement-category-5").assertDoesNotExist()
    }
}
