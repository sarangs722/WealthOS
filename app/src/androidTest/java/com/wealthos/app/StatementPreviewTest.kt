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
import java.time.LocalDate

/** Actual parser-to-preview checks with independently fictional XLS assets; no financial saves. */
class StatementPreviewTest {
    @get:Rule val rule = createComposeRule()

    private fun reveal(matcher: SemanticsMatcher) {
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
    }

    private fun revealRow(index: Int) = reveal(hasTestTag("statement-category-$index"))

    private fun chooseSeptember() {
        reveal(hasText("Month: Oct 2026 (2)"))
        rule.onNodeWithText("Month: Oct 2026 (2)").performClick()
        rule.onNodeWithText("Sep 2026 (1)").performClick()
    }

    private fun showFixture(name: String, change: (ParsedStatement) -> ParsedStatement = { it }): ParsedStatement {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        check(context.packageName == "com.wealthos.app.uxlab")
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("statements/$name").use { it.readBytes() }
        val parsed = try { change(runBlocking { StatementReaderClient.parse(context, bytes) }) }
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

    @Test fun bankStartsWithRequestedPeriodInsteadOfOneLaterPosting() {
        showFixture("fictional-bank.xls") { parsed ->
            parsed.copy(periodStart = LocalDate.parse("2026-09-01"), periodEnd = LocalDate.parse("2026-09-30"),
                rows = parsed.rows.map { if (it.index == 6) it.copy(date = LocalDate.parse("2026-09-30")) else it })
        }
        rule.onNodeWithText("Month: Sep 2026 (2)").assertExists()
        rule.onNodeWithText("Transactions: 2 of 3 · 2 to review").assertExists()
        revealRow(5)
        rule.onNodeWithText("Fictional shop").assertExists()
    }

    @Test fun cardStartsWithMostPopulatedMonthAndKeepsSmallOtherMonthSelectable() {
        showFixture("fictional-card.xls") { parsed ->
            parsed.copy(rows = parsed.rows.map { if (it.index == 4) it.copy(date = LocalDate.parse("2026-09-30")) else it })
        }
        rule.onNodeWithText("Month: Sep 2026 (2)").assertExists()
        rule.onNodeWithText("Transactions: 2 of 3 · 2 to review").assertExists()
        reveal(hasText("Month: Sep 2026 (2)"))
        rule.onNodeWithText("Month: Sep 2026 (2)").performClick()
        rule.onNodeWithText("Oct 2026 (1)").performClick()
        rule.onNodeWithText("Transactions: 1 of 3 · 1 to review").assertExists()
        revealRow(5)
        rule.onNodeWithText("Fictional card payment").assertExists()
    }

    @Test fun bankPreviewKeepsAllRowsReachableByTransactionMonthAndPreservesValueDate() {
        assertEquals(3, showFixture("fictional-bank.xls").rows.size)
        rule.onNodeWithText("Month: Oct 2026 (2)").assertExists()
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
