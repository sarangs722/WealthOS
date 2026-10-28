package com.wealthos.security

import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant

sealed interface Cell {data object Null:Cell;data class Integer(val value:Long):Cell;data class Text(val value:String):Cell}
data class SnapshotTable(val name:String,val columns:List<String>,val rows:List<List<Cell>>)
data class VaultSnapshot(val schema:Int,val createdAt:Instant,val tables:List<SnapshotTable>) {
    val records get()=tables.sumOf {it.rows.size}
    override fun toString()="VaultSnapshot(schema=$schema, tables=${tables.size}, records=$records)"
}
/** Typed and bounded binary rows; financial decimals are UTF-8 TEXT, never floating point. */
object SnapshotCodec {
    const val MAX_ROWS=50_000
    private val magic="WOSROWS1".toByteArray(Charsets.US_ASCII)
    fun encode(s:VaultSnapshot):ByteArray {
        require(s.schema in 1..1000 && s.tables.size in 1..100 && s.records<=MAX_ROWS)
        val bytes=object:ByteArrayOutputStream(){override fun write(b:Int){require(count<BackupEnvelope.MAX_PAYLOAD){"Backup exceeds 8 MiB."};super.write(b)};override fun write(b:ByteArray,off:Int,len:Int){require(count+len<=BackupEnvelope.MAX_PAYLOAD){"Backup exceeds 8 MiB."};super.write(b,off,len)}}
        DataOutputStream(bytes).use {out->out.write(magic);out.writeInt(s.schema);out.text(s.createdAt.toString(),80);out.writeInt(s.tables.size)
            require(s.tables.map {it.name}.distinct().size==s.tables.size)
            s.tables.forEach {t->out.text(t.name,100);require(t.columns.size in 1..80 && t.columns.distinct().size==t.columns.size);out.writeInt(t.columns.size);t.columns.forEach {out.text(it,100)};out.writeInt(t.rows.size)
                t.rows.forEach {row->require(row.size==t.columns.size);row.forEach {c->when(c){Cell.Null->out.writeByte(0);is Cell.Integer->{out.writeByte(1);out.writeLong(c.value)};is Cell.Text->{out.writeByte(2);out.text(c.value,65536)}}}}
            }
        };return bytes.toByteArray()
    }
    fun decode(bytes:ByteArray):VaultSnapshot {
        require(bytes.size in 1..BackupEnvelope.MAX_PAYLOAD)
        val input=DataInputStream(ByteArrayInputStream(bytes));val m=ByteArray(8).also(input::readFully);require(m.contentEquals(magic)){"Unknown backup payload."}
        val version=input.readInt();require(version in 1..1000);val created=Instant.parse(input.text(80));val count=input.readInt();require(count in 1..100);var total=0
        val tables=List(count){val name=input.text(100);val n=input.readInt();require(n in 1..80);val columns=List(n){input.text(100)};require(columns.distinct().size==n);val rows=input.readInt();require(rows>=0 && rows<=MAX_ROWS-total);total+=rows
            SnapshotTable(name,columns,List(rows){List(n){when(input.readUnsignedByte()){0->Cell.Null;1->Cell.Integer(input.readLong());2->Cell.Text(input.text(65536));else->throw IllegalArgumentException("Unsupported cell type in backup.")}}})}
        require(tables.map {it.name}.distinct().size==tables.size && input.read()==-1){"Duplicate tables or trailing payload data."}
        return VaultSnapshot(version,created,tables)
    }
    fun fingerprint(s:VaultSnapshot):ByteArray {val bytes=encode(s.copy(createdAt=Instant.EPOCH));return try{MessageDigest.getInstance("SHA-256").digest(bytes)}finally{bytes.fill(0)}}
    private fun DataOutputStream.text(value:String,max:Int){val b=value.toByteArray(Charsets.UTF_8);require(b.size<=max);writeInt(b.size);write(b)}
    private fun DataInputStream.text(max:Int):String {val n=readInt();require(n in 0..max && n<=available()){ "Invalid text length in backup."};val b=ByteArray(n).also(::readFully);return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString()}
}
