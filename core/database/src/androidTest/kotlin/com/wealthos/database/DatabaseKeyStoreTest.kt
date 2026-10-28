package com.wealthos.database

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseKeyStoreTest {
    private fun isolated(test: (Context, File) -> Unit) {
        val base=ApplicationProvider.getApplicationContext<Context>()
        val directory=File(base.cacheDir,"key-test-${java.util.UUID.randomUUID()}").apply {mkdirs()}
        val context=object:ContextWrapper(base) {
            override fun getNoBackupFilesDir()=directory
            override fun getDatabasePath(name:String)=File(directory,name)
        }
        try {test(context,directory)} finally {directory.deleteRecursively()}
    }
    @Test fun wrappedKeyRoundTripAndTamperRejection() = isolated {context,directory ->
        val store=DatabaseKeyStore(context)
        val original=store.loadOrCreate()
        assertEquals(32,original.size)
        assertArrayEquals(original,store.loadOrCreate())
        val envelope=File(directory,"database-key.v1")
        val bytes=envelope.readBytes()
        assertEquals(61,bytes.size)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        envelope.writeBytes(bytes)
        assertThrows(Exception::class.java) {store.loadOrCreate()}
        assertArrayEquals(bytes,envelope.readBytes())
        original.fill(0)
    }
    @Test fun existingDatabaseWithoutKeyIsNeverReset() = isolated {context,directory ->
        val database=File(directory,"wealthos.db").apply {writeText("preserve existing bytes")}
        assertThrows(IllegalArgumentException::class.java) {DatabaseKeyStore(context).loadOrCreate()}
        assertEquals("preserve existing bytes",database.readText())
        assertFalse(File(directory,"database-key.v1").exists())
    }
    @Test fun independentConcurrentLoadersAllReturnTheSamePersistedKey() = isolated {context,_ ->
        val pool=Executors.newFixedThreadPool(8)
        val start=CyclicBarrier(8)
        val keys=mutableListOf<ByteArray>()
        try {
            val requests=(1..8).map {pool.submit<ByteArray> {start.await(20,TimeUnit.SECONDS);DatabaseKeyStore(context).loadOrCreate()}}
            requests.forEach {keys+=it.get(60,TimeUnit.SECONDS)}
            val persisted=DatabaseKeyStore(context).loadOrCreate()
            try{keys.forEach {assertArrayEquals("Concurrent startup must not replace the database password",persisted,it)}}finally{persisted.fill(0)}
        }finally{pool.shutdownNow();pool.awaitTermination(10,TimeUnit.SECONDS);keys.forEach {it.fill(0)}}
    }
}
