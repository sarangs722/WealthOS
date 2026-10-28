package com.wealthos.security

import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class BackupSecurityTest {
    private val password="Portable-நிதி-2026-test!".toCharArray()
    private fun data()=VaultSnapshot(6,Instant.parse("2026-10-28T00:00:00Z"),listOf(SnapshotTable("fictional",listOf("amount","native","count","unknown"),listOf(listOf(Cell.Text("123456789012345678901234.123456789012"),Cell.Text("INR · தமிழ்"),Cell.Integer(Long.MAX_VALUE),Cell.Null)))))
    private fun reject(action:()->Unit){try{action();fail("Invalid backup accepted")}catch(_:Exception){}}
    @Test fun portableAuthenticatedRoundTripPreservesExactBytes(){val payload=SnapshotCodec.encode(data());val file=BackupEnvelope.encrypt(payload,password);assertArrayEquals(payload,BackupEnvelope.decrypt(file,password));assertFalse(String(file,Charsets.ISO_8859_1).contains("123456789012345678901234"))}
    @Test fun independentDotNetUnicodeFixtureDecrypts(){val lines=javaClass.getResourceAsStream("/portable-unicode.fixture")!!.bufferedReader().readLines();assertEquals(lines[1],String(BackupEnvelope.decrypt(Base64.getDecoder().decode(lines[0]),lines[2].toCharArray()),Charsets.UTF_8))}
    @Test fun wrongPasswordIsRejected(){val file=BackupEnvelope.encrypt(byteArrayOf(1,2,3),password);reject {BackupEnvelope.decrypt(file,"Another strong passphrase".toCharArray())}}
    @Test fun alteredCiphertextSaltNonceAndTagFailAuthentication(){val file=BackupEnvelope.encrypt(byteArrayOf(1,2,3,4),password);listOf(25,57,72,file.lastIndex).forEach {at->val bad=file.copyOf();bad[at]=(bad[at].toInt() xor 1).toByte();reject {BackupEnvelope.decrypt(bad,password)}}}
    @Test fun downgradeUnknownVersionAndUnboundedKdfAreRejected(){val file=BackupEnvelope.encrypt(byteArrayOf(1),password);listOf(8,12,16,20).forEach {at->val bad=file.copyOf();bad[at]=127;reject {BackupEnvelope.decrypt(bad,password)}}}
    @Test fun truncationAndAppendedBytesAreRejected(){val file=BackupEnvelope.encrypt(byteArrayOf(1,2),password);listOf(file.copyOf(8),file.copyOf(file.size-1),file+byteArrayOf(0)).forEach {reject {BackupEnvelope.decrypt(it,password)}}}
    @Test fun saltAndNonceAreFreshForEachBackup(){val a=BackupEnvelope.encrypt(byteArrayOf(1),password);val b=BackupEnvelope.encrypt(byteArrayOf(1),password);assertFalse(a.contentEquals(b));assertFalse(a.copyOfRange(24,56).contentEquals(b.copyOfRange(24,56)));assertFalse(a.copyOfRange(56,68).contentEquals(b.copyOfRange(56,68)))}
    @Test fun invalidPasswordAndOversizedInputFailBeforeProcessing(){listOf(CharArray(11){'x'},CharArray(129){'x'},charArrayOf('\ud800')+CharArray(12){'x'}).forEach {reject {BackupEnvelope.encrypt(byteArrayOf(1),it)}};reject {BackupEnvelope.encrypt(ByteArray(BackupEnvelope.MAX_PAYLOAD+1),password)};reject {BackupEnvelope.read(ByteArrayInputStream(ByteArray(BackupEnvelope.MAX_PAYLOAD+100)))}}
    @Test fun losslessTypedSnapshotRoundTrip(){val s=data();assertEquals(s,SnapshotCodec.decode(SnapshotCodec.encode(s)))}
    @Test fun snapshotTrailingDataTruncationAndUnsupportedTypeFail(){val encoded=SnapshotCodec.encode(data());reject {SnapshotCodec.decode(encoded+byteArrayOf(0))};reject {SnapshotCodec.decode(encoded.copyOf(encoded.size-1))};val invalid=encoded.copyOf();invalid[0]=0;reject {SnapshotCodec.decode(invalid)}}
    @Test fun fingerprintIgnoresCaptureTimeButDetectsRecordChanges(){val s=data();assertArrayEquals(SnapshotCodec.fingerprint(s),SnapshotCodec.fingerprint(s.copy(createdAt=Instant.EPOCH)));val changed=s.copy(tables=s.tables.map {it.copy(rows=listOf(listOf(Cell.Text("0"),Cell.Text("INR"),Cell.Integer(1),Cell.Null)))});assertFalse(SnapshotCodec.fingerprint(s).contentEquals(SnapshotCodec.fingerprint(changed)))}
    @Test fun duplicateTablesAndExcessiveCellsAreRejected(){val s=data();reject {SnapshotCodec.encode(s.copy(tables=s.tables+s.tables))};reject {SnapshotCodec.encode(s.copy(tables=listOf(s.tables.single().copy(columns=List(81){"c$it"}))))}}
}
