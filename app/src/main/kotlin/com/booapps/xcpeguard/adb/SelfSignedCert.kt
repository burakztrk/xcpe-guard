package com.booapps.xcpeguard.adb

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.SecureRandom
import java.security.Signature
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object SelfSignedCert {
    private val SHA256_RSA = byteArrayOf(0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x0b)
    private val OID_CN = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)

    fun create(kp: KeyPair, commonName: String): ByteArray {
        val algo = seq(SHA256_RSA, byteArrayOf(0x05, 0x00))
        val name = seq(set(seq(OID_CN, tlv(0x0c, commonName.toByteArray()))))
        val validity = seq(tlv(0x17, utc(System.currentTimeMillis() - 86_400_000L)), tlv(0x17, "491231235959Z".toByteArray()))
        val serial = tlv(0x02, BigInteger(63, SecureRandom()).add(BigInteger.ONE).toByteArray())
        val version = tlv(0xa0, tlv(0x02, byteArrayOf(2)))
        val tbs = seq(version, serial, algo, name, validity, name, kp.public.encoded)
        val sig = Signature.getInstance("SHA256withRSA").run {
            initSign(kp.private)
            update(tbs)
            sign()
        }
        return seq(tbs, algo, tlv(0x03, byteArrayOf(0) + sig))
    }

    private fun utc(t: Long): ByteArray = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(t)).toByteArray()

    private fun seq(vararg p: ByteArray) = tlv(0x30, cat(*p))
    private fun set(vararg p: ByteArray) = tlv(0x31, cat(*p))
    private fun cat(vararg p: ByteArray) = ByteArrayOutputStream().apply { p.forEach { write(it) } }.toByteArray()

    private fun tlv(tag: Int, v: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(tag)
        val n = v.size
        when {
            n < 0x80 -> write(n)
            n < 0x100 -> { write(0x81); write(n) }
            n < 0x10000 -> { write(0x82); write(n shr 8); write(n and 0xff) }
            else -> { write(0x83); write(n shr 16); write((n shr 8) and 0xff); write(n and 0xff) }
        }
        write(v)
    }.toByteArray()
}
