package com.wealthos.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.wealthos.finance.*
import java.time.*
import java.util.UUID
import org.junit.Rule
import org.junit.Test

class PublicReferencePrivacyTest {
    @get:Rule val rule=createComposeRule()
    @Test fun cachedReferenceAmountsAndRawInputsAreObscured(){
        val d=PublicDatum(PublicProvider.MANUAL,PublicKind.BANK_RATE,"test","Fictional reference","7.123456789012".toBigDecimal(),null,"percent",LocalDate.now(),"https://example.com/","MANUAL".let(PublicValidation::valueOf),"raw private basis 54321","sensitive input 98765","Test only")
        rule.setContent {WealthTheme {PublicObservationCard(PublicObservation(UUID.randomUUID(),d,Instant.now(),1,""),true,LocalDate.now())}}
        rule.onNodeWithText("••••••").assertExists();rule.onAllNodes(hasText("7.123456789012",substring=true)).assertCountEquals(0);rule.onAllNodes(hasText("54321",substring=true)).assertCountEquals(0);rule.onAllNodes(hasText("98765",substring=true)).assertCountEquals(0)
    }
}
