package com.wealthos.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Framework-only release probe; no AndroidX, Kotlin or obfuscated application-class linkage. */
public final class StatementReaderInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            Context target=getTargetContext();
            check(target.getPackageName().equals("com.wealthos.app.uxlab"));
            check((target.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)==0);
            probe(target,"fictional-bank.xls","HDFC_BANK");
            probe(target,"fictional-card.xls","HDFC_NEU_CARD");
            result.putString("stream","OK (2 minified XLS reader fixtures; isolated, non-debuggable UI Lab)\n");
            result.putString("status","MINIFIED_READER_VERIFIED");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            // Exception type only: even failed probes never print file data or raw parser messages.
            result.putString("stream","Minified reader probe failed: "+failure.getClass().getSimpleName()+"\n");
            finish(Activity.RESULT_CANCELED,result);
        }
    }
    private void probe(Context target,String file,String kind) throws Exception {
        byte[] bytes;
        try(InputStream input=getContext().getAssets().open("statements/"+file);ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int read;
            while((read=input.read(buffer))>=0)output.write(buffer,0,read);
            bytes=output.toByteArray();
        }
        final CountDownLatch latch=new CountDownLatch(1);
        final AtomicReference<Bundle> response=new AtomicReference<>();
        final Messenger receiver=new Messenger(new Handler(Looper.getMainLooper(),message->{response.set(new Bundle(message.getData()));latch.countDown();return true;}));
        ServiceConnection connection=new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name,IBinder binder) {
                try {Message message=Message.obtain();message.replyTo=receiver;Bundle data=new Bundle();data.putByteArray("file",bytes);message.setData(data);new Messenger(binder).send(message);}
                catch(Exception failure){latch.countDown();}
            }
            @Override public void onServiceDisconnected(ComponentName name){latch.countDown();}
        };
        check(target.bindService(new Intent().setClassName(target.getPackageName(),"com.wealthos.app.StatementReaderService"),connection,Context.BIND_AUTO_CREATE));
        try {
            check(latch.await(20,TimeUnit.SECONDS));Bundle reply=response.get();check(reply!=null && reply.getString("error")==null);
            JSONObject json=new JSONObject(reply.getString("result"));check(json.getString("kind").equals(kind));check(json.getString("currency").equals("INR"));check(json.getJSONArray("rows").length()==3);
            StringBuilder hash=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(java.util.Locale.ROOT,"%02x",value));
            check(json.getString("hash").equals(hash.toString()));
            if(kind.equals("HDFC_BANK"))check(json.getJSONArray("rows").getJSONObject(0).getString("amount").equals("125.25"));
            else check(json.getJSONArray("rows").getJSONObject(2).getString("direction").equals("CREDIT"));
        } finally {target.unbindService(connection);Arrays.fill(bytes,(byte)0);}
    }
    private static void check(boolean condition){if(!condition)throw new IllegalStateException("Reader probe invariant failed");}
}
