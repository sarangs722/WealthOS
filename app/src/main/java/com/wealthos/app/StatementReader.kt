package com.wealthos.app

import android.app.Service
import android.content.*
import android.os.*
import com.wealthos.finance.*
import com.wealthos.imports.HdfcStatements
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object StatementWire {
    fun encode(value:ParsedStatement):String=JSONObject().put("kind",value.kind.name).put("hash",value.fileHash)
        .put("first",value.firstCovered.toString()).put("last",value.lastCovered.toString()).put("version",value.parserVersion)
        .put("currency",value.currency.name)
        .put("periodStart",value.periodStart?.toString() ?: JSONObject.NULL).put("periodEnd",value.periodEnd?.toString() ?: JSONObject.NULL)
        .put("rows",JSONArray().also {rows->value.rows.forEach {r->rows.put(JSONObject().put("index",r.index).put("date",r.date.toString())
            .put("valueDate",r.valueDate?.toString() ?: JSONObject.NULL).put("description",r.description).put("reference",r.reference)
            .put("amount",r.amount.toPlainString()).put("direction",r.direction.name))}}).toString().also {require(it.length<=120000)}
    fun decode(text:String):ParsedStatement {
        require(text.length<=120000);val json=JSONObject(text);val rows=json.getJSONArray("rows");require(rows.length() in 1..1500)
        return ParsedStatement(StatementKind.valueOf(json.getString("kind")),json.getString("hash"),LocalDate.parse(json.getString("first")),LocalDate.parse(json.getString("last")),
            (0 until rows.length()).map {val r=rows.getJSONObject(it);StatementInputRow(r.getInt("index"),LocalDate.parse(r.getString("date")),if(r.isNull("valueDate"))null else LocalDate.parse(r.getString("valueDate")),r.getString("description"),r.getString("reference"),Amounts.parse(r.getString("amount"),CurrencyCode.INR),StatementDirection.valueOf(r.getString("direction")))},json.getInt("version"),if(json.isNull("periodStart"))null else LocalDate.parse(json.getString("periodStart")),if(json.isNull("periodEnd"))null else LocalDate.parse(json.getString("periodEnd")),CurrencyCode.valueOf(json.getString("currency")))
    }
}

/** Separate isolated UID: no vault files, URI grants or inherited INTERNET permission. No raw file is persisted. */
class StatementReaderService:Service() {
    private val started=AtomicBoolean()
    override fun onCreate() {
        super.onCreate()
        check(android.os.Process.myUid()!=applicationInfo.uid && checkSelfPermission("android.permission.INTERNET")==android.content.pm.PackageManager.PERMISSION_DENIED)
        // Independent watchdog also terminates a stuck parser or exhausted heap in this disposable process.
        Thread({Thread.sleep(12000);android.os.Process.killProcess(android.os.Process.myPid())},"statement-deadline").apply {isDaemon=true;start()}
    }
    private val messenger=Messenger(Handler(Looper.getMainLooper()){request->
        // Handler recycles Message after this callback. Copy its payload and reply endpoint before dispatching.
        val endpoint=request.replyTo
        val input=request.data.getByteArray("file")
        if(started.compareAndSet(false,true))Thread({
            val reply=Message.obtain();var bytes:ByteArray?=null
            try {
                bytes=requireNotNull(input);require(bytes.size<=HdfcStatements.MAX_BYTES)
                reply.data=Bundle().apply {putString("result",StatementWire.encode(HdfcStatements.parse(bytes)))}
            }catch(error:Throwable) {reply.data=Bundle().apply {putString("error","This statement could not be validated. Use the original supported HDFC XLS or UTF-8 CSV, at most 256 KB, with no formulas or embedded content.");putString("errorType",error.javaClass.simpleName)}}
            finally {bytes?.fill(0)}
            try{endpoint?.send(reply)}catch(_:Exception){/* Never log statement content. */}
            Handler(Looper.getMainLooper()).postDelayed({android.os.Process.killProcess(android.os.Process.myPid())},250)
        },"statement-reader").start()
        true
    })
    override fun onBind(intent:Intent):IBinder=messenger.binder
    override fun onDestroy(){super.onDestroy();android.os.Process.killProcess(android.os.Process.myPid())}
}

internal object StatementReaderClient {
    suspend fun parse(context:Context,bytes:ByteArray):ParsedStatement=withTimeout(15000) {
        val expectedHash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        suspendCancellableCoroutine {continuation->
            var bound=false
            val closed=AtomicBoolean()
            lateinit var connection:ServiceConnection
            fun close(){if(closed.compareAndSet(false,true) && bound)try{context.unbindService(connection)}catch(_:IllegalArgumentException){}}
            val receiver=Messenger(Handler(Looper.getMainLooper()){message->
                if(continuation.isActive) {
                    try {val data=message.data;val error=data.getString("error");if(error!=null)throw IllegalStateException("Reader ${data.getString("errorType")}");val result=StatementWire.decode(requireNotNull(data.getString("result")));require(result.fileHash==expectedHash);continuation.resume(result)}
                    catch(error:Exception){continuation.resumeWithException(IllegalArgumentException("This statement could not be validated. Choose an original supported HDFC XLS or UTF-8 CSV (up to 256 KB).",error))}
                }
                close();true
            })
            connection=object:ServiceConnection {
                override fun onServiceConnected(name:ComponentName,binder:IBinder) {
                    if(!continuation.isActive){close();return}
                    try{Messenger(binder).send(Message.obtain().apply {replyTo=receiver;data=Bundle().apply {putByteArray("file",bytes)}})}
                    catch(_:Exception){if(continuation.isActive)continuation.resumeWithException(IllegalArgumentException("Statement reader unavailable. Try again."));close()}
                }
                override fun onServiceDisconnected(name:ComponentName){if(continuation.isActive)continuation.resumeWithException(IllegalArgumentException("Statement reader stopped before validation completed. Try a shorter export."));close()}
                override fun onNullBinding(name:ComponentName)=onServiceDisconnected(name)
                override fun onBindingDied(name:ComponentName)=onServiceDisconnected(name)
            }
            continuation.invokeOnCancellation {close()}
            try {
                bound=context.bindService(Intent(context,StatementReaderService::class.java),connection,Context.BIND_AUTO_CREATE)
                if(!bound && continuation.isActive)continuation.resumeWithException(IllegalArgumentException("Cannot start statement reader."))
                if(closed.get() && bound)try{context.unbindService(connection)}catch(_:IllegalArgumentException){}
            }catch(_:Exception){if(continuation.isActive)continuation.resumeWithException(IllegalArgumentException("Cannot start statement reader."));close()}
        }
    }
}
