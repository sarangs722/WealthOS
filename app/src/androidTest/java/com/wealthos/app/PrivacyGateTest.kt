package com.wealthos.app

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyGateTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun persistedAccessLockGatesRecordsAndWindowIsSecure() {
        val preferences=PrivacyPreferences(rule.activity)
        val previous=preferences.get("appLock")
        try {
            rule.activityRule.scenario.onActivity {
                assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                preferences.set("appLock",true)
            }
            rule.activityRule.scenario.recreate()
            rule.onNodeWithText("Unlock WealthOS").assertExists()
            rule.onNodeWithTag("home-list").assertDoesNotExist()
            rule.onNodeWithText("Portfolio").assertDoesNotExist()
        } finally {
            preferences.set("appLock",previous)
        }
    }
}
