package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BankWorkflowTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun writeForm(label:String,value:String) {
        rule.activity.dismissKeyboardForTest()
        rule.onNodeWithTag("account-form").performScrollToNode(hasText(label))
        rule.onNodeWithText(label).performTextReplacement(value)
        rule.activity.dismissKeyboardForTest()
        rule.onNodeWithText(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(value)))
    }
    private fun save(label:String) {
        rule.activity.dismissKeyboardForTest()
        rule.onNodeWithTag("account-form").performScrollToNode(hasText(label))
        rule.onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()
    }
    @Test fun addUpdateCorrectHistoryAndEditPersistAcrossActivityRecreation() {
        val name="UI savings ${System.currentTimeMillis()}"
        rule.waitUntil(30_000) {rule.onAllNodesWithTag("home-list").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Portfolio",useUnmergedTree=true).performClick()
        rule.addBank()
        writeForm("Account name",name)
        writeForm("Balance in INR","100.10")
        save("Save balance")
        rule.waitUntil(10_000) {rule.onAllNodesWithTag("portfolio-list").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("portfolio-list").performScrollToNode(hasText(name))
        rule.onNodeWithText(name).performClick()
        rule.onNodeWithText("Update balance").performClick()
        writeForm("Balance in INR","200.20")
        save("Save balance")
        val correctionError="Explain the same-day correction; earlier revisions are retained."
        rule.waitUntil(30_000){ViewModelProvider(rule.activity)[BankViewModel::class.java].state.value.formError==correctionError}
        rule.onNodeWithTag("account-form").performScrollToNode(hasText(correctionError));rule.onNodeWithText(correctionError).assertExists()
        writeForm("Note / correction explanation","Corrected statement")
        save("Save balance")
        try{rule.waitUntil(30_000) {
            val book=ViewModelProvider(rule.activity)[BankViewModel::class.java].state.value.book
            val account=book.accounts.singleOrNull {it.name==name}
            account!=null && book.observations.filter {it.accountId==account.id}.map {it.revision}.sorted()==listOf(1,2)
        }}catch(error:Throwable){
            val state=ViewModelProvider(rule.activity)[BankViewModel::class.java].state.value
            val id=state.book.accounts.singleOrNull{it.name==name}?.id
            throw AssertionError("Fictional correction revisions=${state.book.observations.filter{it.accountId==id}.map{it.revision}}; saving=${state.saving}; validation=${state.formError}\n"+rule.onRoot(useUnmergedTree=true).printToString(),error)
        }
        rule.waitUntil(10_000) {rule.onAllNodesWithText("Balance history").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Balance history").performClick()
        val originalRevision="${dateLabel(LocalDate.now())} · Revision 1"
        rule.onNodeWithTag("account-detail").performScrollToNode(hasText(originalRevision))
        rule.onNodeWithText(originalRevision).assertExists()
        rule.onNodeWithTag("account-detail").performScrollToNode(hasText("Superseded · Retained for history"))
        rule.onNodeWithText("Superseded · Retained for history").assertExists()
        rule.onNodeWithTag("account-detail").performScrollToNode(hasText("Edit account"))
        rule.onNodeWithText("Edit account").performClick()
        writeForm("Account name","$name renamed")
        save("Save account")
        rule.waitUntil(10_000) {rule.onAllNodesWithText("$name renamed").fetchSemanticsNodes().isNotEmpty()}
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10_000) {rule.onAllNodesWithText("$name renamed").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("$name renamed").assertExists()
        rule.onNodeWithTag("account-detail").performScrollToNode(hasText("Balance history"))
        rule.onNodeWithText("Balance history").assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Explore demo mode").performScrollTo().performClick()
        rule.onNodeWithText("Demo · Fictional records").assertExists()
        rule.onNodeWithText("$name renamed").assertDoesNotExist()
        rule.onNodeWithText("Exit demo").performClick()
        rule.onNodeWithTag("portfolio-list").performScrollToNode(hasText("$name renamed"))
        rule.onNodeWithText("$name renamed").assertExists()
        // Synthetic test label only; used by the direct old-APK→new-APK retention fixture.
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .openFileOutput("upgrade-account.txt",android.content.Context.MODE_PRIVATE).use {it.write("$name renamed".toByteArray())}
    }
}
