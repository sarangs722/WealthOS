package com.wealthos.app

import android.content.*
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** IPC-only check also runs against R8 output; deliberately does not link to obfuscated app classes. */
class StatementReleaseReaderTest {
    @Test fun packagedReaderParsesBothFictionalXlsFilesInIsolation() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        check(context.packageName=="com.wealthos.app.uxlab")
        for((file,kind) in listOf("fictional-bank.xls" to "HDFC_BANK","fictional-card.xls" to "HDFC_NEU_CARD")) {
            val bytes=instrumentation.context.assets.open("statements/$file").use {it.readBytes()};val latch=CountDownLatch(1);var result:Bundle?=null
            val endpoint=Messenger(Handler(Looper.getMainLooper()){message->result=Bundle(message.data);latch.countDown();true})
            val connection=object:ServiceConnection {
                override fun onServiceConnected(name:ComponentName,binder:IBinder){Messenger(binder).send(Message.obtain().apply {replyTo=endpoint;data=Bundle().apply {putByteArray("file",bytes)}})}
                override fun onServiceDisconnected(name:ComponentName){latch.countDown()}
            }
            assertTrue(context.bindService(Intent().setClassName(context.packageName,"com.wealthos.app.StatementReaderService"),connection,Context.BIND_AUTO_CREATE))
            try{assertTrue(latch.await(20,TimeUnit.SECONDS));val reply=requireNotNull(result);assertNull(reply.getString("error"));val json=JSONObject(requireNotNull(reply.getString("result")));assertEquals(kind,json.getString("kind"));assertEquals(3,json.getJSONArray("rows").length())}
            finally{context.unbindService(connection);bytes.fill(0)}
        }
    }
}
