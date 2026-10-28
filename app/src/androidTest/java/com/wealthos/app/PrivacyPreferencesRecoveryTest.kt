package com.wealthos.app

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Properties
import java.util.UUID

/** Models interrupted platform AtomicFile writes, without touching the real privacy file or vault. */
class PrivacyPreferencesRecoveryTest {
    private fun fixture(run:(File,android.content.Context)->Unit){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"privacy-recovery-${UUID.randomUUID()}")
        assertTrue(directory.mkdir())
        val isolated=object:ContextWrapper(context){override fun getNoBackupFilesDir()=directory}
        try{run(directory,isolated)}finally{
            for(name in listOf("privacy.properties","privacy.properties.bak","privacy.properties.new")){
                val file=File(directory,name);if(file.exists())assertTrue(file.delete())
            }
            assertTrue(directory.delete())
        }
    }
    private fun committedBackup(directory:File){
        val properties=Properties().apply {setProperty("appLock","true");setProperty("hideAmounts","true")}
        File(directory,"privacy.properties.bak").outputStream().use {properties.store(it,null)}
    }
    @Test fun backupOnlyInterruptedWriteRetainsLockAndAmountPrivacy(){fixture {directory,context->
        committedBackup(directory)
        assertFalse(File(directory,"privacy.properties").exists())
        val preferences=PrivacyPreferences(context)
        assertTrue("The committed access lock must survive a backup-only interrupted write",preferences.get("appLock"))
        assertTrue(preferences.get("hideAmounts"))
        assertTrue(File(directory,"privacy.properties").exists())
        assertFalse(File(directory,"privacy.properties.bak").exists())
        preferences.set("hideAmounts",false)
        val reopened=PrivacyPreferences(context)
        assertTrue(reopened.get("appLock"));assertFalse(reopened.get("hideAmounts"))
    }}
    @Test fun committedBackupWinsOverIncompleteBase(){fixture {directory,context->
        committedBackup(directory)
        File(directory,"privacy.properties").writeText("appLock=false\nhideAmounts=false\n")
        val preferences=PrivacyPreferences(context)
        assertTrue(preferences.get("appLock"));assertTrue(preferences.get("hideAmounts"))
    }}
    @Test fun genuinelyNewPreferencesUseDefaultsWithoutCreatingFiles(){fixture {directory,context->
        val preferences=PrivacyPreferences(context)
        assertFalse(preferences.get("appLock"));assertFalse(preferences.get("hideAmounts"))
        assertTrue(directory.listFiles()!!.isEmpty())
    }}
}
