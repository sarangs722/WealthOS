package com.wealthos.app

import android.app.Application
import android.net.Uri
import android.os.CancellationSignal
import com.wealthos.imports.HdfcStatements
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A cancelled or timed-out provider read closes its descriptor rather than leaving a blocked stream open. */
internal suspend fun BankViewModel.readStatementDocument(uri:Uri):ByteArray=suspendCancellableCoroutine {continuation->
    val signal=CancellationSignal();val opened=AtomicReference<InputStream?>()
    continuation.invokeOnCancellation {signal.cancel();try{opened.getAndSet(null)?.close()}catch(_:Exception){}}
    Dispatchers.IO.dispatch(EmptyCoroutineContext,Runnable {
        var bytes:ByteArray?=null
        try {
            signal.throwIfCanceled()
            val descriptor=requireNotNull(getApplication<Application>().contentResolver.openAssetFileDescriptor(uri,"r",signal))
            descriptor.use {asset->
                val stream=asset.createInputStream();opened.set(stream)
                stream.use {signal.throwIfCanceled();bytes=HdfcStatements.read(it)}
            }
            if(continuation.isActive){continuation.resume(requireNotNull(bytes)){_,value,_->value.fill(0)};bytes=null}
        }catch(_:Exception){if(continuation.isActive)continuation.resumeWithException(IllegalArgumentException("Cannot read this document. Choose a local supported statement."))}
        finally{opened.set(null);bytes?.fill(0)}
    })
}
