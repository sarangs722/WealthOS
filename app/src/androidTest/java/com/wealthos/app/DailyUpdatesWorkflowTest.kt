package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.wealthos.finance.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class DailyUpdatesWorkflowTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun model()=ViewModelProvider(rule.activity)[BankViewModel::class.java]
    private fun state()=model().state.value
    private fun write(label:String,value:String){rule.activity.dismissKeyboardForTest();rule.onNodeWithText(label).performScrollTo().performTextReplacement(value);rule.activity.dismissKeyboardForTest()}
    private fun click(text:String){rule.onNodeWithText(text).performScrollTo().performClick()}
    private fun choose(label:String,value:String){rule.onNode(hasText("$label:",substring=true) and hasClickAction()).performScrollTo().performClick();click(value)}
    private fun open(i:Instrument){rule.onNodeWithTag("nav-1").performClick();rule.onNodeWithTag("portfolio-list").performScrollToNode(hasText(i.name));rule.onNodeWithText(i.name).performClick()}
    private fun seed(i:Instrument,contract:FinancialContract?=null){
        rule.activityRule.scenario.onActivity {model().portfolio({}) {repo->repo.addInstrument(NewInstrument(i,
            openingUnits=if(i.tracking==TrackingMode.TRANSACTIONS)BigDecimal.TEN else BigDecimal.ZERO,
            openingCost=if(i.tracking==TrackingMode.TRANSACTIONS)"1000".toBigDecimal() else null,
            initialValue=if(i.tracking==TrackingMode.TRANSACTIONS)"100".toBigDecimal() else null,contract=contract))}}
        rule.waitUntil(30_000){state().book.portfolio.instruments.any {it.id==i.id} && !state().saving}
    }
    @Test fun collapsedFeesAndExplicitCashTreatmentPersistWithoutDoubleCounting(){
        rule.waitForHome();val day=LocalDate.now().minusDays(1);val suffix=System.nanoTime();val bankName="Fictional cash $suffix"
        rule.activityRule.scenario.onActivity {model().add(bankName,"",CurrencyCode.INR,AccountKind.SAVINGS,"10000",day.toString(),{})}
        rule.waitUntil(30_000){state().book.accounts.any {it.name==bankName} && !state().saving}
        val bank=state().book.accounts.single {it.name==bankName}
        val i=Instrument(UUID.randomUUID(),"Fictional fee fund $suffix",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.TRANSACTIONS,day,Instant.now())
        seed(i);open(i);click("Add transaction")
        rule.onNodeWithText("Fees / withholding in INR").assertDoesNotExist()
        write("Units","2");write("Total amount in INR","200");rule.chooseEntryDate("Event date",day)
        click("Fees & note (optional)");write("Fees / withholding in INR","5");write("Explanation / note","Fictional statement charge");click("Fees & note (optional)")
        rule.onNodeWithText("Fees: 5 INR · Note added").assertExists()
        choose("Cash account","$bankName (INR)")
        rule.onNodeWithText("Save event").performClick()
        rule.waitUntil(30_000){state().formError!=null && !state().saving}
        assertEquals(1,state().book.portfolio.entries.count {it.instrumentId==i.id})
        choose("Cash account treatment","Apply provisional cash adjustment");rule.onNodeWithText("Save event").performClick()
        rule.waitUntil(30_000){state().book.portfolio.entries.count {it.instrumentId==i.id}==2 && !state().saving}
        rule.activityRule.scenario.recreate()
        rule.waitUntil(30_000){state().book.portfolio.entries.count {it.instrumentId==i.id}==2}
        val p=state().book.portfolio;val entry=p.entries.single {it.instrumentId==i.id && it.kind==TradeKind.BUY}
        assertEquals(day,entry.date);assertEquals("Fictional statement charge",entry.note);assertEquals(0,"5".toBigDecimal().compareTo(entry.fees))
        val holding=Holdings.calculate(p.entries.filter {it.instrumentId==i.id},day)
        assertEquals(0,"12".toBigDecimal().compareTo(holding.units));assertEquals(0,"1205".toBigDecimal().compareTo(holding.remainingCost))
        assertEquals(0,"-205".toBigDecimal().compareTo(p.cashMovements.single {it.accountId==bank.id}.amount))
        assertEquals(0,"9795".toBigDecimal().compareTo(BankValuation.at(state().book,day).inputs.single {it.account.id==bank.id}.value))
    }
    @Test fun loanPaymentKeepsPrincipalInterestAndCollapsedFeesSeparate(){
        rule.waitForHome();val day=LocalDate.now().minusDays(1)
        val i=Instrument(UUID.randomUUID(),"Fictional payment ${System.nanoTime()}",AssetCategory.LOAN,CurrencyCode.INR,TrackingMode.LOAN,day,Instant.now())
        seed(i,FinancialContract(i.id,"10000".toBigDecimal(),BigDecimal.ZERO,day,day.plusMonths(12),termMonths=12));open(i);click("Record repayment")
        rule.onNodeWithText("Fees / withholding").assertDoesNotExist()
        write("Principal paid in INR","1000");write("Interest paid in INR","50");rule.chooseEntryDate("Event date",day)
        click("Fees & note (optional)");write("Fees / withholding","10");write("Explanation / statement reference","Fictional loan statement");click("Fees & note (optional)")
        rule.onNodeWithText("Fees: 10 INR · Note added").assertExists();rule.onNodeWithText("Save payment").performClick()
        rule.waitUntil(30_000){state().book.portfolio.contractEvents.any {it.instrumentId==i.id} && !state().saving}
        val event=state().book.portfolio.contractEvents.single {it.instrumentId==i.id}
        assertEquals(day,event.date);assertEquals(0,"1000".toBigDecimal().compareTo(event.principal));assertEquals(0,"50".toBigDecimal().compareTo(event.interest));assertEquals(0,"10".toBigDecimal().compareTo(event.fees));assertEquals("Fictional loan statement",event.note)
        assertTrue(state().book.portfolio.cashMovements.none {it.sourceId==event.id})
        rule.waitUntil(30_000){rule.onAllNodesWithText("₹9,000.00").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun manualPriceCorrectionKeepsRequiredReasonVisibleAndRetainsHistory(){
        rule.waitForHome();val day=LocalDate.now();val i=Instrument(UUID.randomUUID(),"Fictional price ${System.nanoTime()}",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.TRANSACTIONS,day.minusDays(1),Instant.now())
        seed(i);open(i);click("Update price")
        rule.onNodeWithText("Source / correction explanation").assertDoesNotExist()
        write("Unit price in INR","101");rule.onNodeWithText("Save valuation").performClick()
        rule.waitUntil(30_000){state().book.portfolio.valuations.count {it.instrumentId==i.id}==2 && !state().saving}
        click("Update price");rule.onNodeWithText("Source / correction explanation").assertIsDisplayed()
        write("Unit price in INR","102");rule.onNodeWithText("Save valuation").performClick()
        rule.waitUntil(30_000){state().formError!=null && !state().saving}
        assertEquals(2,state().book.portfolio.valuations.count {it.instrumentId==i.id})
        write("Source / correction explanation","Fictional corrected statement");rule.onNodeWithText("Save valuation").performClick()
        rule.waitUntil(30_000){state().book.portfolio.valuations.count {it.instrumentId==i.id}==3 && !state().saving}
        val revisions=state().book.portfolio.valuations.filter {it.instrumentId==i.id && it.date==day}.sortedBy {it.revision}
        assertEquals(listOf(1,2),revisions.map {it.revision});assertEquals(listOf("101","102"),revisions.map {it.value.stripTrailingZeros().toPlainString()})
        assertEquals("Fictional corrected statement",revisions.last().note)
    }
    @Test fun settingsShowsInstalledVersion(){
        rule.waitForHome();rule.onNodeWithContentDescription("Settings").performClick()
        @Suppress("DEPRECATION") val version=rule.activity.packageManager.getPackageInfo(rule.activity.packageName,0).versionName
        rule.onNodeWithText("WealthOS $version").performScrollTo().assertIsDisplayed()
    }
}
