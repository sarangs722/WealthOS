package com.wealthos.security

import java.io.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable, authenticated format. No device identifier, database key or Keystore dependency. */
object BackupEnvelope {
    const val MAX_PAYLOAD=8*1024*1024
    const val ITERATIONS=600_000
    private val magic="WOSBAK01".toByteArray(Charsets.US_ASCII)
    private const val HEADER=72
    private val random=SecureRandom()
    fun validatePassword(password:CharArray){
        require(password.size in 12..128){"Use a password or passphrase of 12–128 characters. Spaces and Unicode are supported; remember it independently of this device."}
        var i=0
        while(i<password.size){
            val c=password[i]
            if(Character.isHighSurrogate(c)){
                require(i+1<password.size && Character.isLowSurrogate(password[i+1]))
                i++
            }else require(!Character.isLowSurrogate(c))
            i++
        }
    }
    fun encrypt(payload:ByteArray,password:CharArray):ByteArray {
        validatePassword(password)
        require(payload.size in 1..MAX_PAYLOAD){"Backup exceeds the supported 8 MiB financial payload limit. No records were changed."}
        val salt=ByteArray(32).also(random::nextBytes)
        val iv=ByteArray(12).also(random::nextBytes)
        val header=ByteBuffer.allocate(HEADER)
            .put(magic).putInt(1).putInt(1).putInt(1).putInt(ITERATIONS)
            .put(salt).put(iv).putInt(payload.size+16).array()
        val key=derive(password,salt)
        return try{
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv))
            cipher.updateAAD(header)
            header+cipher.doFinal(payload)
        }finally{key.fill(0)}
    }
    fun decrypt(file:ByteArray,password:CharArray):ByteArray {
        validatePassword(password)
        require(file.size in (HEADER+17)..(HEADER+MAX_PAYLOAD+16)){"Invalid, truncated or oversized WealthOS backup."}
        val h=ByteBuffer.wrap(file,0,HEADER)
        val m=ByteArray(8).also(h::get)
        // Fixed supported parameters prevent a hostile file from choosing an unbounded KDF cost.
        require(m.contentEquals(magic) && h.int==1 && h.int==1 && h.int==1 && h.int==ITERATIONS){"Unsupported backup format or security parameters. Your vault was not changed."}
        val salt=ByteArray(32).also(h::get)
        val iv=ByteArray(12).also(h::get)
        val length=h.int
        require(length==file.size-HEADER){"Backup is truncated or has trailing data."}
        val key=derive(password,salt)
        return try{
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv))
            cipher.updateAAD(file,0,HEADER)
            cipher.doFinal(file,HEADER,length)
        }catch(_:javax.crypto.BadPaddingException){
            throw IllegalArgumentException("Incorrect password or damaged backup. Your vault was not changed.")
        }finally{key.fill(0)}
    }
    fun read(input:InputStream):ByteArray {
        val out=ByteArrayOutputStream()
        val chunk=ByteArray(8192)
        while(true){
            val n=input.read(chunk)
            if(n<0)break
            require(out.size()+n<=HEADER+MAX_PAYLOAD+16){"Backup exceeds its size limit."}
            out.write(chunk,0,n)
        }
        return out.toByteArray()
    }
    private fun derive(password:CharArray,salt:ByteArray):ByteArray {
        val spec=PBEKeySpec(password,salt,ITERATIONS,256)
        return try{
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        }finally{spec.clearPassword()}
    }
}
