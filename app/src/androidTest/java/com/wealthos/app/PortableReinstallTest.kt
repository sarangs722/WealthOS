package com.wealthos.app

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.wealthos.finance.*
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Direct-only, emulator-guarded host fixture; ordinary regression discovers zero cases. */
@RunWith(Parameterized::class)
class PortableReinstallTest(private val phase:String) {
    companion object {
        const val FILE="portable-fixture.v1.wosbackup"
        const val PASSWORD="Fictional reinstall portability 2026"
        @JvmStatic @Parameterized.Parameters(name="Portable reinstall {0}")
        fun parameters():List<Array<String>> = InstrumentationRegistry.getArguments().getString("portablePhase")?.takeIf {it in listOf("seed","restore")}?.let {listOf(arrayOf(it))} ?: emptyList()
    }
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private lateinit var model:BankViewModel
    private fun idle(){rule.waitUntil(60_000){!model.state.value.saving};assertNull(model.state.value.formError)}
    @Test fun exportedBackupSurvivesActualUninstallAndFreshDeviceKey(){
        rule.waitUntil(30_000){rule.onAllNodesWithTag("home-list").fetchSemanticsNodes().isNotEmpty()}
        rule.activityRule.scenario.onActivity {model=ViewModelProvider(it)[BankViewModel::class.java]}
        val file=File(rule.activity.filesDir,FILE);val day=LocalDate.now()
        assertTrue("A fresh empty synthetic installation is required",model.state.value.book.accounts.isEmpty())
        assertTrue(model.state.value.book.portfolio.instruments.isEmpty())
        if(phase=="seed"){
            rule.runOnIdle{model.add("Fictional reinstall bank","Test",CurrencyCode.INR,AccountKind.SAVINGS,"500.01",day.toString()) {}};idle()
            rule.waitUntil(10_000){model.state.value.book.accounts.size==1}
            val id=model.state.value.book.accounts.single().id
            rule.runOnIdle{model.balance(id,CurrencyCode.INR,"650.02",day.toString(),"Retained fictional correction") {}};idle()
            rule.runOnIdle{model.portfolio({}) {it.addInstrument(NewInstrument(Instrument(UUID.randomUUID(),"Fictional reinstall fund",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.TRANSACTIONS,day,Instant.now()),"5.00".toBigDecimal(),"400.00".toBigDecimal(),"100.50".toBigDecimal()))}};idle()
            rule.runOnIdle{model.prepareExport(PASSWORD.toCharArray())};idle()
            rule.runOnIdle{model.writeExport(Uri.fromFile(file))};idle();assertTrue(file.length()>88)
            assertTrue(model.state.value.backup.message!!.startsWith("Encrypted backup saved"))
        }else{
            assertTrue("Host must return the same encrypted file after uninstall",file.isFile)
            rule.onNodeWithContentDescription("Settings").performClick();rule.onNodeWithText("Manage backups").performScrollTo().performClick()
            rule.runOnIdle{model.prepareRestore(Uri.fromFile(file),PASSWORD.toCharArray())};idle()
            rule.onNodeWithText("Replace from validated backup").assertIsNotEnabled()
            rule.onNodeWithContentDescription("Confirm replacement of current records").performScrollTo().performClick()
            rule.onNodeWithText("Replace from validated backup").performClick();idle()
            rule.waitUntil(10_000){model.state.value.book.accounts.size==1}
            val book=model.state.value.book;assertEquals("Fictional reinstall bank",book.accounts.single().name)
            assertEquals(setOf(BigDecimal("500.01"),BigDecimal("650.02")),book.observations.map {it.amount}.toSet())
            assertEquals(listOf(1,2),book.observations.sortedBy {it.revision}.map {it.revision})
            val fund=book.portfolio.instruments.single();assertEquals("Fictional reinstall fund",fund.name)
            val position=Holdings.calculate(book.portfolio.entries.filter {it.instrumentId==fund.id},day)
            assertEquals(0,BigDecimal("5.00").compareTo(position.units));assertEquals(0,BigDecimal("400.00").compareTo(position.remainingCost!!))
            assertEquals(0,BigDecimal("1152.52").compareTo(WealthValuation.at(book,day).netWorthInr!!));assertFalse(book.publicData.networkEnabled)
            assertTrue(model.state.value.backup.safety.isNotEmpty())
        }
    }
}
