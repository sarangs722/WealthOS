package com.wealthos.app

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModelProvider
import com.wealthos.finance.*
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackupWorkflowTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private lateinit var model:BankViewModel
    private val pass="Fictional UI portable passphrase 2026"
    private fun ready(){
        rule.waitUntil(30_000){rule.onAllNodesWithTag("home-list").fetchSemanticsNodes().isNotEmpty()}
        rule.activityRule.scenario.onActivity {model=ViewModelProvider(it)[BankViewModel::class.java]}
    }
    private fun idle(){rule.waitUntil(60_000){!model.state.value.saving};assertNull(model.state.value.formError)}
    private fun backups(){
        // Preparing an export/restore can already navigate to this screen.
        if(rule.onAllNodesWithTag("backup-screen").fetchSemanticsNodes().isEmpty()){
            rule.onNodeWithContentDescription("Settings").performClick()
            rule.onNodeWithText("Manage backups").performScrollTo().performClick()
        }
        rule.onNodeWithText("Your vault, beyond this device").assertExists()
    }
    private fun add(name:String,amount:String){rule.runOnIdle{model.add(name,"Fictional",CurrencyCode.INR,AccountKind.SAVINGS,amount,LocalDate.now().toString()) {}};idle();rule.waitUntil(10_000){model.state.value.book.accounts.any {it.name==name}}}
    @Test fun passwordConfirmationAndRecreationDoNotPersistPassword(){
        ready();backups()
        rule.onNodeWithText("Backup password").performScrollTo().performTextInput(pass)
        rule.onNodeWithText("Confirm backup password").performScrollTo().performTextInput("Different portable password")
        rule.onNodeWithText("Create encrypted backup").performScrollTo().performClick()
        rule.onNodeWithTag("backup-screen").performScrollToNode(hasText("The backup passwords do not match."))
        rule.onNodeWithText("The backup passwords do not match.").assertExists()
        assertNull(model.state.value.backup.exportId)
        rule.activityRule.scenario.recreate()
        rule.waitUntil(30_000){rule.onAllNodesWithTag("backup-screen").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Backup password").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
        rule.onNodeWithText("Confirm backup password").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
        rule.activityRule.scenario.onActivity {assertTrue(it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0)}
    }
    @Test fun exportReadbackPreviewCancelAndConfirmedReplacementKeepHistory(){
        ready();val suffix=System.nanoTime();val name="Fictional backup UI $suffix";add(name,"100.10")
        val id=model.state.value.book.accounts.single {it.name==name}.id
        rule.runOnIdle{model.balance(id,CurrencyCode.INR,"200.20",LocalDate.now().toString(),"Fictional retained correction") {}};idle()
        rule.waitUntil(10_000){model.state.value.book.observations.count {it.accountId==id}==2}
        val original=model.state.value.book
        val file=File(rule.activity.filesDir,"ui-backup-$suffix.wosbackup")
        try {
            rule.runOnIdle{model.prepareExport(pass.toCharArray())};idle();assertNotNull(model.state.value.backup.exportId)
            rule.runOnIdle{model.writeExport(Uri.fromFile(file))};idle();assertTrue(file.length()>88);assertTrue(model.state.value.backup.message!!.startsWith("Encrypted backup saved"))
            add("Fictional after export $suffix","77.00");val current=model.state.value.book.accounts.size
            backups()
            rule.runOnIdle{model.prepareRestore(Uri.fromFile(file),"Wrong long backup password".toCharArray())}
            rule.waitUntil(60_000){!model.state.value.saving};assertTrue(model.state.value.formError!!.startsWith("Incorrect password"));assertEquals(current,model.state.value.book.accounts.size)
            rule.runOnIdle{model.prepareRestore(Uri.fromFile(file),pass.toCharArray())};idle()
            rule.onNodeWithText("Replace this financial vault?").assertExists()
            rule.onNodeWithText("Replace from validated backup").assertIsNotEnabled()
            rule.onNodeWithText("Cancel restore").performClick();assertEquals(current,model.state.value.book.accounts.size)
            rule.runOnIdle{model.prepareRestore(Uri.fromFile(file),pass.toCharArray())};idle()
            rule.onNodeWithContentDescription("Confirm replacement of current records").performScrollTo().performClick()
            rule.onNodeWithText("Replace from validated backup").performClick();idle()
            rule.waitUntil(10_000){model.state.value.book.accounts.size==original.accounts.size}
            assertEquals(original.observations.sortedBy {it.id},model.state.value.book.observations.sortedBy {it.id})
            assertFalse(model.state.value.book.publicData.networkEnabled);assertTrue(model.state.value.backup.safety.isNotEmpty())
            assertTrue(model.state.value.backup.message!!.startsWith("Financial backup restored"))
        }finally{file.delete()}
    }
    @Test fun truncatedDocumentProviderCannotReportExportSuccessOrChangeVault(){
        ready();val before=model.state.value.book
        rule.runOnIdle{model.prepareExport(pass.toCharArray())};idle()
        // App lock may dispose the screen while a SAF picker is open; its ciphertext must survive.
        rule.runOnIdle{model.leaveBackups()};assertNotNull(model.state.value.backup.exportId)
        val authority=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.packageName+".truncated-backup"
        rule.runOnIdle{model.writeExport(Uri.parse("content://$authority/fictional-only"))}
        rule.waitUntil(60_000){!model.state.value.saving}
        assertTrue(model.state.value.formError!!.startsWith("Backup could not be verified"))
        assertNull(model.state.value.backup.exportId);assertEquals(before,model.state.value.book)
        assertFalse(model.state.value.backup.message?.startsWith("Encrypted backup saved")==true)
    }
}
