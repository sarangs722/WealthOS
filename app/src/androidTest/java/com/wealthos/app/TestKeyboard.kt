package com.wealthos.app

import android.content.Context
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry

/** Fixture-only keyboard synchronization; never depends on an IME result callback. */
internal fun MainActivity.dismissKeyboardForTest() {
    val instrumentation=InstrumentationRegistry.getInstrumentation()
    val deadline=SystemClock.uptimeMillis()+10_000
    var hidden=false
    do {
        instrumentation.runOnMainSync {
            currentFocus?.clearFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(window.decorView.windowToken,0)
            hidden=ViewCompat.getRootWindowInsets(window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime())!=true
        }
        if(!hidden)SystemClock.sleep(10)
    }while(!hidden && SystemClock.uptimeMillis()<deadline)
    check(hidden){"Test keyboard did not become hidden within 10 seconds."}
    instrumentation.waitForIdleSync()
}
