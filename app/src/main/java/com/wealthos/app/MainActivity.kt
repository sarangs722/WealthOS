package com.wealthos.app

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.util.AtomicFile
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.util.Properties

class PrivacyPreferences(context: android.content.Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "privacy.properties"))
    private val properties = Properties().apply {
        // Android 10 can leave only the last committed .bak after an interrupted
        // write. openRead() restores it; checking the base alone loses privacy flags.
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
            file.openRead().use { load(it) }
        }
    }
    fun get(key: String) = properties.getProperty(key) == "true"
    fun set(key: String, value: Boolean) {
        val updated = Properties().apply { putAll(properties); setProperty(key, value.toString()) }
        val output = file.startWrite()
        try { updated.store(output, null); file.finishWrite(output); properties.putAll(updated) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
}

class MainActivity : ComponentActivity() {
    // Activity registrations survive removal of the financial composition by the access lock.
    private val createBackup=registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->ViewModelProvider(this)[BankViewModel::class.java].writeExport(uri)}
    private val chooseBackup=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->ViewModelProvider(this)[BankViewModel::class.java].selectRestoreFile(uri)}
    private val chooseNav=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->ViewModelProvider(this)[BankViewModel::class.java].navFileReturned(uri)}
    private lateinit var preferences: PrivacyPreferences
    private var locked by mutableStateOf(false)
    private var hideAmounts by mutableStateOf(false)
    private var appLock by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var prompt: CancellationSignal? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        preferences = PrivacyPreferences(this)
        appLock = preferences.get("appLock")
        hideAmounts = preferences.get("hideAmounts")
        locked = appLock
        setContent {
            WealthTheme {
                if (locked) LockScreen(message, ::unlock)
                else WealthApp(hideAmounts, appLock, message,
                    createBackupFile={createBackup.launch(it)},
                    chooseBackupFile={chooseBackup.launch(arrayOf("application/octet-stream","application/*","text/*"))},
                    chooseNavFile={id->if(ViewModelProvider(this)[BankViewModel::class.java].prepareNavPicker(id))chooseNav.launch(arrayOf("text/*","application/octet-stream"))},
                    onHide = { savePreference("hideAmounts", !hideAmounts) { hideAmounts = !hideAmounts } },
                    onLock = {
                        if (!appLock && !getSystemService(KeyguardManager::class.java).isDeviceSecure) {
                            message = "Set a screen lock in Android Settings before enabling app lock."
                        } else savePreference("appLock", !appLock) { appLock = !appLock }
                    })
            }
        }
    }
    private fun savePreference(key: String, value: Boolean, done: () -> Unit) {
        try { preferences.set(key, value); message = null; done() }
        catch (_: Exception) { message = "Could not save privacy settings. Please retry." }
    }
    @Suppress("DEPRECATION")
    private fun unlock() {
        if (prompt != null) return
        val cancellation = CancellationSignal()
        prompt = cancellation
        BiometricPrompt.Builder(this).setTitle("Unlock WealthOS")
            .setSubtitle("Use your biometric or device screen lock")
            .setDeviceCredentialAllowed(true).build()
            .authenticate(cancellation, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompt = null; message = null; locked = false
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompt = null; message = "Unlock was not completed. Your records remain locked."
                }
            })
    }
    override fun onStop() {
        super.onStop()
        if (appLock) locked = true
    }
    override fun onDestroy() { prompt?.cancel(); super.onDestroy() }
}
