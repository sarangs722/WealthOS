package com.wealthos.app

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyGateTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun screenCaptureIsAvailableAfterActivityRecreation() {
        check(rule.activity.packageName == "com.wealthos.app.uxlab")
        val preferences = PrivacyPreferences(rule.activity)
        val previous = preferences.get("appLock")
        try {
            preferences.set("appLock", false)
            rule.activityRule.scenario.recreate()
            val model = androidx.lifecycle.ViewModelProvider(rule.activity)[BankViewModel::class.java]
            rule.waitUntil(15000) { !model.state.value.loading }
            rule.runOnUiThread { model.demo(true) }
            rule.onNodeWithText("Demo · Fictional records").assertExists()
            rule.activityRule.scenario.onActivity {
                assertFalse(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
            val screenshot = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try {
                val colors = (1..12).flatMap { x -> (1..12).map { y -> screenshot.getPixel(screenshot.width * x / 13, screenshot.height * y / 13) } }.toSet()
                assertTrue("Screenshot must contain the visible fictional UI", colors.size > 4)
                java.io.File(rule.activity.filesDir, "ux-capture-enabled.png").outputStream().use {
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { screenshot.recycle(); rule.runOnUiThread { model.demo(false) } }
        } finally { preferences.set("appLock", previous) }
    }

    @Test fun persistedAccessLockGatesRecordsWhileAllowingScreenCapture() {
        val preferences=PrivacyPreferences(rule.activity)
        val previous=preferences.get("appLock")
        try {
            rule.activityRule.scenario.onActivity {
                assertFalse(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                preferences.set("appLock",true)
            }
            rule.activityRule.scenario.recreate()
            rule.activityRule.scenario.onActivity {
                assertFalse(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
            rule.onNodeWithText("Unlock WealthOS").assertExists()
            rule.onNodeWithTag("home-list").assertDoesNotExist()
            rule.onNodeWithText("Portfolio").assertDoesNotExist()
        } finally {
            preferences.set("appLock",previous)
        }
    }
}
