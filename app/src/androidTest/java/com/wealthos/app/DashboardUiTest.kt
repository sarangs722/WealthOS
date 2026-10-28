package com.wealthos.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wealthos.finance.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DashboardUiTest {
    @get:Rule val rule=createComposeRule()
    @Test fun fictionalDemoAndObscuringAmounts() {
        val book=demoBook()
        rule.setContent {
            var hidden by remember {mutableStateOf(false)}
            WealthTheme { Column { Text("DEMO · Fictional data"); HomeScreen(book,hidden,true,{}, {}, {}, {hidden=!hidden}) } }
        }
        rule.onAllNodesWithText("₹3,77,840.50").onFirst().assertExists()
        rule.onNodeWithText("Hide").performClick()
        rule.onNodeWithText("₹3,77,840.50").assertDoesNotExist()
        rule.onNodeWithText("Show").performClick()
        rule.onAllNodesWithText("₹3,77,840.50").onFirst().assertExists()
        // Screenshot fixture contains only explicitly fictional records, never the user's vault.
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir,"demo-dashboard.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
        assertEquals(2,book.accounts.size)
    }
    @Test fun emptyStateDoesNotDisplayDemoAsRealWealth() {
        rule.setContent {WealthTheme {HomeScreen(BankBook(),false,false,{}, {}, {}, {})}}
        rule.onNodeWithText("Your wealth starts here").assertExists()
        rule.onNodeWithText("Add your first record").assertExists()
        rule.onNodeWithText("₹0.00").assertDoesNotExist()
        rule.onNodeWithText("₹3,77,840.50").assertDoesNotExist()
    }
}
