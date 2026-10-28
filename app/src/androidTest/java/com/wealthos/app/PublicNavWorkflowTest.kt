package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.wealthos.finance.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import org.junit.Rule
import org.junit.Test

/** Synthetic app fixture, including contentResolver file reading and explicit financial posting. */
class PublicNavWorkflowTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun linkImportReviewAndApplyOnlySelectedNavWithoutNetwork(){
        val context=ApplicationProvider.getApplicationContext<android.content.Context>();val day=LocalDate.now();val id=UUID.randomUUID();val name="Fictional NAV ${System.nanoTime()}"
        rule.waitUntil(30_000){rule.onAllNodesWithTag("home-list").fetchSemanticsNodes().isNotEmpty()}
        rule.activityRule.scenario.onActivity {activity->ViewModelProvider(activity)[BankViewModel::class.java].portfolio({}) {repo->
            repo.addInstrument(NewInstrument(Instrument(id,name,AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.TRANSACTIONS,day,Instant.now()),openingUnits="10".toBigDecimal(),openingCost="100.00".toBigDecimal()))
        }}
        rule.waitUntil(20_000){ViewModelProvider(rule.activity)[BankViewModel::class.java].state.value.book.portfolio.instruments.any {it.id==id}}
        rule.onNodeWithContentDescription("Settings").performClick();rule.onNodeWithText("Public data & sources").performScrollTo().performClick()
        rule.onNodeWithTag("public-data-list").performScrollToNode(hasTestTag("nav-binding:$id"));rule.onNodeWithTag("nav-binding:$id").performClick()
        rule.onNodeWithText("AMFI scheme code").performTextInput("123456");rule.onNodeWithText("ISIN (optional)").performTextInput("INF000000001");rule.onNodeWithText("Save NAV binding").performClick()
        rule.waitUntil(20_000){rule.onAllNodesWithTag("public-data-list").fetchSemanticsNodes().isNotEmpty()}
        rule.waitUntil(20_000){ViewModelProvider(rule.activity)[BankViewModel::class.java].state.value.book.publicData.currentBindings().any {it.instrumentId==id}}
        val file=java.io.File(context.filesDir,"synthetic-selected-nav.txt")
        file.writeText("Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Plan;Option;Net Asset Value;Date\n123456;INF000000001;;Fictional fixture fund;Direct;Growth;15.123456;${day.minusDays(1).format(DateTimeFormatter.ofPattern("dd-MMM-uuuu",Locale.ENGLISH))}\n999999;;;Unselected fictional fund;Direct;Growth;100;${day.minusDays(1).format(DateTimeFormatter.ofPattern("dd-MMM-uuuu",Locale.ENGLISH))}")
        try {
            rule.activityRule.scenario.onActivity {activity->val model=ViewModelProvider(activity)[BankViewModel::class.java];val binding=model.state.value.book.publicData.currentBindings().single {it.instrumentId==id};model.importNav(android.net.Uri.fromFile(file),binding.id)}
            rule.waitUntil(20_000){rule.activity.let {ViewModelProvider(it)[BankViewModel::class.java].state.value.book.publicData.observations.any {o->o.datum.series=="NAV:123456"}}}
            rule.onNodeWithTag("public-data-list").performScrollToNode(hasText("Review NAV · $name"));rule.onNodeWithText("Review NAV · $name").performClick()
            rule.onNodeWithText("Apply reviewed reference?").assertExists();rule.onNodeWithText("Confirm application").performClick()
            rule.waitUntil(20_000){rule.activity.let {ViewModelProvider(it)[BankViewModel::class.java].state.value.book.publicData.applications.any {a->a.instrumentId==id}}}
            rule.onNodeWithContentDescription("Back").performClick();rule.onNodeWithText("Portfolio",useUnmergedTree=true).performClick();rule.onNodeWithText("Search portfolio").performTextInput(name)
            rule.onNodeWithTag("portfolio-list").performScrollToNode(hasText(name) and !hasSetTextAction());rule.onNode(hasText(name) and !hasSetTextAction()).performClick()
            rule.onNodeWithText("₹151.23").assertExists()
            rule.onNode(hasScrollAction()).performScrollToNode(hasText("10 units"));rule.onNodeWithText("10 units").assertExists()
            rule.onNode(hasScrollAction()).performScrollToNode(hasText("Remaining FIFO cost: ₹100.00"));rule.onNodeWithText("Remaining FIFO cost: ₹100.00").assertExists()
        }finally{file.delete()}
    }
}
